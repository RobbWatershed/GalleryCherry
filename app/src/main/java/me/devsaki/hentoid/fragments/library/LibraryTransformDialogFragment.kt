package me.devsaki.hentoid.fragments.library

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.exoplayer.ExoPlayer
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil3.load
import com.google.android.material.textfield.TextInputLayout
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibraryTransformBinding
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.createFile
import me.devsaki.hentoid.util.file.fileSizeFromUri
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import me.devsaki.hentoid.util.file.getBinary
import me.devsaki.hentoid.util.file.getExtensionFromMimeType
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.file.tryCleanDirectory
import me.devsaki.hentoid.util.image.ImageProperties
import me.devsaki.hentoid.util.image.TransformParams
import me.devsaki.hentoid.util.image.getImageProperties
import me.devsaki.hentoid.util.image.getMediaDimensions
import me.devsaki.hentoid.util.image.screenHeight
import me.devsaki.hentoid.util.image.screenWidth
import me.devsaki.hentoid.util.image.transformAnimated
import me.devsaki.hentoid.util.image.transformManhwaChapter
import me.devsaki.hentoid.util.image.transformStill
import me.devsaki.hentoid.util.video.videoOnlyRenderersFactory
import me.devsaki.hentoid.viewholders.DrawerItem
import me.devsaki.hentoid.workers.TransformWorker
import okio.use
import timber.log.Timber
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

private const val KEY_CONTENTS = "contents"
private const val CACHE_TRANSFORM_MANHWA = "transform-manhwa"
private const val CACHE_PREVIEW = "preview"
private const val DIMS_LIMIT = 20000

class LibraryTransformDialogFragment : BaseDialogFragment<LibraryTransformDialogFragment.Parent>() {
    companion object {
        fun invoke(parent: Fragment, contentList: List<Content>) {
            val args = Bundle()
            args.putLongArray(KEY_CONTENTS, contentList.map { it.id }.toLongArray())
            invoke(parent, LibraryTransformDialogFragment(), args)
        }
    }

    // UI
    private var binding: DialogLibraryTransformBinding? = null
    private lateinit var updatePreviewDebouncer: Debouncer<Unit>

    // === VARIABLES
    private lateinit var contentIds: LongArray
    private val content: Content? by lazy {
        val dao = ObjectBoxDAO()
        try {
            dao.selectContent(contentIds[contentIndex])
        } finally {
            dao.cleanup()
        }
    }
    private var contentIndex = 0
    private var pageIndex = 0
    private var maxPages = -1
    private val itemAdapter = ItemAdapter<DrawerItem<Any>>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private var targetDimsWarning = false
    private var player: ExoPlayer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val contentIdArg = arguments?.getLongArray(KEY_CONTENTS)
        require(!(null == contentIdArg || contentIdArg.isEmpty())) { "No content IDs" }
        contentIds = contentIdArg

        updatePreviewDebouncer = Debouncer(lifecycleScope, 300) { refreshPreview() }
    }

    override fun onDestroy() {
        updatePreviewDebouncer.clear()
        // Empty cache
        context?.apply {
            getOrCreateCacheFolder(this, CACHE_PREVIEW)?.let {
                if (!tryCleanDirectory(it)) Timber.d("Failed to clean preview cache")
            }
        }
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogLibraryTransformBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        player?.release()
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        // Populate values
        binding?.apply {
            val stillEncoders = PictureEncoder.entries.filter { it.isImage }
            encoderAll.entries = stillEncoders.map { it.description }
            encoderAll.values = stillEncoders.map { it.value.toString() }
            encoderLossless.entries = stillEncoders.filter { it.isLossless }.map { it.description }
            encoderLossless.values =
                stillEncoders.filter { it.isLossless }.map { it.value.toString() }
            encoderLossy.entries = stillEncoders.filter { !it.isLossless }.map { it.description }
            encoderLossy.values =
                stillEncoders.filter { !it.isLossless }.map { it.value.toString() }

            val animEncoders = PictureEncoder.entries.filter { it.isAnimation }
            encoderAnim.entries = animEncoders.map { it.description }
            encoderAnim.values = animEncoders.map { it.value.toString() }
        }

        // Refresh before triggers are set
        refreshControls(true)
        updatePreviewDebouncer.submit(Unit)

        // Set triggers
        binding?.apply {
            resizeSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isResizeEnabled = isChecked
                refreshControls()
                updatePreviewDebouncer.submit(Unit)
            }
            resizeMethod.setOnIndexChangeListener { index ->
                Settings.resizeMethod = index
                refreshControls()
                updatePreviewDebouncer.submit(Unit)
            }
            resizeMethod1Ratio.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(resizeMethod1Ratio, 100, 200)) {
                    Settings.resizeMethod1Ratio = value.toInt()
                    updatePreviewDebouncer.submit(Unit)
                }
            }
            resizeMethod2MaxWidth.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(
                        resizeMethod2MaxWidth,
                        screenWidth,
                        screenWidth * 10
                    )
                ) {
                    Settings.resizeMethod2Width = value.toInt()
                    updatePreviewDebouncer.submit(Unit)
                }
            }
            resizeMethod2MaxHeight.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(
                        resizeMethod2MaxHeight,
                        screenHeight,
                        screenHeight * 10
                    )
                ) {
                    Settings.resizeMethod2Height = value.toInt()
                    updatePreviewDebouncer.submit(Unit)
                }
            }
            resizeMethod3Ratio.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(resizeMethod3Ratio, 10, 100)) {
                    Settings.resizeMethod3Ratio = value.toInt()
                    updatePreviewDebouncer.submit(Unit)
                }
            }
            resizeMethod5Images.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(resizeMethod5Images, 1, 200)) {
                    Settings.resizeMethod5Images = value.toInt()
                    updatePreviewDebouncer.submit(Unit)
                }
            }

            // Images
            transcodeImgHeader.text = resources.getString(
                R.string.transform_transcoding,
                resources.getString(R.string.transform_images)
            )
            transcodeMethod.setOnIndexChangeListener { index ->
                Settings.transcodeMethod = index
                refreshControls()
                updatePreviewDebouncer.submit(Unit)
            }
            encoderAll.setOnValueChangeListener { value ->
                Settings.transcodeEncoderAll = value.toInt()
                refreshControls()
                updatePreviewDebouncer.submit(Unit)
            }
            encoderLossless.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossless = value.toInt()
                updatePreviewDebouncer.submit(Unit)
            }
            encoderLossy.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossy = value.toInt()
                updatePreviewDebouncer.submit(Unit)
            }
            encoderQuality.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(encoderQuality, 75, 100)) {
                    Settings.transcodeQuality = value.toInt()
                    updatePreviewDebouncer.submit(Unit)
                }
            }

            // Animations
            transcodeAnimHeader.text = resources.getString(
                R.string.transform_transcoding,
                resources.getString(R.string.transform_animations)
            )
            encoderAnim.setOnValueChangeListener { value ->
                Settings.transcodeEncoderAnim = value.toInt()
                refreshControls()
                updatePreviewDebouncer.submit(Unit)
            }
            encoderAnimQuality.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(encoderQuality, 75, 100)) {
                    Settings.transcodeAnimQuality = value.toInt()
                    updatePreviewDebouncer.submit(Unit)
                }
            }

            // Preview
            prevPageBtn.setOnClickListener {
                if (pageIndex > 0) pageIndex--
                updatePreviewDebouncer.submit(Unit)
            }
            nextPageBtn.setOnClickListener {
                if (pageIndex < maxPages - 1) pageIndex++
                updatePreviewDebouncer.submit(Unit)
            }
            imgThumb.setOnClickListener {
                imgPreview.isVisible = true
            }
            imgPreview.setOnClickListener {
                imgPreview.isVisible = false
            }
            videoThumbFrame.setOnClickListener {
                player?.setVideoSurfaceView(videoPreview)
                videoPreviewFrame.isVisible = true
            }
            videoPreviewFrame.setOnClickListener {
                player?.setVideoSurfaceView(videoThumb)
                videoPreviewFrame.isVisible = false
            }
            actionButton.setOnClickListener { onActionClick(buildParams()) }
        }
    }

    private fun refreshControls(applyValues: Boolean = false) {
        binding?.apply {
            val isAiUpscale = (3 == Settings.resizeMethod) && Settings.isResizeEnabled

            // Resize
            if (applyValues) resizeSwitch.isChecked = Settings.isResizeEnabled

            if (applyValues) resizeMethod.index = Settings.resizeMethod
            resizeMethod.isVisible = Settings.isResizeEnabled
            resizeMethod1Ratio.isVisible = (0 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod1Ratio.editText?.setText(Settings.resizeMethod1Ratio.toString())
            resizeMethod2MaxWidth.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) {
                val value = max(Settings.resizeMethod2Width, screenWidth)
                resizeMethod2MaxWidth.editText?.setText(value.toString())
            }
            resizeMethod2MaxHeight.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) {
                val value = max(Settings.resizeMethod2Height, screenHeight)
                resizeMethod2MaxHeight.editText?.setText(value.toString())
            }
            resizeMethod3Ratio.isVisible = (2 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod3Ratio.editText?.setText(Settings.resizeMethod3Ratio.toString())
            resizeMethod5Images.isVisible = (4 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod5Images.editText?.setText(Settings.resizeMethod5Images.toString())

            // Transcode images
            transcodeImgHeader.isVisible = !isAiUpscale
            transcodeMethod.isVisible = !isAiUpscale
            if (applyValues) transcodeMethod.index = Settings.transcodeMethod
            encoderAll.isVisible = (0 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderAll.value = Settings.transcodeEncoderAll.toString()
            encoderLossless.isVisible = (1 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderLossless.value = Settings.transcodeEncoderLossless.toString()
            encoderLossy.isVisible = (1 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderLossy.value = Settings.transcodeEncoderLossy.toString()
            val isEncoderAllLossy =
                !(PictureEncoder.fromValue(Settings.transcodeEncoderAll)?.isLossless ?: false)
            encoderQuality.isVisible =
                (1 == transcodeMethod.index || (0 == transcodeMethod.index && isEncoderAllLossy))
            if (isAiUpscale) encoderQuality.isVisible = false
            if (applyValues) encoderQuality.editText?.setText(Settings.transcodeQuality.toString())

            // Transcode animations
            transcodeAnimHeader.isVisible = !isAiUpscale
            encoderAnim.isVisible = !isAiUpscale
            encoderQuality.isVisible = !isAiUpscale
            encoderAnimQuality.isVisible =
                (false == PictureEncoder.fromValue(Settings.transcodeEncoderAnim)?.isLossless)
            if (applyValues) {
                encoderAnim.value = Settings.transcodeEncoderAnim.toString()
                encoderAnimQuality.editText?.setText(Settings.transcodeAnimQuality.toString())
            }

            // Warning list
            warningsList.adapter = fastAdapter

            val encoderWarning = (
                    (0 == transcodeMethod.index && (Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSY.value || Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSLESS.value))
                            || (1 == transcodeMethod.index && (Settings.transcodeEncoderLossy == PictureEncoder.WEBP_LOSSY.value || Settings.transcodeEncoderLossless == PictureEncoder.WEBP_LOSSLESS.value))
                    )

            // Check if content contains transformed pages already
            var retransformedPics = 0
            content?.apply { retransformedPics = imageList.count { it.isTransformed } }

            if (encoderWarning || retransformedPics > 0 || isAiUpscale || targetDimsWarning) {
                itemAdapter.clear()
                if (encoderWarning) itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.encoder_warning),
                        R.drawable.ic_warning,
                        1,
                        true
                    )
                )
                if (retransformedPics > 0) itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.retransform_warning, retransformedPics),
                        R.drawable.ic_warning,
                        2,
                        true
                    )
                )
                if (isAiUpscale) itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.ai_rescale_warning),
                        R.drawable.ic_warning,
                        3,
                        true
                    )
                )
                if (targetDimsWarning) itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.dimensions_warning),
                        R.drawable.ic_warning,
                        4,
                        true
                    )
                )
                warningsList.isVisible = true
            } else warningsList.isVisible = false
        }
    }

    @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
    @SuppressLint("SetTextI18n")
    private fun refreshPreview() {
        val context = requireContext()
        val sourceBmp = getCurrentBitmap() ?: return
        val sourceProps = sourceBmp.getProperties(context)

        binding?.apply {
            previewGrp.visibility = View.INVISIBLE
            previewProgress.isIndeterminate = true
            previewProgress.isVisible = true
        }

        @androidx.media3.common.util.UnstableApi
        lifecycleScope.launch {
            val sourceSize = formatHumanReadableSize(sourceBmp.rawData.size.toLong(), resources)
            val sourceDims = sourceBmp.getDimensions(context)
            val sourceName =
                sourceBmp.name + "." + getExtensionFromMimeType(sourceProps.mime)
            val params = buildParams()
            val targetData: BitmapInfo = withContext(Dispatchers.IO) {
                return@withContext if (params.resizeEnabled && 4 == params.resizeMethod) {
                    // Manhwa resize
                    val res = transformManhwa(params, pageIndex)
                    BitmapInfo(if (res.isEmpty()) sourceBmp.rawData else res)
                } else if (sourceProps.isAnimated) {
                    withContext(Dispatchers.Main) {
                        binding?.previewProgress?.isIndeterminate = false
                        binding?.previewProgress?.max = 100
                    }
                    val tempFolder = getOrCreateCacheFolder(context, CACHE_PREVIEW)?.toUri()
                        ?: return@withContext BitmapInfo(sourceBmp.rawData)
                    val tempFile = createFile(
                        context, tempFolder, "temp",
                        params.transcodeAnim.mimeType
                    )
                    if (transformAnimated(
                            context,
                            sourceBmp.uri,
                            sourceProps.mime,
                            tempFile,
                            params,
                            { false }
                        ) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                binding?.previewProgress?.progress = (it * 100).roundToInt()
                            }
                        }
                    ) BitmapInfo(tempFile)
                    else BitmapInfo(sourceBmp.rawData)
                } else BitmapInfo(transformStill(context, sourceBmp.rawData, params, true))
            }

            @Suppress("ARRAY_EQUALITY_OPERATOR_CAN_BE_REPLACED_WITH_CONTENT_EQUALS")
            val unchanged = targetData.rawData == sourceBmp.rawData

            val displayData = if (unchanged) sourceBmp else targetData
            val displayProps = withContext(Dispatchers.IO) {
                displayData.getProperties(context)
            }
            val targetSize = withContext(Dispatchers.IO) {
                formatHumanReadableSize(displayData.getSize(context), resources)
            }
            val targetMime = displayProps.mime
            val targetName = sourceBmp.name + "." + getExtensionFromMimeType(targetMime)
            val targetDims = displayData.getDimensions(context)
            targetDimsWarning = (targetDims.x > DIMS_LIMIT || targetDims.y > DIMS_LIMIT)
            refreshControls()

            binding?.apply {
                if (unchanged) {
                    previewName.text = resources.getText(R.string.transform_unsupported)
                    previewDims.text = "${sourceDims.x} x ${sourceDims.y}"
                    previewSize.text = sourceSize
                } else {
                    previewName.text = "$sourceName ➤ $targetName"
                    previewDims.text =
                        "${sourceDims.x} x ${sourceDims.y} ➤ ${targetDims.x} x ${targetDims.y}"
                    previewSize.text = "$sourceSize ➤ $targetSize"
                }

                videoThumb.isVisible = targetMime.contains("video/")
                imgThumb.visibility = if (videoThumb.isVisible) View.INVISIBLE else View.VISIBLE

                Timber.d("target : $targetMime / ${displayData.uri}")

                if (targetMime.contains("video/")) {
                    videoThumbFrame.setAspectRatio(targetDims.x.toFloat() / targetDims.y)
                    videoPreviewFrame.setAspectRatio(targetDims.x.toFloat() / targetDims.y)
                    ExoPlayer.Builder(requireContext(), videoOnlyRenderersFactory).build().apply {
                        player = this
                        setVideoSurfaceView(videoThumb)
                        // Those are only available through Uris
                        val mediaItem = MediaItem.fromUri(displayData.uri)
                        setMediaItem(mediaItem)
                        repeatMode = REPEAT_MODE_ONE
                        prepare()
                        play()
                    }
                } else {
                    if (displayData.rawData.isEmpty()) {
                        imgThumb.load(displayData.uri)
                        imgPreview.load(displayData.uri)
                    } else {
                        imgThumb.load(displayData.rawData)
                        imgPreview.load(displayData.rawData)
                    }
                }

                previewProgress.isVisible = false
                previewGrp.visibility = View.VISIBLE
            }
        }
    }

    private fun getCurrentBitmap(): BitmapInfo? {
        content?.apply {
            // Get bitmap for display
            val pages = imageList.filter { it.isReadable }
            if (pages.isEmpty()) return null
            maxPages = pages.size
            val page = pages[pageIndex]
            try {
                getInputStream(requireContext(), page.fileUri.toUri()).use {
                    return BitmapInfo(page.fileUri.toUri(), page.name, it.readBytes())
                }
            } catch (t: Throwable) {
                Timber.w(t)
            }
        }
        return null
    }

    private suspend fun transformManhwa(params: TransformParams, firstPageIndex: Int): ByteArray {
        content?.let {
            // Prepare cache folder
            var cacheFolder =
                getOrCreateCacheFolder(requireContext(), CACHE_TRANSFORM_MANHWA)
                    ?: return ByteArray(0)
            if (!cacheFolder.deleteRecursively()) return ByteArray(0)
            cacheFolder =
                getOrCreateCacheFolder(requireContext(), CACHE_TRANSFORM_MANHWA)
                    ?: return ByteArray(0)

            // Run transformation in preview mode
            transformManhwaChapter(
                requireContext(),
                it.imageList.filter { i -> i.isReadable }.drop(firstPageIndex),
                1,
                cacheFolder.toUri(),
                params,
                true
            )

            // Read result from cache folder
            val file = cacheFolder.listFiles()?.firstOrNull() ?: return ByteArray(0)
            context?.let { ctx ->
                return getBinary(ctx, file.toUri())
            }
        }
        return ByteArray(0)
    }

    private fun buildParams(): TransformParams {
        binding!!.apply {
            return TransformParams(
                resizeSwitch.isChecked,
                resizeMethod.index,
                resizeMethod1Ratio.editText!!.text.toString().toFloat() / 100f,
                resizeMethod2MaxHeight.editText!!.text.toString().toInt(),
                resizeMethod2MaxWidth.editText!!.text.toString().toInt(),
                resizeMethod3Ratio.editText!!.text.toString().toFloat() / 100f,
                resizeMethod5Images.editText!!.text.toString().toInt(),
                transcodeMethod.index,
                PictureEncoder.fromValue(encoderAll.value.toInt())!!,
                PictureEncoder.fromValue(encoderLossy.value.toInt())!!,
                PictureEncoder.fromValue(encoderLossless.value.toInt())!!,
                encoderQuality.editText!!.text.toString().toInt(),
                PictureEncoder.fromValue(encoderAnim.value.toInt())!!,
                encoderAnimQuality.editText!!.text.toString().toInt()
            )
        }
    }

    private fun onActionClick(params: TransformParams) {
        // Check if no dialog is in error state
        binding?.apply {
            val nbError = container.children
                .filter { it is TextInputLayout }
                .map { it as TextInputLayout }
                .count { it.isErrorEnabled }

            if (nbError > 0) return

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val serializedParams = moshi.adapter(TransformParams::class.java).toJson(params)

            val myData: Data = workDataOf(
                "IDS" to contentIds,
                "PARAMS" to serializedParams
            )

            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueueUniqueWork(
                R.id.transform_service.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(TransformWorker::class.java)
                    .setInputData(myData)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
        parent?.leaveSelectionMode()
        dismissAllowingStateLoss()
    }

    private fun checkRange(text: TextInputLayout, minValue: Int, maxValue: Int): Boolean {
        val editTxt = text.editText
        require(editTxt != null)
        val errMsg = resources.getString(R.string.range_check, minValue, maxValue)
        val nbMaxDigits = floor(log10(maxValue.toDouble())) + 1
        if (editTxt.text.toString().isEmpty() || editTxt.text.toString().length > nbMaxDigits) {
            text.isErrorEnabled = true
            text.error = errMsg
            return false
        }
        val intValue = editTxt.text.toString().toInt()
        if (intValue !in minValue..maxValue) {
            text.isErrorEnabled = true
            text.error = errMsg
            return false
        }
        text.isErrorEnabled = false
        text.error = null
        return true
    }

    @Suppress("ArrayInDataClass")
    data class BitmapInfo(
        val uri: Uri,
        val name: String,
        val rawData: ByteArray
    ) {
        constructor(uri: Uri) : this(uri, "", ByteArray(0))
        constructor(rawData: ByteArray) : this(Uri.EMPTY, "", rawData)

        fun getProperties(context: Context): ImageProperties {
            return if (rawData.isNotEmpty()) getImageProperties(rawData)
            else getImageProperties(context, uri) ?: ImageProperties(
                "",
                isLossless = false,
                isAnimated = false
            )
        }

        fun getSize(context: Context): Long {
            return if (rawData.isNotEmpty()) rawData.size.toLong()
            else fileSizeFromUri(context, uri)
        }

        suspend fun getDimensions(context: Context): Point {
            return getMediaDimensions(context, uri, rawData)
        }
    }

    interface Parent {
        fun leaveSelectionMode()
    }
}
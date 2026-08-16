package me.devsaki.hentoid.activities

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.view.descendants
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil3.load
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputLayout
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.TransformActivityBundle
import me.devsaki.hentoid.core.URL_WIKI_TRANSFORM
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.ActivityTransformBinding
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.fragments.RangeDialogFragment
import me.devsaki.hentoid.fragments.transform.EncodeAnimFragment
import me.devsaki.hentoid.fragments.transform.EncodeImgFragment
import me.devsaki.hentoid.fragments.transform.ResizeFragment
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.createExceptionLogFile
import me.devsaki.hentoid.util.file.createFile
import me.devsaki.hentoid.util.file.fileSizeFromUri
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import me.devsaki.hentoid.util.file.getBinary
import me.devsaki.hentoid.util.file.getExtensionFromMimeType
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.formatEpochToDate
import me.devsaki.hentoid.util.image.ImageProperties
import me.devsaki.hentoid.util.image.TransformParams
import me.devsaki.hentoid.util.image.getImageProperties
import me.devsaki.hentoid.util.image.getMediaDimensions
import me.devsaki.hentoid.util.image.transformAnimated
import me.devsaki.hentoid.util.image.transformManhwaChapter
import me.devsaki.hentoid.util.image.transformStill
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.tryShowMenuIcons
import me.devsaki.hentoid.util.video.videoOnlyRenderersFactory
import me.devsaki.hentoid.viewholders.DrawerItem
import me.devsaki.hentoid.workers.TransformWorker
import okio.use
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.time.Instant
import kotlin.math.roundToInt


private const val CACHE_TRANSFORM_MANHWA = "transform-manhwa"
private const val CACHE_PREVIEW = "preview"
private const val DIMS_LIMIT = 20000

class TransformActivity : BaseActivity(), RangeDialogFragment.Parent {

    // == UI
    private var binding: ActivityTransformBinding? = null
    private lateinit var unlockMenu: MenuItem
    private lateinit var resizeTab: TabLayout.Tab
    private lateinit var encodePicTab: TabLayout.Tab
    private lateinit var encodeAnimTab: TabLayout.Tab
    private var player: ExoPlayer? = null

    private lateinit var updatePreviewDebouncer: Debouncer<Unit>
    private var backCallback: OnBackPressedCallback? = null

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
    private val nbTransformed: Long by lazy {
        val dao = ObjectBoxDAO()
        try {
            dao.countTransformedPages(contentIds)
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
    private var warnings = HashMap<Int, Set<Int>>()

    private var isRangeChapters = false
    private var range = ""

    // Previews
    private var rawData: BitmapInfo? = null
    private var transformedData: BitmapInfo? = null
    private var isFullscreenTransformed = true


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (null == intent || null == intent.extras) throw IllegalArgumentException("Required intent not found")

        val parser = TransformActivityBundle(intent.extras!!)
        contentIds =
            parser.contentIds ?: throw IllegalArgumentException("Required init arguments not found")
        if (contentIds.isEmpty()) throw IllegalArgumentException("Required init arguments not found")

        binding = ActivityTransformBinding.inflate(layoutInflater)
        binding?.apply {
            setContentView(root)
            toolbar.setOnMenuItemClickListener(this@TransformActivity::onToolbarItemClicked)
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            tryShowMenuIcons(this@TransformActivity, toolbar.menu)
            unlockMenu = toolbar.menu.findItem(R.id.action_unlock)

            // Instantiate a ViewPager and a PagerAdapter.
            val pagerAdapter: FragmentStateAdapter = ScreenSlidePagerAdapter(this@TransformActivity)
            pager.isUserInputEnabled = false // Disable swipe to change tabs

            pager.adapter = pagerAdapter
            TabLayoutMediator(tabs, pager) { tab: TabLayout.Tab, position: Int ->
                when (position) {
                    0 -> {
                        resizeTab = tab
                        tab.setText(R.string.transform_resize)
                    }

                    1 -> {
                        encodePicTab = tab
                        tab.text = resources.getString(
                            R.string.transform_encoder,
                            resources.getString(R.string.transform_images)
                        )
                    }

                    else -> {
                        encodeAnimTab = tab
                        tab.text = resources.getString(
                            R.string.transform_encoder,
                            resources.getString(R.string.transform_animations)
                        )
                    }
                }
            }.attach()

            rangeTxt.isVisible = (1 == contentIds.size) // Nonsensical for multiple books
            rangeTxt.text = String.format(
                "%s : %s",
                resources.getString(R.string.transform_range),
                resources.getString(R.string.transform_all_pages)
            )

            rangeButton.setOnClickListener {
                RangeDialogFragment.invoke(
                    this@TransformActivity,
                    resources.getString(R.string.range_process_prompt),
                    "",
                    content?.chaptersList?.isNotEmpty() ?: false
                )
            }

            skipTransformedSwitch.isVisible = (nbTransformed > 0)
            skipTransformedSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.skipTransformedPics = isChecked
                refreshUI()
            }

            // Switch between original and transformed
            switchFullscreenBtn.setOnClickListener {
                isFullscreenTransformed = !isFullscreenTransformed
                switchFullscreenBtn.text = resources.getString(
                    if (isFullscreenTransformed) R.string.transformed else R.string.original
                )
                val data = if (isFullscreenTransformed) transformedData else rawData
                data?.let {
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (it.getProperties(this@TransformActivity).mime.startsWith(
                                "video/",
                                true
                            )
                        ) { // Video
                            Timber.v("Displaying video")
                            // TODO known issue : fullscreen video is not centered
                            val dims = it.getDimensions(this@TransformActivity)
                            withContext(Dispatchers.Main) {
                                videoFullscreenFrame.setAspectRatio(dims.x.toFloat() / dims.y)
                                imgFullscreen.visibility = View.INVISIBLE
                                videoFullscreenFrame.isVisible = true
                                getPlayer(this@TransformActivity).apply {
                                    setMediaItem(MediaItem.fromUri(data.uri))
                                    setVideoSurfaceView(videoFullscreen)
                                    prepare()
                                    play()
                                }
                            }
                        } else { // Other formats
                            Timber.v("Displaying still picture")
                            withContext(Dispatchers.Main) {
                                videoFullscreenFrame.isVisible = false
                                imgFullscreen.isVisible = true
                                if (data.rawData.isEmpty()) {
                                    imgFullscreen.load(data.uri.toString())
                                } else {
                                    imgFullscreen.load(data.rawData)
                                }
                            }
                        }
                    }
                }
            }

            warningsList.adapter = fastAdapter
        }

        if (!Settings.recentVisibility) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        updatePreviewDebouncer = Debouncer(lifecycleScope, 300) { refreshPreview() }
        addCustomBackControl()

        Handler(Looper.getMainLooper()).postDelayed({
            updateUnlockMenu()
        }, 100)
    }

    @Suppress("SameReturnValue")
    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_unlock -> {
                Settings.unlockTransformCaps = !Settings.unlockTransformCaps
                updateUnlockMenu()
            }

            R.id.help -> startBrowserActivity(URL_WIKI_TRANSFORM)
            else -> return true
        }
        return true
    }

    private fun updateUnlockMenu() {
        if (Settings.unlockTransformCaps) {
            unlockMenu.setIcon(R.drawable.ic_lock_open)
            unlockMenu.setTitle(R.string.transform_lock_values)
        } else {
            unlockMenu.setIcon(R.drawable.ic_lock_closed)
            unlockMenu.setTitle(R.string.transform_unlock_values)
        }
        refreshAllTabs()
    }

    private fun addCustomBackControl() {
        backCallback?.remove()
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Close fullscreen preview
                binding?.apply {
                    if (imgFullscreen.isVisible) {
                        imgFullscreen.visibility = View.INVISIBLE
                        switchFullscreenBtn.isVisible = false
                        fullscreenBg.isVisible = false
                        return
                    }
                    if (videoFullscreenFrame.isVisible) {
                        // Revert player to transformed data
                        getPlayer(this@TransformActivity).apply {
                            setVideoSurfaceView(videoThumb)
                            transformedData?.let {
                                setMediaItem(MediaItem.fromUri(it.uri))
                                prepare()
                                play()
                            }
                        }
                        videoFullscreenFrame.isVisible = false
                        switchFullscreenBtn.isVisible = false
                        fullscreenBg.isVisible = false
                        return
                    }
                }

                // Other cases
                backCallback?.remove()
                onBackPressedDispatcher.onBackPressed()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback!!)
    }

    override fun onDestroy() {
        player?.stop()
        player = null
        binding = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updatePreview()

        // Set triggers
        binding?.apply {
            prevPageBtn.setOnClickListener {
                if (pageIndex > 0) {
                    rawData = null
                    pageIndex--
                    updatePreviewDebouncer.submit(Unit)
                }
            }
            nextPageBtn.setOnClickListener {
                if (pageIndex < maxPages - 1) {
                    rawData = null
                    pageIndex++
                    updatePreviewDebouncer.submit(Unit)
                }
            }
            imgThumb.setOnClickListener {
                imgFullscreen.isVisible = true
                switchFullscreenBtn.isVisible = true
                fullscreenBg.isVisible = true
                isFullscreenTransformed = true
            }
            imgFullscreen.setOnClickListener {
                imgFullscreen.visibility = View.INVISIBLE
                switchFullscreenBtn.isVisible = false
                fullscreenBg.isVisible = false
            }
            videoThumbFrame.setOnClickListener {
                getPlayer(this@TransformActivity).setVideoSurfaceView(videoFullscreen)
                videoFullscreenFrame.isVisible = true
                switchFullscreenBtn.isVisible = true
                fullscreenBg.isVisible = true
                isFullscreenTransformed = true
            }
            videoFullscreenFrame.setOnClickListener {
                getPlayer(this@TransformActivity).apply {
                    setVideoSurfaceView(videoThumb)
                    // Revert player to transformed data
                    transformedData?.let {
                        setMediaItem(MediaItem.fromUri(it.uri))
                        prepare()
                        play()
                    }
                }
                videoFullscreenFrame.isVisible = false
                switchFullscreenBtn.isVisible = false
                fullscreenBg.isVisible = false
            }
            actionButton.setOnClickListener { onActionClick(buildParams()) }
        }
    }

    fun updatePreview() {
        updatePreviewDebouncer.submit(Unit)
    }

    fun refreshAllTabs() {
        EventBus.getDefault().post(
            CommunicationEvent(
                CommunicationEvent.Type.UPDATE,
                CommunicationEvent.Recipient.TRANSFORM_ALL
            )
        )
    }

    /**
     * Warnings are StringRes
     */
    fun setWarnings(tab: Int, warnings: Set<Int>) {
        this.warnings[tab] = warnings
        refreshUI()
    }

    override fun onRangeSelected(isChapters: Boolean, value: String) {
        val unit = if (isChapters) R.plurals.chapter else R.plurals.page
        binding?.apply {
            isRangeChapters = isChapters
            range = value
            rangeTxt.text = String.format(
                "%s : %s",
                resources.getString(R.string.transform_range),
                String.format("%s %s", resources.getQuantityString(unit, 2), value)
            )
        }
    }

    private fun refreshUI() {
        val allWarnings = warnings.values.flatten().map { resources.getString(it) }.toMutableList()
        if (targetDimsWarning) allWarnings.add(resources.getString(R.string.dimensions_warning))

        // Check if content contains transformed pages already
        if (!Settings.skipTransformedPics) {
            if (nbTransformed > 0) allWarnings.add(
                resources.getString(
                    R.string.retransform_warning,
                    nbTransformed
                )
            )
        }

        binding?.warningsList?.isVisible = allWarnings.isNotEmpty()

        if (allWarnings.isNotEmpty()) {
            itemAdapter.clear()
            allWarnings.forEachIndexed { index, s ->
                itemAdapter.add(
                    DrawerItem(
                        s,
                        R.drawable.ic_warning,
                        index.toLong(),
                        true
                    )
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
    @SuppressLint("SetTextI18n")
    private fun refreshPreview() {
        if (areInputErrors()) {
            binding?.actionButton?.isEnabled = false
            return
        } else binding?.actionButton?.isEnabled = true

        val context = this
        val sourceBmp = getCurrentBitmap() ?: return
        val sourceProps = sourceBmp.getProperties(context)

        binding?.apply {
            previewGrp.visibility = View.INVISIBLE
            player?.stop()
            previewProgress.isIndeterminate = true
            previewProgress.isVisible = true
        }

        lifecycleScope.launch {
            val sourceSize = formatHumanReadableSize(sourceBmp.rawData.size.toLong(), resources)
            val sourceDims = sourceBmp.getDimensions(context)
            val sourceName =
                sourceBmp.name + "." + getExtensionFromMimeType(sourceProps.mime)
            val params = buildParams()
            val targetData: BitmapInfo? = withContext(Dispatchers.IO) {
                try {
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
                            context, tempFolder, "temp_" + formatEpochToDate(
                                Instant.now().toEpochMilli(), "yyyyMMdd-hhmmss-nnnnnnnnn"
                            ),
                            params.transcodeAnim.mimeType
                        )
                        if (transformAnimated(
                                context,
                                sourceBmp.uri,
                                sourceProps.mime,
                                tempFile,
                                params,
                                { false } // TODO interrupt previous encoding process when running a new one
                            ) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    binding?.previewProgress?.progress = (it * 100).roundToInt()
                                }
                            }
                        ) BitmapInfo(tempFile)
                        else BitmapInfo(sourceBmp.rawData)
                    } else BitmapInfo(transformStill(context, sourceBmp.rawData, params, true))
                } catch (e: Exception) {
                    toast(R.string.transform_error)
                    createExceptionLogFile(e, context)
                    return@withContext null
                }
            }
            targetData ?: return@launch

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
            refreshUI()

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

                videoThumbFrame.isVisible = targetMime.startsWith("video/")
                videoThumb.isVisible = videoThumbFrame.isVisible
                imgThumb.visibility =
                    if (videoThumbFrame.isVisible) View.INVISIBLE else View.VISIBLE

                transformedData = displayData
                Timber.d("target : $targetMime / ${displayData.uri}")

                if (videoThumbFrame.isVisible) {
                    videoThumbFrame.setAspectRatio(targetDims.x.toFloat() / targetDims.y)
                    // TODO known issue : fullscreen video is not centered
                    videoFullscreenFrame.setAspectRatio(targetDims.x.toFloat() / targetDims.y)
                    getPlayer(context).apply {
                        setVideoSurfaceView(videoThumb)
                        setMediaItem(MediaItem.fromUri(displayData.uri)) // Only available through Uris
                        prepare()
                        play()
                    }
                } else {
                    if (displayData.rawData.isEmpty()) {
                        imgThumb.load(displayData.uri.toString())
                        imgFullscreen.load(displayData.uri.toString())
                    } else {
                        imgThumb.load(displayData.rawData)
                        imgFullscreen.load(displayData.rawData)
                    }
                }

                previewProgress.isVisible = false
                previewGrp.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun transformManhwa(params: TransformParams, firstPageIndex: Int): ByteArray {
        content?.let {
            // Prepare cache folder
            var cacheFolder =
                getOrCreateCacheFolder(this, CACHE_TRANSFORM_MANHWA)
                    ?: return ByteArray(0)
            if (!cacheFolder.deleteRecursively()) return ByteArray(0)
            cacheFolder =
                getOrCreateCacheFolder(this, CACHE_TRANSFORM_MANHWA)
                    ?: return ByteArray(0)

            // Run transformation in preview mode
            transformManhwaChapter(
                this,
                it.imageList.filter { i -> i.isReadable }.drop(firstPageIndex),
                1,
                cacheFolder.toUri(),
                params,
                true
            )

            // Read result from cache folder
            val file = cacheFolder.listFiles()?.firstOrNull() ?: return ByteArray(0)
            return getBinary(this, file.toUri())
        }
        return ByteArray(0)
    }

    @Synchronized
    private fun getCurrentBitmap(): BitmapInfo? {
        if (rawData != null) return rawData
        content?.apply {
            val pages = imageList.filter { it.isReadable }
            if (pages.isEmpty()) return null
            maxPages = pages.size
            val page = pages[pageIndex]
            try {
                getInputStream(this@TransformActivity, page.fileUri.toUri()).use {
                    rawData = BitmapInfo(page.fileUri.toUri(), page.name, it.readBytes())
                    return rawData
                }
            } catch (t: Throwable) {
                Timber.w(t)
            }
        }
        return null
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    private fun getPlayer(context: Context): ExoPlayer {
        if (player != null) return player!!

        val p = ExoPlayer.Builder(context, videoOnlyRenderersFactory).build()
        p.repeatMode = REPEAT_MODE_ONE
        player = p
        return p
    }

    private fun buildParams(): TransformParams {
        return TransformParams(
            Settings.isResizeEnabled,
            Settings.resizeMethod,
            Settings.resizeMethod1Ratio.toFloat() / 100f,
            Settings.resizeMethod2Height,
            Settings.resizeMethod2Width,
            Settings.resizeMethod3Ratio.toFloat() / 100f,
            Settings.resizeMethod5Images,
            Settings.transcodeMethod,
            PictureEncoder.fromValue(Settings.transcodeEncoderAll)!!,
            PictureEncoder.fromValue(Settings.transcodeEncoderLossy)!!,
            PictureEncoder.fromValue(Settings.transcodeEncoderLossless)!!,
            Settings.transcodeQuality,
            PictureEncoder.fromValue(Settings.transcodeEncoderAnim)!!,
            Settings.transcodeAnimQuality,
            skipTransformedPics = Settings.skipTransformedPics,
            isRangeChapters = isRangeChapters,
            range = range
        )
    }

    // Check if no control is in error state
    private fun areInputErrors(): Boolean {
        binding ?: return true
        return binding!!.pager.descendants
            .filter { it is TextInputLayout }
            .map { it as TextInputLayout }
            .any { it.isErrorEnabled }
    }

    private fun onActionClick(params: TransformParams) {
        if (areInputErrors()) return

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val serializedParams = moshi.adapter(TransformParams::class.java).toJson(params)

        val myData: Data = workDataOf(
            "IDS" to contentIds,
            "PARAMS" to serializedParams
        )

        val workManager = WorkManager.getInstance(this@TransformActivity)
        workManager.enqueueUniqueWork(
            R.id.transform_service.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(TransformWorker::class.java)
                .setInputData(myData)
                .addTag(WORK_CLOSEABLE).build()
        )
        finish()
    }

    private class ScreenSlidePagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ResizeFragment()
                1 -> EncodeImgFragment()
                else -> EncodeAnimFragment()
            }
        }

        override fun getItemCount(): Int {
            return 3
        }
    }

    @Suppress("ArrayInDataClass")
    data class BitmapInfo(
        val uri: Uri,
        val name: String,
        val rawData: ByteArray
    ) {
        constructor(uri: Uri) : this(uri, "", ByteArray(0))
        constructor(rawData: ByteArray) : this(Uri.EMPTY, "", rawData)

        private var mProps: ImageProperties? = null
        private var mSize: Long? = null
        private var mDims: Point? = null

        @Synchronized
        fun getProperties(context: Context): ImageProperties {
            if (mProps != null) return mProps!!

            return if (rawData.isNotEmpty()) getImageProperties(rawData)
            else getImageProperties(context, uri) ?: ImageProperties(
                "",
                isLossless = false,
                isAnimated = false
            )
        }

        @Synchronized
        fun getSize(context: Context): Long {
            if (mSize != null) return mSize!!

            return if (rawData.isNotEmpty()) rawData.size.toLong()
            else fileSizeFromUri(context, uri)
        }

        suspend fun getDimensions(context: Context): Point {
            if (mDims != null) return mDims!!
            return getMediaDimensions(context, uri, rawData)
        }
    }
}
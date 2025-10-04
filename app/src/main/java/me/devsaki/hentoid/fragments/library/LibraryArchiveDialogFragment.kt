package me.devsaki.hentoid.fragments.library

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibraryArchiveBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.PickFolderContract
import me.devsaki.hentoid.util.PickerResult
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.persistLocationCredentials
import me.devsaki.hentoid.workers.ArchiveWorker

class LibraryArchiveDialogFragment : BaseDialogFragment<LibraryArchiveDialogFragment.Parent>() {
    companion object {
        const val KEY_CONTENTS = "contents"

        fun invoke(parent: FragmentActivity, contentList: List<Content>) {
            invoke(parent, LibraryArchiveDialogFragment(), getArgs(contentList))
        }

        private fun getArgs(contentList: List<Content>): Bundle {
            val args = Bundle()
            args.putLongArray(
                KEY_CONTENTS, contentList.map { it.id }.toLongArray()
            )
            return args
        }
    }


    // UI
    private var binding: DialogLibraryArchiveBinding? = null

    // === VARIABLES
    private lateinit var contentIds: LongArray

    private val pickFolder =
        registerForActivityResult(PickFolderContract()) {
            onFolderPickerResult(it.first, it.second)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val contentIdArg = arguments?.getLongArray(KEY_CONTENTS)
        require(!(null == contentIdArg || contentIdArg.isEmpty())) { "No content IDs" }
        contentIds = contentIdArg
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogLibraryArchiveBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        refreshControls(true)

        binding?.apply {
            targetFolder.setOnIndexChangeListener { index ->
                when (index) {
                    0 -> Settings.archiveTargetFolder =
                        Settings.Value.TARGET_FOLDER_DOWNLOADS

                    targetFolder.entries.size - 1 -> { // Last item => Pick a folder
                        // Make sure permissions are set
                        if (requireActivity().requestExternalStorageReadWritePermission(
                                RQST_STORAGE_PERMISSION
                            )
                        ) {
                            // Run folder picker
                            pickFolder.launch(StorageLocation.NONE)
                        }
                    }

                    else -> Settings.archiveTargetFolder = Settings.latestArchiveTargetFolderUri
                }
                refreshControls()
            }
            targetFormat.setOnIndexChangeListener { index ->
                Settings.archiveTargetFormat = index
                refreshControls()
            }
            backgroundColor.setOnIndexChangeListener { index ->
                Settings.pdfBackgroundColor = index
                refreshControls()
            }
            overwriteSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isArchiveOverwrite = isChecked
                refreshControls()
            }
            deleteSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isArchiveDeleteOnSuccess = isChecked
                refreshControls()
            }
            action.setOnClickListener { onActionClick(buildWorkerParams()) }
        }
    }

    private fun refreshControls(applyValues: Boolean = false) {
        binding?.apply {
            if (applyValues) {
                val entries = mutableListOf(
                    resources.getString(R.string.folder_device_downloads),
                    resources.getString(R.string.folder_other)
                )
                if (Settings.latestArchiveTargetFolderUri.isNotEmpty()) {
                    val uri = Settings.latestArchiveTargetFolderUri.toUri()
                    if (getDocumentFromTreeUriString(
                            requireContext(),
                            uri.toString()
                        ) != null
                    ) {
                        entries.add(
                            1,
                            getFullPathFromUri(
                                requireContext(),
                                Settings.latestArchiveTargetFolderUri.toUri()
                            )
                        )
                    }
                }
                targetFolder.entries = entries
                targetFolder.index =
                    if (Settings.archiveTargetFolder == Settings.latestArchiveTargetFolderUri) 1 else 0

                targetFormat.index = Settings.archiveTargetFormat

                // PDF only
                backgroundColor.index = Settings.pdfBackgroundColor

                overwriteSwitch.isChecked = Settings.isArchiveOverwrite
                deleteSwitch.isChecked = Settings.isArchiveDeleteOnSuccess
            }

            backgroundColor.isVisible = (2 == targetFormat.index)
        }
    }

    private fun onFolderPickerResult(resultCode: PickerResult, uri: Uri) {
        when (resultCode) {
            PickerResult.OK -> {
                // Persist I/O permissions; keep existing ones if present
                persistLocationCredentials(requireContext(), uri)
                Settings.latestArchiveTargetFolderUri = uri.toString()
                Settings.archiveTargetFolder = uri.toString()
                refreshControls(true)
            }

            else -> {}
        }
    }

    private fun buildWorkerParams(): ArchiveWorker.Params {
        binding!!.apply {
            return ArchiveWorker.Params(
                Settings.archiveTargetFolder,
                targetFormat.index,
                backgroundColor.index,
                overwriteSwitch.isChecked,
                deleteSwitch.isChecked
            )
        }
    }

    private fun onActionClick(params: ArchiveWorker.Params) {
        binding?.apply {
            // Check if no dialog is in error state
            val nbError = container.children
                .filter { it is TextInputLayout }
                .map { it as TextInputLayout }
                .count { it.isErrorEnabled }

            if (nbError > 0) return

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val serializedParams = moshi.adapter(ArchiveWorker.Params::class.java).toJson(params)

            val myData: Data = workDataOf(
                "IDS" to contentIds,
                "PARAMS" to serializedParams
            )

            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueueUniqueWork(
                R.id.archive_service.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(ArchiveWorker::class.java)
                    .setInputData(myData)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
        parent?.leaveSelectionMode()
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun leaveSelectionMode()
    }
}
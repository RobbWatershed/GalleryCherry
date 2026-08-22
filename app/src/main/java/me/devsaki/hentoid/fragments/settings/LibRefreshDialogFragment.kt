package me.devsaki.hentoid.fragments.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogSettingsRefreshBinding
import me.devsaki.hentoid.databinding.IncludeImportStepsBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.FolderScanResult
import me.devsaki.hentoid.util.ImportOptions
import me.devsaki.hentoid.util.PickFolderContract
import me.devsaki.hentoid.util.PickUriResult
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.setAndScanExternalFolder
import me.devsaki.hentoid.util.setAndScanPrimaryFolder
import me.devsaki.hentoid.util.showExistingLibraryDialog
import me.devsaki.hentoid.util.toastShort
import me.devsaki.hentoid.workers.STEP_2_BOOK_FOLDERS
import me.devsaki.hentoid.workers.STEP_3_BOOKS
import me.devsaki.hentoid.workers.STEP_3_PAGES
import me.devsaki.hentoid.workers.STEP_4_QUEUE_FINAL
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Launcher dialog for the following features :
 * - Set/replace download folder
 * - Library refresh
 */
private const val SHOW_OPTIONS = "show_options"
private const val CHOOSE_FOLDER = "choose_folder"
private const val LOCATION = "location"

class LibRefreshDialogFragment : BaseDialogFragment<LibRefreshDialogFragment.Parent>() {
    // == UI
    private var binding1: DialogSettingsRefreshBinding? = null
    private var binding2: IncludeImportStepsBinding? = null

    // === VARIABLES
    private var showOptions = false
    private var chooseFolder = false
    private var location = StorageLocation.NONE

    private var isServiceGracefulClose = false


    companion object {
        fun invoke(
            fragmentManager: FragmentManager,
            showOptions: Boolean,
            chooseFolder: Boolean,
            location: StorageLocation
        ) {
            val fragment = LibRefreshDialogFragment()

            val args = Bundle()
            args.putBoolean(SHOW_OPTIONS, showOptions)
            args.putBoolean(CHOOSE_FOLDER, chooseFolder)
            args.putInt(LOCATION, location.ordinal)
            fragment.arguments = args

            fragment.show(fragmentManager, null)
        }
    }


    private val pickFolder = registerForActivityResult(PickFolderContract(), ::onFolderPickerResult)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding1 = DialogSettingsRefreshBinding.inflate(inflater, container, false)
        requireNotNull(arguments) { "No arguments found" }
        arguments?.apply {
            showOptions = getBoolean(SHOW_OPTIONS, false)
            chooseFolder = getBoolean(CHOOSE_FOLDER, false)
            location = StorageLocation.entries.toTypedArray()[getInt(
                LOCATION,
                StorageLocation.NONE.ordinal
            )]
        }

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
        return binding1?.root
    }

    override fun onDestroyView() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        binding1 = null
        binding2 = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        if (showOptions) { // Show option screen first
            binding1?.apply {
                val canQuickRefresh = (location == StorageLocation.EXTERNAL && !chooseFolder)
                quickRefresh.isVisible = canQuickRefresh
                quickRefresh.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        refreshOptions.isVisible = false
                        refreshOptionsSubgroup.isVisible = false
                        refreshOptions.isChecked = false
                    } else {
                        refreshOptions.isVisible = true
                    }
                }
                refreshOptions.setOnCheckedChangeListener { _, isChecked ->
                    quickRefresh.isVisible = !isChecked && canQuickRefresh
                    if (isChecked) {
                        refreshOptionsSubgroup.visibility = View.VISIBLE
                        val warningVisibility =
                            if (refreshOptionsRenumberPages.isChecked) View.VISIBLE else View.GONE
                        refreshRenumberWarningTxt.visibility = warningVisibility
                        warningImg.visibility = warningVisibility
                    } else {
                        refreshOptionsSubgroup.visibility = View.GONE
                        refreshRenumberWarningTxt.visibility = View.GONE
                        warningImg.visibility = View.GONE
                    }
                }
                refreshOptionsRenumberPages.setOnCheckedChangeListener { _, isChecked ->
                    val visibility = if (isChecked) View.VISIBLE else View.GONE
                    refreshRenumberWarningTxt.visibility = visibility
                    warningImg.visibility = visibility
                }

                actionButton.setOnClickListener {
                    showImportProgressLayout(false, location)
                    runImport(
                        location,
                        refreshOptions.isChecked && refreshOptionsRename.isChecked,
                        refreshOptions.isChecked && refreshOptionsRemovePlaceholders.isChecked,
                        refreshOptions.isChecked && refreshOptionsRenumberPages.isChecked,
                        refreshOptions.isChecked && refreshOptionsRemove1.isChecked,
                        refreshOptions.isChecked && refreshOptionsRemove2.isChecked,
                        quickRefresh.isChecked
                    )
                }
            }
        } else { // Show import progress layout immediately
            showImportProgressLayout(chooseFolder, location)
            if (!chooseFolder) runImport(location)
        }
    }

    private fun runImport(
        location: StorageLocation,
        rename: Boolean = false,
        removePlaceholders: Boolean = false,
        renumberPages: Boolean = false,
        cleanAbsent: Boolean = false,
        cleanNoImages: Boolean = false,
        quickScan: Boolean = false
    ) {
        isCancelable = false

        if (location == StorageLocation.EXTERNAL) {
            val externalUri = Settings.externalLibraryUri.toUri()

            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    try {
                        setAndScanExternalFolder(requireContext(), externalUri, quickScan)
                    } catch (e: Exception) {
                        Timber.w(e)
                        FolderScanResult.KoOther
                    }
                }
                if (FolderScanResult.KoInvalidFolder == res
                    || FolderScanResult.KoCreateFail == res
                    || FolderScanResult.KoDownloadFolder == res
                    || FolderScanResult.KoAlreadyRunning == res
                    || FolderScanResult.KoOther == res
                ) {
                    binding1?.apply {
                        root.showSnackbarFromResult(res)
                        delay(3000)
                    }
                    dismissAllowingStateLoss()
                }
            }
        } else {
            val options = ImportOptions(
                rename,
                removePlaceholders,
                renumberPages,
                cleanAbsent,
                cleanNoImages,
                false
            )
            val uriStr = Settings.getStorageUri(location)
            if (uriStr.isEmpty()) {
                toastShort(R.string.import_invalid_uri)
                dismissAllowingStateLoss()
                return
            }
            val rootUri = uriStr.toUri()

            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    try {
                        setAndScanPrimaryFolder(requireContext(), rootUri, location, false, options)
                    } catch (e: Exception) {
                        Timber.w(e)
                        FolderScanResult.KoOther
                    }
                }

                if (FolderScanResult.KoInvalidFolder == res
                    || FolderScanResult.KoCreateFail == res
                    || FolderScanResult.KoDownloadFolder == res
                    || FolderScanResult.KoAlreadyRunning == res
                    || FolderScanResult.KoOtherPrimary == res
                    || FolderScanResult.KoPrimaryExternal == res
                    || FolderScanResult.OkEmptyFolder == res
                    || FolderScanResult.KoOther == res
                ) {
                    binding1?.apply {
                        root.showSnackbarFromResult(res)
                        delay(3000)
                    }
                    if (FolderScanResult.OkEmptyFolder == res) parent?.onFolderSuccess()
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    private fun showImportProgressLayout(askFolder: Boolean, location: StorageLocation) {
        // Replace launch options layout with import progress layout
        binding1?.apply {
            root.removeAllViews()
            binding2 = IncludeImportStepsBinding.inflate(requireActivity().layoutInflater, root)
        }

        // Memorize UI elements that will be updated during the import events
        binding2?.apply {
            when (location) {
                StorageLocation.PRIMARY_1 -> {
                    importStep1Button.setText(R.string.refresh_step1)
                    importStep1Text.setText(R.string.refresh_step1_select)
                }

                StorageLocation.PRIMARY_2 -> {
                    importStep1Button.setText(R.string.refresh_step1_2)
                    importStep1Text.setText(R.string.refresh_step1_select_2)
                }

                StorageLocation.EXTERNAL -> {
                    importStep1Button.setText(R.string.refresh_step1_select_external)
                    importStep1Text.setText(R.string.refresh_step1_external)
                }

                else -> {
                    // Nothing
                }
            }
            if (askFolder) {
                importStep1Button.visibility = View.VISIBLE
                importStep1Button.setOnClickListener { pickFolder() }
                pickFolder() // Ask right away, there's no reason why the user should click again
            } else {
                importStep1Folder.text = getFullPathFromUri(
                    requireContext(), Settings.getStorageUri(location).toUri()
                )
                importStep1Folder.isVisible = true
                importStep1Text.isVisible = true
                importStep1Check.isVisible = true
                importStep2.isVisible = true
                importStep2Bar.isIndeterminate = true
            }
        }
    }

    private fun pickFolder() {
        // Make sure permissions are set
        if (requireActivity().requestExternalStorageReadWritePermission(RQST_STORAGE_PERMISSION)) {
            Settings.isBrowserMode = false
            pickFolder.launch(location) // Run folder picker
        }
    }

    private fun onFolderPickerResult(result: PickUriResult) {
        when (result) {
            is PickUriResult.Success -> {
                lifecycleScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        return@withContext if (location == StorageLocation.EXTERNAL)
                            setAndScanExternalFolder(requireContext(), result.uri)
                        else setAndScanPrimaryFolder(
                            requireContext(),
                            result.uri,
                            location,
                            true,
                            null
                        )
                    }
                    onScanHentoidFolderResult(res)
                }
            }

            PickUriResult.Cancelled -> {
                binding2?.apply {
                    Snackbar.make(
                        root,
                        R.string.import_canceled,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                }
            }

            PickUriResult.NoUri, PickUriResult.Unknown -> {
                binding2?.apply {
                    Snackbar.make(root, R.string.import_other, BaseTransientBottomBar.LENGTH_LONG)
                        .show()
                }
                isCancelable = true
            }
        }
    }

    private fun onScanHentoidFolderResult(result: FolderScanResult) {
        when (result) {
            FolderScanResult.OkEmptyFolder -> {
                parent?.onFolderSuccess()
                dismissAllowingStateLoss()
            }

            FolderScanResult.OkLibraryDetected -> {
                // Hentoid folder is finally selected at this point -> Update UI
                updateOnSelectFolder()
            }

            is FolderScanResult.OkLibraryDetectedAsk -> {
                updateOnSelectFolder()
                showExistingLibraryDialog(
                    requireContext(),
                    location,
                    result.rootUri.toString()
                ) { onCancelExistingLibraryDialog() }
            }

            else -> {
                binding2?.apply {
                    root.showSnackbarFromResult(result)
                }
                isCancelable = true
            }
        }
    }

    private fun View.showSnackbarFromResult(result: FolderScanResult) {
        val message = when (result) {
            FolderScanResult.KoInvalidFolder -> R.string.import_invalid
            FolderScanResult.KoDownloadFolder -> R.string.import_download_folder
            FolderScanResult.KoCreateFail -> R.string.import_create_fail
            FolderScanResult.KoAlreadyRunning -> R.string.service_running
            FolderScanResult.KoOtherPrimary -> R.string.import_other_primary
            FolderScanResult.KoPrimaryExternal -> R.string.import_other_external_inside_primary
            FolderScanResult.OkEmptyFolder -> R.string.import_empty
            FolderScanResult.KoOther -> R.string.import_other
            FolderScanResult.OkLibraryDetected,
            is FolderScanResult.OkLibraryDetectedAsk -> R.string.none
            // Nothing should happen here
        }

        Snackbar.make(this, message, BaseTransientBottomBar.LENGTH_LONG)
            .show()
    }

    private fun onCancelExistingLibraryDialog() {
        // Revert back to initial state where only the "Select folder" button is visible
        binding2?.apply {
            importStep1Text.visibility = View.INVISIBLE
            importStep1Folder.text = ""
            importStep1Check.visibility = View.INVISIBLE
            importStep2.visibility = View.INVISIBLE
            importStep1Button.isVisible = true
        }
        isCancelable = true
    }

    private fun updateOnSelectFolder() {
        binding2?.apply {
            importStep1Folder.text = getFullPathFromUri(
                requireContext(), Settings.getStorageUri(location).toUri()
            )
            importStep1Folder.isVisible = true
            importStep1Text.isVisible = true
            importStep1Button.visibility = View.INVISIBLE
            importStep1Check.isVisible = true
            importStep2.isVisible = true
            importStep2Bar.isIndeterminate = true
        }
        isCancelable = false
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onImportEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_external
            && event.processId != R.id.import_primary
            && event.processId != R.id.import_primary_pages
        ) return

        importEvent(event)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onImportStickyEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_external
            && event.processId != R.id.import_primary
            && event.processId != R.id.import_primary_pages
        ) return
        EventBus.getDefault().removeStickyEvent(event)
        importEvent(event)
    }

    private fun importEvent(event: ProcessEvent) {
        val nbElts = event.elementsOK + event.elementsKO
        binding2?.apply {
            val progressBar: ProgressBar = when (event.step) {
                STEP_2_BOOK_FOLDERS -> importStep2Bar
                STEP_3_BOOKS -> importStep3Bar
                STEP_3_PAGES -> importStep3SubBar
                else -> importStep4Bar
            }
            if (ProcessEvent.Type.PROGRESS == event.eventType) {
                if (event.progressPc > -1) {
                    progressBar.isIndeterminate = false
                    progressBar.max = 100
                    progressBar.progress = (event.progressPc * 100).roundToInt()
                } else {
                    progressBar.isIndeterminate = true
                }
                when (event.step) {
                    STEP_2_BOOK_FOLDERS -> {
                        importStep2Text.text = event.elementName
                    }

                    STEP_3_BOOKS -> {
                        importStep2Bar.isIndeterminate = false
                        importStep2Bar.max = 1
                        importStep2Bar.progress = 1
                        importStep2Text.visibility = View.GONE
                        importStep2Check.visibility = View.VISIBLE
                        importStep3.visibility = View.VISIBLE
                        if (nbElts >= event.elementsTotal || event.elementsTotal < 1) {
                            importStep3Text.text = resources.getString(
                                R.string.refresh_step3_nomax,
                                nbElts
                            )
                        } else {
                            importStep3Text.text = resources.getString(
                                R.string.refresh_step3,
                                nbElts,
                                event.elementsTotal
                            )
                        }
                    }

                    STEP_3_PAGES -> {
                        progressBar.visibility = View.VISIBLE
                    }

                    STEP_4_QUEUE_FINAL -> {
                        importStep3Check.visibility = View.VISIBLE
                        importStep4.visibility = View.VISIBLE
                    }
                }
            } else if (ProcessEvent.Type.COMPLETE == event.eventType) {
                when (event.step) {
                    STEP_2_BOOK_FOLDERS -> {
                        importStep2Bar.isIndeterminate = false
                        importStep2Bar.max = 1
                        importStep2Bar.progress = 1
                        importStep2Text.visibility = View.GONE
                        importStep2Check.visibility = View.VISIBLE
                        importStep3.visibility = View.VISIBLE
                    }

                    STEP_3_BOOKS -> {
                        importStep3Text.text = resources.getString(
                            R.string.refresh_step3,
                            event.elementsTotal,
                            event.elementsTotal
                        )
                        importStep3Check.visibility = View.VISIBLE
                        importStep4.visibility = View.VISIBLE
                    }

                    STEP_3_PAGES -> {
                        progressBar.visibility = View.GONE
                    }

                    STEP_4_QUEUE_FINAL -> {
                        importStep4Check.visibility = View.VISIBLE

                        isServiceGracefulClose = true
                        // Tell library screens to go back to top
                        EventBus.getDefault().post(
                            CommunicationEvent(
                                CommunicationEvent.Type.SCROLL_TOP,
                                CommunicationEvent.Recipient.ALL
                            )
                        )
                        dismissAllowingStateLoss()
                    }
                }
            }
        }
    }

    /**
     * Service destroyed event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceDestroyed(event: ServiceDestroyedEvent) {
        if (event.service != R.id.import_service) return
        if (!isServiceGracefulClose) {
            binding1?.apply {
                Snackbar.make(
                    root,
                    R.string.import_unexpected,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
            Handler(Looper.getMainLooper()).postDelayed(
                { dismissAllowingStateLoss() },
                3000
            )
        }
    }

    interface Parent {
        fun onFolderSuccess()
    }
}
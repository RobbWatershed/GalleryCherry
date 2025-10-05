package me.devsaki.hentoid.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.moshi.JsonDataException
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.DEFAULT_PRIMARY_FOLDER
import me.devsaki.hentoid.core.DEFAULT_PRIMARY_FOLDER_OLD
import me.devsaki.hentoid.core.HentoidApp.LifeCycleListener.Companion.disable
import me.devsaki.hentoid.core.JSON_ARCHIVE_SUFFIX
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.json.JsonContent
import me.devsaki.hentoid.util.file.ArchiveEntry
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.InnerNameNumberFileComparator
import me.devsaki.hentoid.util.file.NameFilter
import me.devsaki.hentoid.util.file.PdfManager
import me.devsaki.hentoid.util.file.createNoMedia
import me.devsaki.hentoid.util.file.getArchiveEntries
import me.devsaki.hentoid.util.file.getArchiveNamesFilter
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.getPdfNamesFilter
import me.devsaki.hentoid.util.file.isSupportedArchive
import me.devsaki.hentoid.util.file.listFoldersFilter
import me.devsaki.hentoid.util.file.persistNewUriPermission
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.workers.ExternalImportWorker
import me.devsaki.hentoid.workers.PrimaryImportWorker
import me.devsaki.hentoid.workers.data.ExternalImportData
import me.devsaki.hentoid.workers.data.PrimaryImportData
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLDecoder
import java.time.Instant
import java.util.Locale
import java.util.regex.Pattern


private const val EXTERNAL_LIB_TAG = "external-library"

val ENDS_WITH_NUMBER: Pattern by lazy { Pattern.compile(".*\\d+(\\.\\d+)?$") }
val BRACKETS by lazy { "\\[[^(\\[\\])]*]".toRegex() }

enum class PickerResult {
    OK,  // OK - Returned a valid URI
    KO_NO_URI,  // No URI selected
    KO_CANCELED, // Operation canceled
    KO_OTHER // Any other issue
}

enum class ProcessFolderResult {
    OK_EMPTY_FOLDER, // OK - Existing, empty Hentoid folder
    OK_LIBRARY_DETECTED, // OK - En existing Hentoid folder with books
    OK_LIBRARY_DETECTED_ASK, // OK - Existing Hentoid folder with books + we need to ask the user if he wants to import them
    KO_INVALID_FOLDER, // File or folder is invalid, cannot be found
    KO_APP_FOLDER, // Selected folder is the primary location and can't be used as an external location
    KO_DOWNLOAD_FOLDER, // Selected folder is the device's download folder and can't be used as a primary folder (downloads visibility + storage calculation issues)
    KO_CREATE_FAIL, // Hentoid folder could not be created
    KO_ALREADY_RUNNING, // Import is already running
    KO_OTHER_PRIMARY, // Selected folder is inside or contains the other primary location
    KO_PRIMARY_EXTERNAL, // Selected folder is inside or contains the external location
    KO_OTHER // Any other issue
}

private val hentoidFolderNames =
    NameFilter { displayName: String ->
        (displayName.equals(DEFAULT_PRIMARY_FOLDER, ignoreCase = true)
                || displayName.equals(DEFAULT_PRIMARY_FOLDER_OLD, ignoreCase = true))
    }

private val hentoidContentJson =
    NameFilter { displayName: String ->
        displayName.equals(JSON_FILE_NAME_V2, ignoreCase = true)
    }

/**
 * Import options for the Hentoid folder
 */
data class ImportOptions(
    val rename: Boolean = false, // If true, rename folders with current naming convention
    val removePlaceholders: Boolean = false, // If true, books & folders with status PLACEHOLDER will be removed
    val renumberPages: Boolean = false, // If true, renumber pages from books that have numbering gaps
    val cleanNoJson: Boolean = false, // If true, delete folders where no JSON file is found
    val cleanNoImages: Boolean = false, // If true, delete folders where no supported images are found
    val importGroups: Boolean = false // If true, reimport groups from the groups JSON
)


/**
 * Indicate whether the given folder name is a valid Hentoid folder name
 *
 * @param folderName Folder name to test
 * @return True if the given folder name is a valid Hentoid folder name; false if not
 */
fun isHentoidFolderName(folderName: String): Boolean {
    return hentoidFolderNames.accept(folderName)
}


class PickFolderContract : ActivityResultContract<StorageLocation, Pair<PickerResult, Uri>>() {
    override fun createIntent(context: Context, input: StorageLocation): Intent {
        disable() // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        return getFolderPickerIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<PickerResult, Uri> {
        disable() // Restores autolock on app going to background
        return parsePickerResult(resultCode, intent)
    }
}


class PickFileContract : ActivityResultContract<Int, Pair<PickerResult, Uri>>() {
    override fun createIntent(context: Context, input: Int): Intent {
        disable() // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        return getFilePickerIntent()
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<PickerResult, Uri> {
        disable() // Restores autolock on app going to background
        return parsePickerResult(resultCode, intent)
    }
}


private fun parsePickerResult(resultCode: Int, intent: Intent?): Pair<PickerResult, Uri> {
    // Return from the SAF picker
    if (resultCode == Activity.RESULT_OK && intent != null) {
        // Get Uri from Storage Access Framework
        val uri = intent.data
        return if (uri != null) Pair(PickerResult.OK, uri)
        else Pair(PickerResult.KO_NO_URI, Uri.EMPTY)
    } else if (resultCode == Activity.RESULT_CANCELED) {
        return Pair(PickerResult.KO_CANCELED, Uri.EMPTY)
    }
    return Pair(PickerResult.KO_OTHER, Uri.EMPTY)
}

/**
 * Get the intent for the SAF folder picker properly set up, positioned on the Hentoid primary folder
 *
 * @param context Context to be used
 * @return Intent for the SAF folder picker
 */
private fun getFolderPickerIntent(context: Context, location: StorageLocation): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    intent.putExtra(DocumentsContract.EXTRA_PROMPT, context.getString(R.string.dialog_prompt))
    // http://stackoverflow.com/a/31334967/1615876
    intent.putExtra("android.content.extra.SHOW_ADVANCED", true)

    // Start the SAF at the specified location
    if (Settings.getStorageUri(location).isNotEmpty()) {
        val file = getDocumentFromTreeUriString(
            context,
            Settings.getStorageUri(location)
        )
        if (file != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, file.uri)
    }
    return intent
}

/**
 * Get the intent for the SAF file picker properly set up
 *
 * @return Intent for the SAF folder picker
 */
private fun getFilePickerIntent(): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType("*/*")
    // http://stackoverflow.com/a/31334967/1615876
    intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
    disable() // Prevents the app from displaying the PIN lock when returning from the SAF dialog
    return intent
}

/**
 * Scan the given tree URI for a primary folder
 * If none is found there, try to create one
 *
 * @param context         Context to be used
 * @param treeUri         Tree URI of the folder where to find or create the Hentoid folder
 * @param location        Location to associate the folder with
 * @param askScanExisting If true and an existing non-empty Hentoid folder is found, the user will be asked if he wants to import its contents
 * @param options         Import options - See ImportHelper.ImportOptions
 * @return Pair containing :
 * - Left : Standardized result - see ImportHelper.Result
 * - Right : URI of the detected or created primary folder
 */
fun setAndScanPrimaryFolder(
    context: Context,
    treeUri: Uri,
    location: StorageLocation,
    askScanExisting: Boolean,
    options: ImportOptions?
): Pair<ProcessFolderResult, String> {
    // Persist I/O permissions; keep existing ones if present
    persistLocationCredentials(context, treeUri, location)

    // Check if the folder exists
    val docFile = DocumentFile.fromTreeUri(context, treeUri)
    if (null == docFile || !docFile.exists()) {
        Timber.e("Could not find the selected file %s", treeUri.toString())
        return Pair(ProcessFolderResult.KO_INVALID_FOLDER, treeUri.toString())
    }

    // Check if the folder is not the device's Download folder
    val pathSegments = treeUri.pathSegments
    if (pathSegments.size > 1) {
        var firstSegment = pathSegments[1].lowercase(Locale.getDefault())
        firstSegment =
            firstSegment.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0]
        if (firstSegment.startsWith("download") || firstSegment.startsWith("primary:download")) {
            Timber.e("Device's download folder detected : %s", treeUri.toString())
            return Pair(ProcessFolderResult.KO_DOWNLOAD_FOLDER, treeUri.toString())
        }
    }

    // Check if selected folder is separate from Hentoid's other primary location
    val otherLocationUriStr: String =
        if (location == StorageLocation.PRIMARY_1) Settings.getStorageUri(StorageLocation.PRIMARY_2)
        else Settings.getStorageUri(StorageLocation.PRIMARY_1)

    if (otherLocationUriStr.isNotEmpty()) {
        val treeFullPath = getFullPathFromUri(context, treeUri)
        val otherLocationFullPath =
            getFullPathFromUri(context, otherLocationUriStr.toUri())
        if (treeFullPath.startsWith(otherLocationFullPath)) {
            Timber.e(
                "Selected folder is inside the other primary location : %s",
                treeUri.toString()
            )
            return Pair(ProcessFolderResult.KO_OTHER_PRIMARY, treeUri.toString())
        }
        if (otherLocationFullPath.startsWith(treeFullPath)) {
            Timber.e(
                "Selected folder contains the other primary location : %s",
                treeUri.toString()
            )
            return Pair(ProcessFolderResult.KO_OTHER_PRIMARY, treeUri.toString())
        }
    }

    // Check if selected folder is separate from Hentoid's external location
    val extLocationStr = Settings.getStorageUri(StorageLocation.EXTERNAL)
    if (extLocationStr.isNotEmpty()) {
        val treeFullPath = getFullPathFromUri(context, treeUri)
        val extFullPath = getFullPathFromUri(context, extLocationStr.toUri())
        if (treeFullPath.startsWith(extFullPath)) {
            Timber.e("Selected folder is inside the external location : %s", treeUri.toString())
            return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
        }
        if (extFullPath.startsWith(treeFullPath)) {
            Timber.e("Selected folder contains the external location : %s", treeUri.toString())
            return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
        }
    }

    // Retrieve or create the Hentoid folder
    val hentoidFolder = getOrCreateHentoidFolder(context, docFile)
    if (null == hentoidFolder) {
        Timber.e("Could not create Primary folder in folder %s", docFile.uri.toString())
        return Pair(ProcessFolderResult.KO_CREATE_FAIL, treeUri.toString())
    }

    // Set the folder as the app's downloads folder
    val result = createNoMedia(context, hentoidFolder)
    if (result < 0) {
        Timber.e(
            "Could not set the selected root folder (error = %d) %s",
            result,
            hentoidFolder.uri.toString()
        )
        return Pair(
            ProcessFolderResult.KO_INVALID_FOLDER,
            hentoidFolder.uri.toString()
        )
    }

    // Scan the folder for an existing library; start the import
    return if (hasBooks(context, hentoidFolder)) {
        if (!askScanExisting) {
            if (runPrimaryImport(context, location, hentoidFolder.uri.toString(), options))
                Pair(ProcessFolderResult.OK_LIBRARY_DETECTED, hentoidFolder.uri.toString())
            else Pair(ProcessFolderResult.KO_ALREADY_RUNNING, hentoidFolder.uri.toString())
        } else Pair(ProcessFolderResult.OK_LIBRARY_DETECTED_ASK, hentoidFolder.uri.toString())
    } else {
        // Create a new library or import an Hentoid folder without books
        // => Don't run the import worker and settle things here

        // In case that Location was previously populated, drop all books
        if (Settings.getStorageUri(location).isNotEmpty()) {
            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                detachAllPrimaryContent(dao, location)
            } finally {
                dao.cleanup()
            }
        }
        Settings.setStorageUri(location, hentoidFolder.uri.toString())
        Pair(ProcessFolderResult.OK_EMPTY_FOLDER, hentoidFolder.uri.toString())
    }
}

/**
 * Scan the given tree URI for 3rd party books, archives or Hentoid books
 *
 * @param context Context to be used
 * @param treeUri Tree URI of the folder where to find 3rd party books, archives or Hentoid books
 * @return Pair containing :
 * - Left : Standardized result - see ImportHelper.Result
 * - Right : URI of the detected or created primary folder
 */
fun setAndScanExternalFolder(
    context: Context,
    treeUri: Uri,
    quickScan: Boolean = false
): Pair<ProcessFolderResult, String> {
    // Persist I/O permissions; keep existing ones if present
    persistLocationCredentials(context, treeUri, StorageLocation.EXTERNAL)

    // Check if the folder exists
    val docFile = DocumentFile.fromTreeUri(context, treeUri)
    if (null == docFile || !docFile.exists()) {
        Timber.e("Could not find the selected file %s", treeUri.toString())
        return Pair(ProcessFolderResult.KO_INVALID_FOLDER, treeUri.toString())
    }

    // Check if selected folder is separate from one of Hentoid's primary locations
    var primaryUri1 = Settings.getStorageUri(StorageLocation.PRIMARY_1)
    var primaryUri2 = Settings.getStorageUri(StorageLocation.PRIMARY_2)
    if (primaryUri1.isNotEmpty()) primaryUri1 =
        getFullPathFromUri(context, primaryUri1.toUri())
    if (primaryUri2.isNotEmpty()) primaryUri2 =
        getFullPathFromUri(context, primaryUri2.toUri())
    val selectedFullPath = getFullPathFromUri(context, treeUri)
    if (primaryUri1.isNotEmpty() && selectedFullPath.startsWith(primaryUri1)
        || primaryUri2.isNotEmpty() && selectedFullPath.startsWith(primaryUri2)
    ) {
        Timber.w(
            "Trying to set the external library inside a primary library location %s",
            treeUri.toString()
        )
        return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
    }
    if (primaryUri1.isNotEmpty() && primaryUri1.startsWith(selectedFullPath)
        || primaryUri2.isNotEmpty() && primaryUri2.startsWith(selectedFullPath)
    ) {
        Timber.w(
            "Trying to set the external library over a primary library location %s",
            treeUri.toString()
        )
        return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
    }

    // Set the folder as the app's external library folder
    val folderUri = docFile.uri.toString()
    Settings.externalLibraryUri = folderUri

    // Start the import
    return if (runExternalImport(context, quickScan)) Pair(
        ProcessFolderResult.OK_LIBRARY_DETECTED,
        folderUri
    )
    else Pair(ProcessFolderResult.KO_ALREADY_RUNNING, folderUri)
}

/**
 * Persist I/O credentials for the given location, keeping the given existing credentials
 *
 * @param context       Context to use
 * @param treeUri       Uri to add credentials for
 * @param override      Storage location to override with threeUri (optional)
 */
fun persistLocationCredentials(
    context: Context,
    treeUri: Uri,
    override: StorageLocation? = null
) {
    // Keep library roots to the exception of the one we're overrriding
    val locations = mutableListOf(
        StorageLocation.PRIMARY_1,
        StorageLocation.PRIMARY_2,
        StorageLocation.EXTERNAL
    )
    override?.let { locations.remove(it) }

    val keepUris = locations
        .map { Settings.getStorageUri(it) }
        .filterNot { it.isEmpty() }
        .map { it.toUri() }
        .toMutableList()

    // Keep folders mode roots too
    keepUris.addAll(Settings.libraryFoldersRoots.map { it.toUri() })

    persistNewUriPermission(context, treeUri, keepUris)
}

/**
 * Show the dialog to ask the user if he wants to import existing books
 *
 * @param context        Context to be used
 * @param location       Location we're working on
 * @param rootUri        Uri of the selected folder
 * @param cancelCallback Callback to run when the dialog is canceled
 */
fun showExistingLibraryDialog(
    context: Context,
    location: StorageLocation,
    rootUri: String,
    cancelCallback: Runnable?
) {
    MaterialAlertDialogBuilder(
        context,
        context.getIdForCurrentTheme(R.style.Theme_Light_Dialog)
    )
        .setIcon(R.drawable.ic_warning)
        .setCancelable(false)
        .setTitle(R.string.app_name)
        .setMessage(R.string.contents_detected)
        .setPositiveButton(
            R.string.yes
        ) { dialog1, _ ->
            dialog1.dismiss()
            runPrimaryImport(context, location, rootUri, null)
        }
        .setNegativeButton(
            R.string.no
        ) { dialog2, _ ->
            dialog2.dismiss()
            cancelCallback?.run()
        }
        .create()
        .show()
}

/**
 * Detect whether the given folder contains books or not
 * by counting the elements inside each site's download folder (but not its subfolders)
 *
 * NB : this method works approximately because it doesn't try to count JSON files
 * However, findFilesRecursively -the method used by ImportService- is too slow on certain phones
 * and might cause freezes -> we stick to that approximate method for ImportActivity
 *
 * @param context Context to be used
 * @param folder  Folder to examine
 * @return True if the current Hentoid folder contains at least one book; false if not
 */
private fun hasBooks(context: Context, folder: DocumentFile): Boolean {
    try {
        FileExplorer(context, folder.uri).use { explorer ->
            val folders = explorer.listFolders(context, folder)

            // Filter out download subfolders among listed subfolders
            for (subfolder in folders) {
                val subfolderName = subfolder.name
                if (subfolderName != null) {
                    for (s in Site.entries)
                        if (subfolderName.equals(s.folder, ignoreCase = true)) {
                            // Search subfolders within identified download folders
                            // NB : for performance issues, we assume the mere presence of a subfolder inside a download folder means there's an existing book
                            if (explorer.hasFolders(subfolder)) return true
                            break
                        }
                }
            }
        }
    } catch (e: IOException) {
        Timber.w(e)
    }
    return false
}

/**
 * Detect or create the Hentoid app folder inside the given base folder
 *
 * @param context    Context to be used
 * @param baseFolder Root folder to search for or create the Hentoid folder
 * @return DocumentFile representing the found or newly created Hentoid folder
 */
private fun getOrCreateHentoidFolder(
    context: Context,
    baseFolder: DocumentFile
): DocumentFile? {
    val targetFolder = getExistingHentoidDirFrom(context, baseFolder)
    return targetFolder ?: baseFolder.createDirectory(DEFAULT_PRIMARY_FOLDER)
}

/**
 * Try and detect if the Hentoid primary folder is, or is inside the given folder
 *
 * @param context Context to use
 * @param root    Folder to search the Hentoid folder in
 * @return Detected Hentoid folder; null if nothing detected
 */
fun getExistingHentoidDirFrom(context: Context, root: DocumentFile): DocumentFile? {
    if (!root.exists() || !root.isDirectory || null == root.name) return null

    // Selected folder _is_ the Hentoid folder
    if (isHentoidFolderName(root.name!!)) return root

    // If not, look for it in its children
    val hentoidDirs = listFoldersFilter(context, root, hentoidFolderNames)
    return if (hentoidDirs.isNotEmpty()) hentoidDirs[0] else null
}

/**
 * Run the import of the Hentoid primary library
 *
 * @param context Context to use
 * @param options Import options to use
 */
private fun runPrimaryImport(
    context: Context,
    location: StorageLocation,
    targetRoot: String,
    options: ImportOptions?
): Boolean {
    if (ExternalImportWorker.isRunning(context) || PrimaryImportWorker.isRunning(context)) return false
    me.devsaki.hentoid.notification.import_.init(context)
    val builder = PrimaryImportData.Builder()
    builder.setLocation(location)
    builder.setTargetRoot(targetRoot)
    if (options != null) {
        builder.setRefreshRename(options.rename)
        builder.setRefreshRemovePlaceholders(options.removePlaceholders)
        builder.setRenumberPages(options.renumberPages)
        builder.setRefreshCleanNoJson(options.cleanNoJson)
        builder.setRefreshCleanNoImages(options.cleanNoImages)
        builder.setImportGroups(options.importGroups)
    }
    val workManager = WorkManager.getInstance(context)
    workManager.enqueueUniqueWork(
        R.id.import_service.toString(), ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequest.Builder(PrimaryImportWorker::class.java)
            .setInputData(builder.data)
            .addTag(WORK_CLOSEABLE).build()
    )
    return true
}

/**
 * Run the import of the Hentoid external library
 *
 * @param context Context to use
 */
fun runExternalImport(
    context: Context,
    behold: Boolean = false,
    folders: List<String> = emptyList()
): Boolean {
    if (ExternalImportWorker.isRunning(context) || PrimaryImportWorker.isRunning(context)) return false
    me.devsaki.hentoid.notification.import_.init(context)
    val builder = ExternalImportData.Builder()
    builder.setBehold(behold)
    if (behold) builder.setFolders(folders)
    val workManager = WorkManager.getInstance(context)
    workManager.enqueueUniqueWork(
        R.id.external_import_service.toString(), ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequest.Builder(ExternalImportWorker::class.java)
            .setInputData(builder.data)
            .addTag(WORK_CLOSEABLE).build()
    )
    return true
}

/**
 * Recursively scan the contents of the given folder, up to 4 sublevels
 *
 * @param context Context to use
 * @param dao DAO to use to populate entities
 * @param parent Parent of the folder to scan (cuz DocumentFile.getParentFile can't stand on its own)
 * @param toScan Folder to scan
 * @param explorer FileExplorer to use
 * @param progress ProgressManager to use
 * @param parentNames Names of all parent folders
 * @param log Log to write to (optional)
 * @param isCanceled Returns true if the process has been canceled upstream
 * @param onFolderFound Callback when a folder has been found
 * @param onContentFound Callback when a Content has been found
 */
fun scanFolderRecursive(
    context: Context,
    dao: CollectionDAO,
    parent: Uri?,
    toScan: DocumentFile,
    explorer: FileExplorer,
    progress: ProgressManager?,
    parentNames: List<String>,
    log: MutableList<LogEntry>? = null,
    isCanceled: (() -> Boolean)? = null,
    onFolderFound: (DocumentFile) -> Unit,
    onContentFound: (Content) -> Unit,
) {
    assertNonUiThread()
    if (isCanceled?.invoke() == true) return
    if (parentNames.size > 4) return  // We've descended too far
    val rootName = toScan.name ?: ""

    Timber.d(">>>> scan root ${URLDecoder.decode(toScan.uri.toString(), "UTF-8")}")
    // Ignore syncthing subfolders
    val files = explorer.listDocumentFiles(context, toScan)
        .filterNot { it.isDirectory && (it.name ?: "").startsWith(".st") }

    val subFolders: MutableList<DocumentFile> = ArrayList()
    val images: MutableList<DocumentFile> = ArrayList()
    val archivesPdf: MutableList<DocumentFile> = ArrayList()
    val jsons: MutableList<DocumentFile> = ArrayList()
    val contentJsons: MutableList<DocumentFile> = ArrayList()

    onFolderFound(toScan)

    // Look for the interesting stuff
    for (file in files) {
        val fileName = file.name ?: ""
        if (file.isDirectory) subFolders.add(file)
        else if (imageNamesFilter.accept(fileName)) images.add(file)
        else if (getArchiveNamesFilter().accept(fileName)) archivesPdf.add(file)
        else if (getPdfNamesFilter().accept(fileName)) archivesPdf.add(file)
        else if (getJsonNamesFilter().accept(fileName)) {
            jsons.add(file)
            if (getContentJsonNamesFilter().accept(fileName)) contentJsons.add(file)
        }
    }
    val nbItems = archivesPdf.size + subFolders.size
    var nbProcessed = 0

    progress?.let { prg ->
        if (parentNames.isEmpty()) {
            // Level 0 : init steps according to found content
            subFolders.forEach { prg.setProgress(it.name ?: "", 0f) }
            archivesPdf.forEach { prg.setProgress(it.name ?: "", 0f) }
        }
    }

    // If at least 2 subfolders and all of them ends with a number, we've got a multi-chapter book
    var hasScannedChapters = false
    if (subFolders.size >= 2) {
        val allSubfoldersEndWithNumber =
            subFolders.mapNotNull { it.name }.all { ENDS_WITH_NUMBER.matcher(it).matches() }
        if (allSubfoldersEndWithNumber) {
            // Make certain folders contain actual books by peeking the 1st one (could be a false positive, i.e. folders per year '1990-2000')
            val nbPicturesInside = explorer.countFiles(subFolders[0], imageNamesFilter)
            if (nbPicturesInside > 1) {
                val json = getFileWithName(jsons, JSON_FILE_NAME_V2)
                hasScannedChapters = true
                onContentFound(
                    scanChapterFolders(
                        context, toScan, subFolders, explorer, parentNames, dao, json
                    )
                )
            }
            // Look for archives inside; if there's one inside the 1st folder, load them as a chapters
            val nbArchivesInside = explorer.countFiles(subFolders[0], getArchiveNamesFilter())
            if (1 == nbArchivesInside) {
                scanForArchivesPdf(
                    context,
                    toScan,
                    subFolders,
                    explorer,
                    parentNames,
                    dao,
                    true
                ).forEach { onContentFound(it) }
            }
        }
    }

    // We've got an archived book
    if (archivesPdf.isNotEmpty()) {
        for (archive in archivesPdf) {
            val content = jsonToContent(context, dao, jsons, archive.name ?: "")
            val c = scanArchivePdf(
                context, toScan.uri, archive, parentNames, StatusContent.EXTERNAL, content
            )
            // Valid archive
            if (0 == c.first) onContentFound(c.second!!)
            else {
                // Invalid archive
                val message = when (c.first) {
                    1 -> "Archive ignored (contains another archive) : %s"
                    else -> "Archive ignored (unsupported pictures or corrupted archive) : %s"
                }
                trace(Log.INFO, 0, log, message, archive.name ?: "<name not found>")
            }
            progress?.let { prg ->
                if (parentNames.isEmpty()) {
                    progress.setProgress(archive.name ?: "", 1f)
                } else if (1 == parentNames.size) {
                    progress.setProgress(rootName, ++nbProcessed * 1f / nbItems)
                }
            }
            if (isCanceled?.invoke() == true) return
        }
    }

    // We've got a regular book
    if (images.size > 2 || contentJsons.isNotEmpty()) {
        val json = getFileWithName(contentJsons, JSON_FILE_NAME_V2)
        onContentFound(
            scanBookFolder(
                context,
                parent,
                toScan,
                parentNames,
                StatusContent.EXTERNAL,
                explorer,
                dao,
                images,
                json
            )
        )
        if (1 == parentNames.size) {
            progress?.setProgress(rootName, 1f)
        }
    }

    // Stop here if subfolders have already been scanned as chapters
    if (hasScannedChapters) return

    // Go down one level
    val newParentNames: MutableList<String> = ArrayList(parentNames)
    newParentNames.add(rootName)
    for (subfolder in subFolders) {
        scanFolderRecursive(
            context,
            dao,
            toScan.uri,
            subfolder,
            explorer,
            progress,
            newParentNames,
            log,
            isCanceled,
            onFolderFound,
            onContentFound
        )
    }
}

/**
 * Create a Content from the given folder
 *
 * @param context      Context to use
 * @param parentUri Parent folder of bookFolder (cuz DocumentFile.getParentFile can't stand on its own)
 * @param bookFolder   Folder to analyze
 * @param parentNames  Names of parent folders, for formatting purposes; last of the list is the immediate parent of bookFolder
 * @param targetStatus Target status of the Content to create
 * @param explorer     FileExplorer to use
 * @param dao          CollectionDAO to use
 * @param files        List of images to match files with; null if they have to be recreated from the files
 * @param jsonFile     JSON file to use, if one has been detected upstream; null if it has to be detected
 * @return Content created from the folder information and files
 */
fun scanBookFolder(
    context: Context,
    parentUri: Uri?,
    bookFolder: DocumentFile,
    parentNames: List<String>,
    targetStatus: StatusContent,
    explorer: FileExplorer? = null,
    dao: CollectionDAO? = null,
    files: List<DocumentFile>? = null,
    jsonFile: DocumentFile? = null
): Content {
    Timber.d(">>>> scan book folder %s", bookFolder.uri)
    val now = Instant.now().toEpochMilli()
    val isExternal = (targetStatus == StatusContent.EXTERNAL)

    var result: Content? = null
    if (jsonFile != null && dao != null) {
        try {
            jsonToObject(
                context, jsonFile,
                JsonContent::class.java
            )?.let { content ->
                result = content.toEntity(dao)
                result.jsonUri = jsonFile.uri.toString()
            }
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }
    }

    // JSON can't be used
    if (null == result) result =
        createContentFromDocumentFile(bookFolder, parentNames, now, isExternal)

    if (isExternal) result.addAttributes(newExternalAttribute())
    result.status = targetStatus
    result.setStorageDoc(bookFolder)
    if (null != parentUri) result.parentStorageUri = parentUri.toString()
    if (0L == result.downloadDate) result.downloadDate = now
    if (isExternal) result.downloadCompletionDate = now
    result.lastEditDate = now
    val images: MutableList<ImageFile> = ArrayList()
    val theExplorer = explorer ?: FileExplorer(context, bookFolder)
    try {
        images.addAll(
            scanFolderImages(
                context,
                bookFolder,
                theExplorer,
                targetStatus,
                false,
                result.imageList,
                images.maxOfOrNull { it.order } ?: 0,
                files
            )
        )
    } finally {
        // Free local FileExplorer
        if (null == explorer) theExplorer.close()
    }

    // Detect cover
    val coverExists = images.any { it.isCover }
    if (!coverExists) createCover(images)

    // If streamed, keep everything and update cover URI
    if (result.downloadMode == DownloadMode.STREAM) {
        val coverFile = images.firstOrNull { it.isCover }
        if (coverFile != null) {
            result.cover.fileUri = coverFile.fileUri
            result.cover.size = coverFile.size
        }
    } else { // Set all detected images
        result.setImageFiles(images)
    }
    if (0 == result.qtyPages) {
        val countUnreadable = images.filterNot { it.isReadable }.count()
        result.qtyPages = images.size - countUnreadable // Minus unreadable pages (cover thumb)
    }
    result.computeSize()
    return result
}

private fun cleanTitle(s: String?): String {
    var result = s ?: ""
    result = result.replace("_", " ")
    // Remove expressions between []'s
    result = result.replace(BRACKETS, "")
    return result.trim()
}

/**
 * Create a Content from the given parent folder and chapter subfolders, merging all "chapters" into one content
 *
 * @param context        Context to use
 * @param parent         Parent folder to take into account for title and download date
 * @param chapterFolders Folders containing chapters to scan for images
 * @param explorer       FileExplorer to use
 * @param parentNames    Names of parent folders, for formatting purposes; last of the list is the immediate parent of parent
 * @param dao            CollectionDAO to use
 * @param jsonFile       JSON file to use, if one has been detected upstream; null if it needs to be detected
 * @return Content created from the folder information, subfolders and files
 */
fun scanChapterFolders(
    context: Context,
    parent: DocumentFile,
    chapterFolders: List<DocumentFile>,
    explorer: FileExplorer,
    parentNames: List<String>,
    dao: CollectionDAO,
    jsonFile: DocumentFile?
): Content {
    Timber.d(">>>> scan chapter folder %s", parent.uri)
    val now = Instant.now().toEpochMilli()

    var result: Content? = null
    if (jsonFile != null) {
        try {
            jsonToObject(
                context, jsonFile,
                JsonContent::class.java
            )?.let { content ->
                result = content.toEntity(dao)
                result.jsonUri = jsonFile.uri.toString()
            }
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }
    }

    // JSON can't be used
    if (null == result) result = createContentFromDocumentFile(parent, parentNames, now)

    result.addAttributes(newExternalAttribute())
    result.status = StatusContent.EXTERNAL
    result.setStorageDoc(parent)
    if (0L == result.downloadDate) result.downloadDate = now
    result.downloadCompletionDate = now
    result.lastEditDate = Instant.now().toEpochMilli()
    val images: MutableList<ImageFile> = ArrayList()
    // Order subfolders by name, using natural ordering
    val sortedChapterFolders = chapterFolders.sortedWith(InnerNameNumberFileComparator())
    // Scan pages across all subfolders; create a chapter for each
    sortedChapterFolders.forEachIndexed { idx, chapterFolder ->
        val imgs = scanFolderImages(
            context,
            chapterFolder,
            explorer,
            StatusContent.EXTERNAL,
            true,
            result.imageList,
            images.maxOfOrNull { it.order } ?: 0
        )
        val chp = Chapter(idx, chapterFolder.uri.toString(), chapterFolder.name ?: "")
        chp.setContent(result)
        chp.setImageFiles(imgs)
        imgs.forEach { it.setChapter(chp) }
        images.addAll(imgs)
    }
    val coverExists = images.any { it.isCover }
    if (!coverExists) createCover(images)
    result.setImageFiles(images)
    if (0 == result.qtyPages) {
        val countUnreadable = images.filterNot { it.isReadable }.count()
        result.qtyPages = images.size - countUnreadable // Minus unreadable pages (cover thumb)
    }
    result.computeSize()
    return result
}

/**
 * Populate on enrich the given image list according to the contents of the given folder
 *
 * @param context                Context to use
 * @param bookFolder             Folder to scan image files from
 * @param explorer               FileExplorer to use
 * @param targetStatus           Target status of the detected images
 * @param addFolderNametoImgName True if the parent folder name has to be added to detected images name
 * @param contentImages          Image of currently processed Content
 * @param startingOrder          Order to start numbering detected images from
 * @param imgs                   Image file list, if already listed upstream; null if it needs to be listed
 */
private fun scanFolderImages(
    context: Context,
    bookFolder: DocumentFile,
    explorer: FileExplorer,
    targetStatus: StatusContent,
    addFolderNametoImgName: Boolean,
    contentImages: List<ImageFile>,
    startingOrder: Int,
    imgs: List<DocumentFile>? = null
): List<ImageFile> {
    val imageFiles = imgs ?: explorer.listFiles(context, bookFolder, imageNamesFilter)
    val folderName = bookFolder.name ?: ""
    val namePrefix = if (addFolderNametoImgName) "$folderName-" else ""
    val results = createImageListFromFiles(imageFiles, targetStatus, startingOrder, namePrefix)
    mapMetadata(results, contentImages)
    return results
}

/**
 * Map metadata from reference images to recipient images, based on their name
 * NB : No element will be added nor removed from 'recipient'
 */
private fun mapMetadata(recipient: List<ImageFile>, ref: List<ImageFile>) {
    // Clear chapters contained in ref images (their content will be replaced by images from 'recipient')
    ref.mapNotNull { it.linkedChapter }.distinct().forEach { it.clearImageFiles() }
    // Copy metadata from ref to recipient images, using name as pivot
    recipient.forEach { img ->
        ref.firstOrNull { it.name == img.name }?.let {
            img.apply {
                url = it.url
                pageUrl = it.pageUrl
                read = it.read
                favourite = it.favourite
                isTransformed = it.isTransformed
                isCover = it.isCover
                chapter = it.chapter
            }
        }
    }
}

/**
 * Create a cover and add it to the given image list
 *
 * @param images Image list to generate the cover from (and add it to)
 */
fun createCover(images: MutableList<ImageFile>) {
    if (images.isNotEmpty()) {
        val img = ImageFile(images[0], populateContent = true, populateChapter = true)
        img.isCover = true
        img.name = THUMB_FILE_NAME
        // Create a new cover entry from the 1st element
        images.add(0, img)
    }
}

/**
 * Return a list with the attribute flagging a book as external
 *
 * @return List with the attribute flagging a book as external
 */
private fun newExternalAttribute(): List<Attribute> {
    return listOf(
        Attribute(
            AttributeType.TAG,
            EXTERNAL_LIB_TAG,
            EXTERNAL_LIB_TAG,
            Site.NONE
        )
    )
}

/**
 * Remove the attribute flagging the given book as external, if it exists
 *
 * @param content Content to remove the "external" attribute flag, if it has been set
 */
fun removeExternalAttributes(content: Content) {
    content.putAttributes(content.attributes.filterNot { a ->
        a.name.equals(EXTERNAL_LIB_TAG, ignoreCase = true)
    }.toList())
    if (content.status == StatusContent.EXTERNAL) content.status = StatusContent.DOWNLOADED
}

/**
 * Convert the given list of parent folder names into a list of Attribute of type TAG
 *
 * @param parentNames List of parent folder names
 * @return Representation of parent folder names as Attributes of type TAG
 */
private fun parentNamesAsTags(parentNames: List<String>): AttributeMap {
    val result = AttributeMap()
    // Don't include the very first one, it's the name of the root folder of the library
    if (parentNames.size > 1) {
        for (i in 1 until parentNames.size) result.add(
            Attribute(
                AttributeType.TAG,
                parentNames[i], parentNames[i], Site.NONE
            )
        )
    }
    return result
}

/**
 * Create Content from every archive or PDF inside the given subfolders
 *
 * @param context     Context to use
 * @param subFolders  Subfolders to scan for archives
 * @param explorer    FileExplorer to use
 * @param parentNames Names of parent folders, for formatting purposes; last of the list is the immediate parent of the scanned folders
 * @param dao         CollectionDAO to use
 * @param chaptered   True to create one single book containing the 1st archive of each subfolder as chapters; false to create one book per archive
 * @return List of Content created from every archive inside the given subfolders
 */
fun scanForArchivesPdf(
    context: Context,
    parent: DocumentFile,
    subFolders: List<DocumentFile>,
    explorer: FileExplorer,
    parentNames: List<String>,
    dao: CollectionDAO,
    chaptered: Boolean = false
): List<Content> {
    val result: MutableList<Content> = ArrayList()
    for (subfolder in subFolders) {
        val files = explorer.listFiles(context, subfolder, null)
        val archives: MutableList<DocumentFile> = ArrayList()
        val jsons: MutableList<DocumentFile> = ArrayList()

        // Look for the interesting stuff
        for (file in files) {
            val fileName = file.name ?: ""
            if (getArchiveNamesFilter().accept(fileName)) archives.add(file)
            else if (getPdfNamesFilter().accept(fileName)) archives.add(file)
            else if (getJsonNamesFilter().accept(fileName)) jsons.add(file)
        }
        for (archive in archives) {
            val content = jsonToContent(context, dao, jsons, archive.name ?: "")
            val c = scanArchivePdf(
                context,
                subfolder.uri,
                archive,
                parentNames,
                StatusContent.EXTERNAL,
                content
            )
            if (0 == c.first) {
                result.add(c.second!!)
                if (chaptered) break // Just read the 1st archive of any subfolder
            }
        }
    }
    if (chaptered) { // Return one single book with all results as chapters
        val content =
            Content(
                site = Site.NONE,
                title = parentNames.lastOrNull() ?: "",
                dbUrl = "",
                downloadDate = subFolders.lastOrNull()?.lastModified() ?: Instant.now()
                    .toEpochMilli()
            )
        content.addAttributes(parentNamesAsTags(parentNames))

        content.addAttributes(newExternalAttribute())
        content.status = StatusContent.EXTERNAL
        content.setStorageDoc(parent)
        val now = Instant.now().toEpochMilli()
        if (0L == content.downloadDate) content.downloadDate = now
        content.downloadCompletionDate = now
        content.lastEditDate = Instant.now().toEpochMilli()

        val chapterStr = context.getString(R.string.gallery_chapter_prefix)
        var chapterOffset = 0
        val chapters: MutableList<Chapter> = ArrayList()
        val images: MutableList<ImageFile> = ArrayList()
        result.forEachIndexed { cidx, c ->
            val chapter = Chapter(cidx + 1, c.parentStorageUri ?: "", chapterStr + " " + (cidx + 1))
            chapter.setContent(content)
            chapter.setImageFiles(c.imageList.filter { i -> i.isReadable })
            chapter.imageList.forEachIndexed { iidx, img ->
                img.order = chapterOffset + iidx + 1
                img.computeName(5)
                img.setChapter(chapter)
            }
            chapters.add(chapter)
            chapter.imageList.let {
                images.addAll(it)
                chapterOffset += it.size
            }
        }
        val coverExists = images.any { i -> i.isCover }
        if (!coverExists) createCover(images)
        content.setImageFiles(images)
        if (0 == content.qtyPages) {
            val countUnreadable = images.filterNot { it.isReadable }.count()
            content.qtyPages = images.size - countUnreadable // Minus unreadable pages (cover thumb)
        }
        content.setChapters(chapters)
        content.computeSize()
        return listOf(content)
    } else return result
}

/**
 * Create a content from the given archive / PDF file
 * NB : any returned Content with the IGNORED status shouldn't be taken into account by the caller
 *
 * @param context       Context to use
 * @param parent        Parent folder where the archive is located
 * @param doc           Archive file to scan
 * @param parentNames   Names of parent folders, for formatting purposes; last of the list is the immediate parent of parentFolder
 * @param targetStatus  Target status of the Content to create
 * @param content       Content metadata to use; null if has to be created from scratch
 * @return Pair containing
 *  Key : Return code
 *      0 = success
 *      1 = failure; file just contains other archives
 *      2 = failure; file doesn't contain supported images or is corrupted
 *  Value : Content created from the given archive, ur null if return code > 0
 */
fun scanArchivePdf(
    context: Context,
    parent: Uri,
    doc: DocumentFile,
    parentNames: List<String>,
    targetStatus: StatusContent,
    content: Content?
): Pair<Int, Content?> {
    val isPdf = doc.getExtension().equals("pdf", true)
    val now = Instant.now().toEpochMilli()

    var entries = emptyList<ArchiveEntry>()
    try {
        entries = if (isPdf) {
            val pdfMgr = PdfManager()
            pdfMgr.getEntries(context, doc)
        } else context.getArchiveEntries(doc)
    } catch (e: Exception) {
        Timber.w(e)
    }

    val archiveEntries = entries.filter { isSupportedArchive(it.path) }
    val imageEntries = entries.filter { isSupportedImage(it.path) }.filter { it.size > 0 }

    if (imageEntries.isEmpty()) {
        // If it just contains other archives, raise an error
        if (archiveEntries.isNotEmpty()) return Pair(1, null)
        // If it contains no supported images, raise an error
        return Pair(2, null)
    }

    val images = createImageListFromArchiveEntries(
        doc.uri,
        imageEntries,
        targetStatus,
        0,
        ""
    ).toMutableList()
    val coverExists = images.any { it.isCover }
    if (!coverExists) createCover(images)

    // Create content envelope
    var result = content

    // JSON can't be used
    if (null == result) result = createContentFromDocumentFile(doc, parentNames, now)

    result.apply {
        addAttributes(newExternalAttribute())
        status = targetStatus
        setStorageDoc(doc) // Here storage URI is a file URI, not a folder
        parentStorageUri = parent.toString()
        if (0L == downloadDate) downloadDate = now
        downloadCompletionDate = now
        lastEditDate = now
        if (null == content) setImageFiles(images)
        else mapMetadata(images, content.imageList)
        if (0 == qtyPages) {
            val countUnreadable = images.filterNot { it.isReadable }.count()
            qtyPages = images.size - countUnreadable // Minus unreadable pages (cover thumb)
        }
        computeSize()
        // e.g. when the ZIP table doesn't contain any size entry
        if (size <= 0) size = doc.length()
    }
    return Pair(0, result)
}

/**
 * Add the given list of bookmarks to the DB, handling duplicates
 * Bookmarks that have the same URL as existing ones won't be imported
 *
 * @param dao       CollectionDAO to use
 * @param bookmarks List of bookmarks to add to the existing bookmarks
 * @return Quantity of new integrated bookmarks
 */
fun importBookmarks(dao: CollectionDAO, bookmarks: List<SiteBookmark>): Int {
    // Don't import bookmarks that have the same URL as existing ones
    val existingBookmarkUrls: Set<SiteBookmark> = HashSet(dao.selectAllBookmarks())
    val bookmarksToImport = HashSet(bookmarks).filterNot { o: SiteBookmark ->
        existingBookmarkUrls.contains(o)
    }.toList()
    dao.insertBookmarks(bookmarksToImport)
    return bookmarksToImport.size
}

/**
 * Add the given list of renaming rules to the DB, handling duplicates
 * Rules that have the same attribute type, source and target string as existing ones won't be imported
 *
 * @param dao   CollectionDAO to use
 * @param rules List of rules to add to the existing rules
 */
fun importRenamingRules(dao: CollectionDAO, rules: List<RenamingRule>) {
    val existingRules = HashSet(dao.selectRenamingRules(AttributeType.UNDEFINED, null))
    val rulesToImport = HashSet(rules).filterNot { o: RenamingRule ->
        existingRules.contains(o)
    }.toList()
    dao.insertRenamingRules(rulesToImport)
}

/**
 * Return the first file with the given name (without extension) among the given list of files
 *
 * @param files List of files to search into
 * @param name  File name to detect
 * @return First file with the given name among the given list, or null if none matches the given name
 */
fun getFileWithName(files: List<DocumentFile>, name: String): DocumentFile? {
    val targetBareName = getFileNameWithoutExtension(name)
    val file = files.firstOrNull { f ->
        f.name != null && getFileNameWithoutExtension(f.name!!)
            .equals(targetBareName, ignoreCase = true)
    }
    return file
}

private fun createContentFromDocumentFile(
    doc: DocumentFile,
    parentNames: List<String>,
    now: Long,
    isExternal: Boolean = true
): Content {
    Timber.v(
        ">> Creating metadata from scratch : ${
            URLDecoder.decode(doc.uri.toString(), "UTF-8")
        }"
    )
    var title = getFileNameWithoutExtension(doc.name ?: "")
    var artist = ""

    val namingPattern = Settings.getImportExtNamePattern()
    if (isExternal && namingPattern != Settings.Default.IMPORT_NAME_PATTERN) {
        val res = Settings.importExtRgx
        val matcher = res.first.matcher(title)
        if (matcher.find()) {
            if (res.second) matcher.group("title")?.let { title = it.trim() }
            if (res.third) matcher.group("artist")?.let { artist = it.trim() }
        }
    } else {
        title = cleanTitle(title)
    }

    // Tachiyomi downloads - include parent folder name as title
    if (parentNames.isNotEmpty()
        && (title.lowercase(Locale.getDefault()).startsWith("chapter")
                ||
                title.lowercase(Locale.getDefault()).startsWith("chap.")
                )
    ) {
        title = cleanTitle(parentNames[parentNames.size - 1]) + " " + title
    }

    val result = Content(
        site = findSiteInParentNames(parentNames),
        title = title,
        dbUrl = "",
        downloadDate = doc.lastModified()
    )
    result.downloadCompletionDate = now

    if (artist.isNotBlank())
        result.addAttributes(
            listOf(Attribute(AttributeType.ARTIST, artist, artist, Site.NONE))
        )

    result.addAttributes(parentNamesAsTags(parentNames))
    return result
}

private fun findSiteInParentNames(parentNames: List<String>): Site {
    parentNames.forEach { pn ->
        Site.entries.forEach { s ->
            if (pn.equals(s.folder, ignoreCase = true)) return s
        }
    }
    return Site.NONE
}

fun createJsonFileFor(
    context: Context,
    content: Content,
    explorer: FileExplorer,
    log: MutableList<LogEntry>?
) {
    if (content.jsonUri.isEmpty()) {
        var jsonUri: Uri? = null
        try {
            jsonUri = createJsonFileFor(context, content, explorer)
        } catch (ioe: IOException) {
            Timber.w(ioe) // Not blocking
            trace(
                Log.WARN,
                1,
                log,
                "Could not create JSON in %s",
                content.storageUri
            )
        }
        if (jsonUri != null) content.jsonUri = jsonUri.toString()
    }
}

@Throws(IOException::class)
private fun createJsonFileFor(
    context: Context,
    content: Content,
    explorer: FileExplorer
): Uri? {
    if (content.storageUri.isEmpty()) return null

    // Check if the storage URI is valid
    val contentFolder: DocumentFile? = if (content.isArchive || content.isPdf) {
        getDocumentFromTreeUriString(context, content.parentStorageUri ?: "")
    } else {
        getDocumentFromTreeUriString(context, content.storageUri)
    }
    if (null == contentFolder) return null

    // If a JSON file already exists at that location, use it as is, don't overwrite it
    val jsonName = if (content.isArchive || content.isPdf) {
        // Use the archive name + a suffix to avoid using by mistake a JSON generated by another app (e.g. Kuro Reader)
        val archiveFile = getFileFromSingleUriString(context, content.storageUri)
        getFileNameWithoutExtension(archiveFile?.name ?: "") + JSON_ARCHIVE_SUFFIX + ".json"
    } else {
        JSON_FILE_NAME_V2
    }

    val jsonFile = explorer.findFile(context, contentFolder, jsonName)
    return if (jsonFile != null && jsonFile.exists()) jsonFile.uri
    else jsonToFile(
        context,
        JsonContent(content),
        JsonContent::class.java,
        contentFolder,
        jsonName
    ).uri
}

fun existsInCollection(
    content: Content,
    dao: CollectionDAO,
    searchByStorageUri: Boolean = false,
    log: MutableList<LogEntry>? = null
): Boolean {
    // If the same book folder is already in the DB, that means the user is trying to import
    // a subfolder of the Hentoid main folder (yes, it has happened) => ignore these books
    var duplicateOrigin = "folder"
    var existingDuplicate: Content? = null

    if (searchByStorageUri)
        existingDuplicate = dao.selectContentByStorageUri(content.storageUri, false)


    // The very same book may also exist in the DB under a different folder
    // 1- Look for duplicates using URL and site
    if (null == existingDuplicate
        && content.url.trim().isNotEmpty()
        && content.site != Site.NONE
    ) {
        existingDuplicate = findDuplicateContentByUrl(content, dao)
        // Ignore the duplicate if it is queued; we do prefer to import a full book
        if (existingDuplicate != null) {
            if (isInQueue(existingDuplicate.status)) existingDuplicate = null
            else duplicateOrigin = "book"
        }
    }

    // 2- Look for duplicates using physical properties (last resort)
    if (null == existingDuplicate) {
        existingDuplicate = findDuplicateContentByQtyPageAndSize(content, dao)
        // Ignore the duplicate if it is queued; we do prefer to import a full book
        if (existingDuplicate != null) {
            if (isInQueue(existingDuplicate.status)) existingDuplicate = null
            else duplicateOrigin = "book"
        }
    }

    if (existingDuplicate != null && !existingDuplicate.isFlaggedForDeletion) {
        trace(
            Log.INFO,
            1,
            log,
            "Import book KO! ($duplicateOrigin already in collection) : %s",
            content.storageUri
        )
        return true
    }
    return false
}

fun jsonToContent(
    context: Context,
    dao: CollectionDAO,
    jsons: List<DocumentFile>,
    archiveName: String
): Content? {
    val nameNoExt = getFileNameWithoutExtension(archiveName)
    // Use new suffixed naming; default on old naming
    val jsonFile = getFileWithName(jsons, nameNoExt + JSON_ARCHIVE_SUFFIX)
        ?: getFileWithName(jsons, nameNoExt) ?: return null
    try {
        val content = jsonToObject(context, jsonFile, JsonContent::class.java)
        if (content != null) {
            val result = content.toEntity(dao)
            result.jsonUri = jsonFile.uri.toString()
            return result
        }
    } catch (e: IOException) {
        Timber.w(e)
    } catch (e: JsonDataException) {
        Timber.w(e)
    }
    return null
}

/**
 * Build a [NameFilter] only accepting Content json files
 *
 * @return [NameFilter] only accepting Content json files
 */
fun getContentJsonNamesFilter(): NameFilter {
    return hentoidContentJson
}

fun patternToRegex(pattern: String): Triple<Pattern, Boolean, Boolean> {
    val hasTitle = pattern.contains("%t")
    val hasArtist = pattern.contains("%a")

    var regexp =
        pattern.replace("\\", "\\\\")
            .replace("[", "\\[").replace("]", "\\]")
            .replace("(", "\\(").replace(")", "\\)")
            .replace("{", "\\{").replace("}", "\\}")
            .replace("?", "\\?").replace("*", "\\*")
            .replace("+", "\\+")
            .replace(".", "\\.").replace("|", "\\|")
            .replace("$", "\\$").replace("^", "\\^")

    // Turn patterns into capturing groups
    val commonWords = "\\w':/`’\\|&\\-_!? %"
    if (hasTitle) regexp = regexp.replace("%t", "(?<title>[$commonWords]+)")
    if (hasArtist) regexp = regexp.replace("%a", "(?<artist>[$commonWords]+)")

    // Turn free patterns into non-capturing groups
    for (i in 0 until 9) {
        if (regexp.contains("%$i")) regexp = regexp.replace("%$i", "(?:[$commonWords]+)")
    }

    Timber.v("regexp : $regexp")

    // Compile regex with unicode support
    return Triple(regexp.toPattern(Pattern.UNICODE_CASE), hasTitle, hasArtist)
}

fun parseBookmarks(input: InputStream): List<SiteBookmark> {
    val result: MutableList<SiteBookmark> = ArrayList()
    val streams = duplicateInputStream(input, 2)
    InputStreamReader(streams[0]).use { isr ->
        var index = 0
        isr.forEachLine { line ->
            if (0 == index) {
                // Bookmark export
                if (line.startsWith("<!DOCTYPE NETSCAPE-Bookmark")) {
                    val doc = Jsoup.parse(streams[1], null, "")
                    result.addAll(
                        doc.select("A")
                            .map {
                                val url = it.attr("href").trim().lowercase()
                                SiteBookmark(
                                    url = url,
                                    title = it.text(),
                                    site = Site.searchByUrl(url) ?: Site.NONE
                                )
                            }
                            .filter { it.url.startsWith("http") }
                            .filter { it.site != Site.NONE }
                            .filter { it.site.isVisible }
                    )
                    return@forEachLine
                }
            }
            // Regular file
            val l = line.trim().lowercase()
            if (l.isNotBlank()) {
                var site = Site.NONE
                if (l.startsWith("http")) {
                    site = Site.searchByUrl(l) ?: Site.NONE
                }
                if (site != Site.NONE && site.isVisible) result.add(
                    SiteBookmark(url = l, title = "", site = site)
                )
            }
            index++
        }
    }
    return result
}
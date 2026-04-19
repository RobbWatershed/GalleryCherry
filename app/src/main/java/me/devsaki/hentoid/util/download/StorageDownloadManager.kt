package me.devsaki.hentoid.util.download

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.DOWNLOAD_CACHE_FOLDER
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.exception.ArchiveException
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.file.ArchiveStreamer
import me.devsaki.hentoid.util.file.InnerNameNumberArchiveComparator
import me.devsaki.hentoid.util.file.MIME_TYPE_CBZ
import me.devsaki.hentoid.util.file.PdfManager
import me.devsaki.hentoid.util.file.copyFile
import me.devsaki.hentoid.util.file.createFile
import me.devsaki.hentoid.util.file.findFolder
import me.devsaki.hentoid.util.file.getArchiveEntries
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.file.removeDocument
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.file.tryCleanDirectory
import me.devsaki.hentoid.util.formatFolderName
import me.devsaki.hentoid.util.getArchivePdfThumbFileName
import me.devsaki.hentoid.util.getContainingFolder
import me.devsaki.hentoid.util.getOrCreateContentDownloadDir
import me.devsaki.hentoid.util.getOrCreateSiteDownloadDir
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.util.persistJson
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class StorageDownloadManager {
    private var downloadMode: DownloadMode? = null

    /**
     * Parent folder of the target downloads' location
     *   DownloadMode.DOWNLOAD or STREAM : book folder inside site folder
     *   DownloadMode.DOWNLOAD_ARCHIVE, DownloadMode.DOWNLOAD_ARCHIVE_FILE : site folder
     */
    private var downloadFolder: DocumentFile? = null

    /**
     * Target archive for downloads
     *   DownloadMode.DOWNLOAD or STREAM : null
     *   DownloadMode.DOWNLOAD_ARCHIVE, DownloadMode.DOWNLOAD_ARCHIVE_FILE : archive file
     */
    private var downloadArchive: Uri? = null

    private var archiveStreamer: ArchiveStreamer? = null

    private var moveAppendedFiles: Boolean = true

    private val localMatch: MutableMap<String, String> = ConcurrentHashMap()


    /**
     * Create download folder or archive in the primary library
     */
    suspend fun createDownloadLocation(context: Context, content: Content): Boolean =
        withContext(Dispatchers.IO) {
            Timber.d("Storage download manager : Init ${content.downloadMode} (download)")
            moveAppendedFiles = true // Download mode

            downloadMode = content.downloadMode
            val locationResult =
                getDownloadLocation(getInstance(), content) ?: return@withContext false

            // Location is known already (e.g. resume download or redownload)
            locationResult.first?.let {
                content.setStorageDoc(it)
                if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE || downloadMode == DownloadMode.DOWNLOAD_ARCHIVE_FILE) {
                    downloadArchive = it.uri
                    // Compute parent folder of the archive
                    downloadFolder = content.getContainingFolder(context)?.let { parent ->
                        getDocumentFromTreeUri(context, parent)
                    }
                    if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE)
                        archiveStreamer = ArchiveStreamer(context, it.uri, true)
                } else {
                    downloadFolder = it
                }

                return@withContext true
            }

            // Location has to be computed
            val location = locationResult.second
            downloadFolder =
                if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE || downloadMode == DownloadMode.DOWNLOAD_ARCHIVE_FILE) {
                    getOrCreateSiteDownloadDir(context, location, content.site)
                } else {
                    getOrCreateContentDownloadDir(context, content, location)
                }

            downloadFolder?.let { dlFolder ->
                if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
                    val archiveName = formatFolderName(content).first + ".cbz"
                    createFile(context, dlFolder.uri, archiveName, MIME_TYPE_CBZ).let { uri ->
                        getDocumentFromTreeUri(context, uri)?.let { content.setStorageDoc(it) }
                        downloadArchive = uri
                        archiveStreamer = ArchiveStreamer(context, uri, false)
                    }
                } else {
                    content.setStorageDoc(dlFolder)
                }
            } ?: throw IOException("Couldn't create download folder")

            return@withContext true
        }

    /**
     * Create folder or archive for a merge / split operation
     */
    suspend fun createNewLocation(
        context: Context,
        containingFolder: Uri,
        targetContent: Content
    ): Boolean = withContext(Dispatchers.IO) {
        Timber.d("Storage download manager : Init ${targetContent.downloadMode} (new)")
        moveAppendedFiles = false // Copy mode

        downloadMode = targetContent.downloadMode
        val parentFolder = getDocumentFromTreeUri(context, containingFolder)
            ?: throw IOException("Couldn't find parent folder")

        val dlFolder = if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) parentFolder
        else {
            if (targetContent.status == StatusContent.EXTERNAL) {
                val bookFolderName = formatFolderName(targetContent)
                // First try finding the folder with new naming...
                var targetFolder = findFolder(context, parentFolder, bookFolderName.first)
                if (null == targetFolder) { // ...then with old (sanitized) naming...
                    targetFolder = findFolder(context, parentFolder, bookFolderName.second)
                    if (null == targetFolder) { // ...if not, create a new folder with the new naming...
                        targetFolder = parentFolder.createDirectory(bookFolderName.first)
                        if (null == targetFolder) { // ...if it fails, create a new folder with the old naming
                            targetFolder = parentFolder.createDirectory(bookFolderName.second)
                        }
                    }
                }
                targetFolder
            } else {
                // Primary folder for non-external content; using download strategy
                val location = selectDownloadLocation(context)
                getOrCreateContentDownloadDir(context, targetContent, location, true)
            }
        }

        if (null == dlFolder || !dlFolder.exists())
            throw ContentNotProcessedException(targetContent, "Could not create target directory")

        downloadFolder = dlFolder
        if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
            val archiveName = formatFolderName(targetContent).first + ".cbz"
            createFile(context, containingFolder, archiveName, MIME_TYPE_CBZ).let { uri ->
                getDocumentFromTreeUri(context, uri)?.let { targetContent.setStorageDoc(it) }
                downloadArchive = uri
                archiveStreamer = ArchiveStreamer(context, uri, false)
            }
        } else {
            targetContent.setStorageDoc(dlFolder)
        }

        return@withContext true
    }

    /**
     * Identify download folder
     */
    suspend fun getDownloadFolder(context: Context): Uri = withContext(Dispatchers.IO) {
        return@withContext if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
            Uri.fromFile(
                getOrCreateCacheFolder(context, DOWNLOAD_CACHE_FOLDER)
                    ?: throw IOException("Couldn't initialize cache folder $DOWNLOAD_CACHE_FOLDER")
            )
        } else {
            downloadFolder?.uri ?: throw IllegalArgumentException("Download folder not set")
        }
    }

    fun getTargetLocation(): Uri? {
        return when (downloadMode) {
            DownloadMode.DOWNLOAD_ARCHIVE, DownloadMode.DOWNLOAD_ARCHIVE_FILE -> downloadArchive
            else -> downloadFolder?.uri
        }
    }

    /**
     * Process downloaded file
     */
    fun appendFile(
        context: Context,
        isCoverThumb: Boolean,
        uri: Uri,
        targetName: String = ""
    ) {
        if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
            if (isCoverThumb) {
                // Copy thumb to thumb location
                val coverUri = copyFile(
                    context,
                    uri,
                    context.filesDir,
                    getArchivePdfThumbFileName(downloadArchive!!)
                )
                localMatch[uri.toString()] = coverUri.toString()
            } else archiveStreamer?.addFile(context, uri)
        } else {
            downloadFolder?.let { dlFolder ->
                // Check if the downloaded file is indeed inside the download folder...
                val dlFolderPath = dlFolder.uri.path ?: return
                val filePath = uri.path ?: return

                if (filePath.startsWith(dlFolderPath, true)) return

                var targetNameFinal = targetName
                if (targetNameFinal.isBlank()) targetNameFinal = uri.lastPathSegment ?: ""
                Timber.i("Moving file to the target download folder as $targetNameFinal")
                // ...if it's not (e.g. Ugoira assembled inside temp folder), move it
                val finalUri = copyFile(
                    context,
                    uri,
                    dlFolder,
                    targetNameFinal,
                    forceCreate = true
                ) ?: throw IOException("Couldn't copy result file")
                localMatch[uri.toString()] = finalUri.toString()

                if (moveAppendedFiles) removeFile(context, uri)
            }
        }
    }

    /**
     * Manually trigger image location refresh when downloading an archive
     * NB : Optional; is done automatically when calling completeDownload
     *
     * @return true if at least one value has been updated; false if nothing changed
     */
    fun refreshLocation(imageList: Collection<ImageFile>): Map<Long, String> {
        return refreshLocation(imageList, archiveStreamer)
    }

    /**
     * Process post-download actions
     */
    @Throws(ArchiveException::class)
    suspend fun completeDownload(context: Context, content: Content) =
        withContext(Dispatchers.IO) {
            if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
                // Wait until archive streaming has completed (poll every 500ms)
                while (archiveStreamer?.queueActive ?: false) pause(500)

                archiveStreamer?.let { streamer ->
                    // Throws exception if archiving has failed
                    if (streamer.queueFailed)
                        throw ArchiveException(streamer.queueFailMessage)

                    var imgList = content.imageList
                    val newLocations = refreshLocation(imgList, streamer)
                    if (newLocations.isNotEmpty()) {
                        imgList = imgList.map { img ->
                            newLocations[img.id]?.let { img.fileUri = it }
                            img
                        }
                        content.setImageFiles(imgList)
                    }
                }
            }

            if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE_FILE) {
                content.imageList.firstOrNull()?.let { archive ->
                    var uri = archive.fileUri.toUri()
                    val uriParts = UriParts(uri)
                    getDocumentFromTreeUri(context, uri)?.let { doc ->
                        if (doc.renameTo(formatFolderName(content).first + "." + uriParts.extension)) {
                            uri = doc.uri
                            content.setStorageDoc(doc)
                        } else {
                            throw IOException("Couldn't rename archive")
                        }
                    }

                    val entries = if (uriParts.extension.equals("pdf", true)) {
                        PdfManager().getEntries(context, uri)
                    } else // Archive
                        context.getArchiveEntries(uri)

                    val imgs = entries
                        .filter { !it.isFolder && isSupportedImage(it.path) }
                        .sortedWith(InnerNameNumberArchiveComparator())
                        .mapIndexed { i, e ->
                            ImageFile(
                                dbOrder = i,
                                dbFileUri = e.path,
                                dbUrl = uri.toString() + File.separator + e.path,
                                size = e.size,
                                status = StatusContent.DOWNLOADED
                            )
                        }
                    imgs.forEach { it.computeName(imgs.size) }
                    content.setImageFiles(imgs)
                    content.qtyPages = imgs.size
                }
            }
            content.qtyPages = content.imageList.count { it.isReadable }

            // Create JSON
            persistJson(context, content)

            // Empty cache
            getOrCreateCacheFolder(context, DOWNLOAD_CACHE_FOLDER)?.let {
                if (!tryCleanDirectory(it)) Timber.d("Failed to clean download cache")
            }

            clear()
        }

    suspend fun removeDownload(context: Context) = withContext(Dispatchers.IO) {
        archiveStreamer?.close()
        getTargetLocation()?.let { removeDocument(context, it) }
    }


    /**
     * Refresh image location according to what's been archived
     *
     * @return true if at least one value has been updated; false if nothing changed
     */
    private fun refreshLocation(
        imageList: Collection<ImageFile>,
        streamer: ArchiveStreamer?
    ): Map<Long, String> {
        val result = HashMap<Long, String>()
        imageList.forEach { img ->
            val fileUri = img.fileUri
            localMatch[fileUri]?.let {
                if (fileUri != it) result[img.id] = it
            }
            streamer?.mappedUris[fileUri]?.let {
                if (fileUri != it) result[img.id] = it
            }
        }
        return result
    }

    fun clear() {
        Timber.d("Storage download manager : Clearing")
        downloadMode = null
        archiveStreamer?.close()
        archiveStreamer = null
        downloadFolder = null
        localMatch.clear()
    }
}
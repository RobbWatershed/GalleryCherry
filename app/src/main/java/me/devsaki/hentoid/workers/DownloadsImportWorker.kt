package me.devsaki.hentoid.workers

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.isInQueue
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.CloudflareHelper
import me.devsaki.hentoid.util.network.CloudflareHelper.CloudflareProtectedException
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.parseBookmarks
import me.devsaki.hentoid.util.parseFromScratch
import me.devsaki.hentoid.workers.data.DownloadsImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException

/**
 * Service responsible for importing downloads
 */
class DownloadsImportWorker(
    context: Context,
    parameters: WorkerParameters
) : BaseWorker(context, parameters, R.id.downloads_import_service, "downloads-import") {
    // Variable used during the import process
    private var totalItems = 0
    private var cfHelper: CloudflareHelper? = null
    private var nbOK = 0
    private var nbKO = 0

    fun isRunning(context: Context): Boolean {
        return isRunning(context, R.id.downloads_import_service)
    }

    override fun getStartNotification(): BaseNotification {
        return ImportStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        cfHelper?.clear()
    }

    override suspend fun getToWork(input: Data) {
        val data = DownloadsImportData.Parser(inputData)
        if (data.fileUri.isEmpty()) return

        startImport(
            applicationContext,
            data.fileUri,
            QueuePosition.entries.first { it.value == data.queuePosition },
            data.importAsStreamed
        )
    }

    /**
     * Import books from external folder
     */
    private suspend fun startImport(
        context: Context,
        fileUri: String,
        queuePosition: QueuePosition,
        importAsStreamed: Boolean
    ) = withContext(Dispatchers.IO) {
        val file = getFileFromSingleUriString(context, fileUri)
        if (null == file) {
            trace(Log.ERROR, "Couldn't find downloads file at %s", fileUri)
            return@withContext
        }
        val downloads = getInputStream(context, file).use {
            val urls = parseBookmarks(it)
            urls.map { it.url }
        }
        if (downloads.isEmpty()) {
            trace(Log.ERROR, "Downloads file %s is empty", fileUri)
            return@withContext
        }
        totalItems = downloads.size
        val dao = ObjectBoxDAO()
        try {
            for (s in downloads) {
                var galleryUrl = s
                if (isNumeric(galleryUrl)) galleryUrl = Content.getGalleryUrlFromId(
                    Site.NHENTAI,
                    galleryUrl
                ) // We assume any launch code is Nhentai's
                importGallery(galleryUrl, queuePosition, importAsStreamed, false, dao)
            }
        } finally {
            dao.cleanup()
        }
        if (Settings.isQueueAutostart) resumeQueue(applicationContext)
        notifyProcessEnd()
    }

    private suspend fun importGallery(
        url: String,
        queuePosition: QueuePosition,
        importAsStreamed: Boolean,
        hasPassedCf: Boolean,
        dao: CollectionDAO
    ) {
        val site = Site.searchByUrl(url)
        if (null == site || Site.NONE == site) {
            trace(Log.WARN, "ERROR : Unsupported source @ %s", url)
            nextKO()
            return
        }
        val existingContent =
            dao.selectContentByUrlOrCover(site, Content.transformRawUrl(site, url), null)
        if (existingContent != null) {
            val location =
                if (isInQueue(existingContent.status)) "queue" else "library"
            trace(Log.INFO, "ERROR : Content already in %s @ %s", location, url)
            nextKO()
            return
        }
        try {
            val content = parseFromScratch(url)
            if (null == content) {
                trace(Log.WARN, "ERROR : Unreachable content @ %s", url)
                nextKO()
            } else {
                trace(Log.INFO, "Added content @ %s", url)
                content.downloadMode =
                    if (importAsStreamed) DownloadMode.STREAM else DownloadMode.DOWNLOAD
                dao.addContentToQueue(
                    content,
                    null,
                    null,
                    queuePosition,
                    -1,
                    null,
                    isQueueActive(applicationContext)
                )
                nextOK()
            }
        } catch (e: IOException) {
            trace(Log.WARN, "ERROR : While loading content @ %s", url)
            nextKO(e)
        } catch (_: CloudflareProtectedException) {
            if (hasPassedCf) {
                trace(Log.WARN, "Cloudflare bypass ineffective for content @ %s", url)
                nextKO()
                return
            }
            trace(Log.INFO, "Trying to bypass Cloudflare for content @ %s", url)
            if (null == cfHelper) cfHelper = CloudflareHelper()
            if (cfHelper!!.tryPassCloudflare(site, null)) {
                importGallery(url, queuePosition, importAsStreamed, true, dao)
            } else {
                trace(Log.WARN, "Cloudflare bypass failed for content @ %s", url)
                nextKO()
            }
        }
    }

    private fun nextOK() {
        nbOK++
        launchProgressNotification()
    }

    private fun nextKO(e: Throwable? = null) {
        nbKO++
        if (e != null) Timber.w(e)
        launchProgressNotification()
    }

    override fun runProgressNotification() {
        notificationManager.notify(
            ImportProgressNotification(
                applicationContext.resources.getString(R.string.importing_downloads),
                nbOK + nbKO,
                totalItems
            )
        )
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                R.id.import_downloads,
                0,
                nbOK,
                nbKO,
                totalItems
            )
        )
    }

    private fun notifyProcessEnd() {
        notificationManager.notify(ImportCompleteNotification(nbOK, nbKO))
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.import_downloads,
                0,
                nbOK,
                nbKO,
                totalItems
            )
        )
    }
}
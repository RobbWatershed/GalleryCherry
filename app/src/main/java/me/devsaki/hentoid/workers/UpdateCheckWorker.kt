package me.devsaki.hentoid.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.AppRepoInfoEvent
import me.devsaki.hentoid.json.core.UpdateInfo
import me.devsaki.hentoid.notification.appUpdate.UpdateAvailableNotification
import me.devsaki.hentoid.notification.appUpdate.UpdateCheckNotification
import me.devsaki.hentoid.retrofit.UpdateServer
import me.devsaki.hentoid.util.notification.BaseNotification
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.update_check_service, null) {

    override fun getStartNotification(): BaseNotification {
        return UpdateCheckNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        // Nothing
    }

    override fun runProgressNotification() {
        // Nothing
    }

    @SuppressLint("TimberArgCount")
    override suspend fun getToWork(input: Data) {
        try {
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.Type.BROADCAST,
                    CommunicationEvent.Recipient.SETTINGS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_checking)
                )
            )
            withContext(Dispatchers.IO) {
                UpdateServer.api.updateInfo.execute().body()
            }?.let {
                onSuccess(it)
            } ?: run {
                EventBus.getDefault().post(
                    CommunicationEvent(
                        CommunicationEvent.Type.BROADCAST,
                        CommunicationEvent.Recipient.SETTINGS,
                        applicationContext.resources.getString(R.string.pref_check_updates_manual_no_connection)
                    )
                )
                notificationManager.cancel()
                Timber.w("Failed to get update info (null result)")
            }
        } catch (e: Exception) {
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.Type.BROADCAST,
                    CommunicationEvent.Recipient.SETTINGS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_no_connection)
                )
            )
            Timber.w(e, "Failed to get update info")
            notificationManager.cancel()
        }
    }

    private fun onSuccess(updateInfoJson: UpdateInfo) {
        var newVersion = false
        if (BuildConfig.VERSION_CODE < updateInfoJson.getVersionCode(BuildConfig.DEBUG)) {
            val updateUrl: String = updateInfoJson.getUpdateUrl(BuildConfig.DEBUG)
            notificationManager.notifyLast(UpdateAvailableNotification(updateUrl))
            newVersion = true
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.Type.BROADCAST,
                    CommunicationEvent.Recipient.SETTINGS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_new)
                )
            )
        } else {
            notificationManager.cancel()
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.Type.BROADCAST,
                    CommunicationEvent.Recipient.SETTINGS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_no_new)
                )
            )
        }

        // Get the alerts relevant to current version code
        val sourceAlerts = updateInfoJson.getSourceAlerts(BuildConfig.DEBUG)
            .filter { it.getFixedByBuild() > BuildConfig.VERSION_CODE }

        val apkUrl = updateInfoJson.getUpdateUrl(BuildConfig.DEBUG)

        // Send update info through the bus to whom it may concern
        EventBus.getDefault().postSticky(AppRepoInfoEvent(newVersion, apkUrl, sourceAlerts))
    }
}
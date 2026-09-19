package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import me.devsaki.hentoid.R

/**
 * Broadcast receiver for the stop button on the app update download notification
 * IMPORTANT : Every Received must be registered on the manifest!
 */
class UpdateNotificationStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WorkManager.getInstance(context).cancelUniqueWork(R.id.update_download_service.toString())
    }
}
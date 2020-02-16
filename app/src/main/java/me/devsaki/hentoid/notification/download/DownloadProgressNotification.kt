package me.devsaki.hentoid.notification.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.notification.Notification
import java.util.*

class DownloadProgressNotification(
        private val title: String,
        private val progress: Int,
        private val max: Int
) : Notification {

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_cherry_icon)
                .setContentTitle(context.getString(R.string.downloading))
                .setContentText(title)
                .setContentInfo(progressString)
                .setProgress(max, progress, false)
                .setColor(ThemeHelper.getColor(context, R.color.secondary_light))
                .setContentIntent(getDefaultIntent(context))
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
    }

    private fun getDefaultIntent(context: Context): PendingIntent {
        val resultIntent = Intent(context, QueueActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        return PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

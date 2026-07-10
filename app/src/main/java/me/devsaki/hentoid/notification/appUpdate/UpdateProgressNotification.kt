package me.devsaki.hentoid.notification.appUpdate

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.UpdateNotificationStopReceiver
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import java.util.Locale

private const val INDETERMINATE = -1

class UpdateProgressNotification(var progress: Int = INDETERMINATE) : BaseNotification() {

    lateinit var builder: NotificationCompat.Builder

    override fun onCreateNotification(context: Context): android.app.Notification {
        val progressString = "$progress%"

        if (!this::builder.isInitialized) {
            builder = NotificationCompat.Builder(context, ID)
                .setSmallIcon(R.drawable.ic_app)
                .setColor(context.getThemedColor(R.color.secondary_light))
                .addAction(
                    R.drawable.ic_cancel,
                    context.getString(R.string.stop),
                    getStopIntent(context)
                )
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
        }
        builder.apply {
            setContentTitle(context.getString(R.string.downloading_update) + " : " + progressString)
            setContentText(progressString)
            setContentInfo(progressString)
            setProgress(100, progress, progress == INDETERMINATE)
        }
        return builder.build()
    }

    private fun getStopIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, UpdateNotificationStopReceiver::class.java)
    }
}

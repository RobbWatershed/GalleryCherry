package me.devsaki.hentoid.notification.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.InstallRunReceiver
import me.devsaki.hentoid.receiver.KEY_APK_PATH
import me.devsaki.hentoid.util.notification.Notification

private val APK_MIMETYPE = MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk")

class UpdateInstallNotification(private val apkUri: Uri) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val pendingIntent = getIntent(context)

        return NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_cherry_icon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVibrate(longArrayOf(1, 1, 1))
                .setContentTitle(context.resources.getText(R.string.update_ready))
                .setContentText(context.resources.getText(R.string.tap_to_install))
                .setContentIntent(pendingIntent)
                .build()
    }

    private fun getIntent(context: Context): PendingIntent {
        val intent: Intent
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent = Intent(context, InstallRunReceiver::class.java)
            intent.putExtra(KEY_APK_PATH, apkUri.path)
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        } else {
            intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, APK_MIMETYPE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            PendingIntent.getActivity(context, 0, intent, 0)
        }
    }
}

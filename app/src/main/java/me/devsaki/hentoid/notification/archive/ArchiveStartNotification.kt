package me.devsaki.hentoid.notification.archive

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class ArchiveStartNotification : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_cherry_icon)
            .setProgress(1, 1, true)
            .setContentTitle(context.resources.getString(R.string.archive_progress))
            .setContentText(context.resources.getString(R.string.archive_progress))
            .build()
}

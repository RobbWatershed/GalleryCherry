package me.devsaki.hentoid.notification.jsonUpdate

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class UpdateJsonStartNotification : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_cherry_icon)
            .setProgress(1, 1, true)
            .setContentTitle(context.resources.getString(R.string.notif_json_progress))
            .setContentText(context.resources.getString(R.string.notif_json_progress))
            .build()
}

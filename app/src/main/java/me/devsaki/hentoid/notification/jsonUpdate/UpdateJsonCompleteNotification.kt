package me.devsaki.hentoid.notification.jsonUpdate

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class UpdateJsonCompleteNotification :
    BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = R.string.notif_json_complete

        return NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_cherry_icon)
            .setContentTitle(context.getString(title))
            .build()
    }
}

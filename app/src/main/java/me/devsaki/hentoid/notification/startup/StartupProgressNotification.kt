package me.devsaki.hentoid.notification.startup

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import java.util.Locale

class StartupProgressNotification(
    private val message: String,
    private val progress: Int,
    private val max: Int
) : BaseNotification() {

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_cherry_icon)
            .setContentTitle(message)
            .setContentInfo(progressString)
            .setProgress(max, progress, (0 == max))
            .setColor(context.getThemedColor(R.color.secondary_light))
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}

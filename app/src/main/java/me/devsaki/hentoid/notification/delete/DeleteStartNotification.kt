package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.BaseDeleteWorker

class DeleteStartNotification(
    private val max: Int,
    private val operation: BaseDeleteWorker.Operation
) : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_cherry_icon)
            .setProgress(max, 0, 0 == max)
            .setContentTitle(
                context.getString(
                    when (operation) {
                        BaseDeleteWorker.Operation.PURGE -> R.string.purge_progress
                        BaseDeleteWorker.Operation.STREAM -> R.string.stream_progress
                        else -> R.string.delete_progress
                    }
                )
            )
            .setContentText("")
            .build()
}

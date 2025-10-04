package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.BaseDeleteWorker

class DeleteCompleteNotification(
    private val items: Int,
    private val nbError: Int,
    private val operation: BaseDeleteWorker.Operation
) :
    BaseNotification() {
    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = if (nbError > 0) {
            when (operation) {
                BaseDeleteWorker.Operation.STREAM -> R.string.notif_stream_fail
                BaseDeleteWorker.Operation.PURGE -> R.string.notif_delete_prepurge_fail
                else -> R.string.notif_delete_fail
            }
        } else {
            when (operation) {
                BaseDeleteWorker.Operation.STREAM -> R.string.notif_stream_complete
                BaseDeleteWorker.Operation.PURGE -> R.string.notif_delete_prepurge_complete
                else -> R.string.notif_delete_complete
            }
        }
        val content = if (nbError > 0) context.resources.getQuantityString(
            R.plurals.notif_delete_fail_details,
            nbError,
            nbError
        )
        else context.resources.getQuantityString(
            R.plurals.notif_process_complete_details_generic,
            items,
            items
        )

        return NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_cherry_icon)
            .setContentTitle(context.getString(title))
            .setContentText(content)
            .build()
    }
}

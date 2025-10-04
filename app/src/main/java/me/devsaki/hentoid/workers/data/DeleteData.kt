package me.devsaki.hentoid.workers.data

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.core.net.toUri
import androidx.work.Data
import me.devsaki.hentoid.util.LATIN_1
import me.devsaki.hentoid.util.fromByteArray
import me.devsaki.hentoid.util.toByteArray
import me.devsaki.hentoid.workers.BaseDeleteWorker

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DeleteWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_CONTENT_IDS = "contentIds"
private const val KEY_CONTENT_PURGE_KEEPCOVERS = "contentPurgeKeepCovers"
private const val KEY_GROUP_IDS = "groupIds"
private const val KEY_QUEUE_IDS = "queueIds"
private const val KEY_DOCS_ROOT = "docRoot"
private const val KEY_DOCS_NAMES = "docNames"
private const val KEY_DOC_URIS = "docUris"
private const val KEY_DELETE_FLAGGED_IMAGES = "deleteFlaggedImages"
private const val KEY_DELETE_ALL_QUEUE_RECORDS = "deleteAllQueueRecords"
private const val KEY_DELETE_GROUPS_ONLY = "deleteGroupsOnly"
private const val KEY_IS_CLEANING = "isCleaning"
private const val KEY_OPERATION = "operation"
private const val KEY_CONTENT_FILTER = "contentFilter"
private const val KEY_INVERT_FILTER_SCOPE = "invertFilterScope"
private const val KEY_KEEP_FAV_GROUPS = "keepFavGroups"

class DeleteData {
    class Builder {
        private val builder = Data.Builder()
        fun setContentIds(value: List<Long>) {
            builder.putLongArray(KEY_CONTENT_IDS, value.toLongArray())
        }

        fun setContentPurgeKeepCovers(value: Boolean) {
            builder.putBoolean(KEY_CONTENT_PURGE_KEEPCOVERS, value)
        }

        fun setGroupIds(value: List<Long>) {
            builder.putLongArray(KEY_GROUP_IDS, value.toLongArray())
        }

        fun setQueueIds(value: List<Long>) {
            builder.putLongArray(KEY_QUEUE_IDS, value.toLongArray())
        }

        fun setDeleteFlaggedImages(value: Boolean) {
            builder.putBoolean(KEY_DELETE_FLAGGED_IMAGES, value)
        }

        fun setDocsRootAndNames(root: Uri, names: Collection<String>) {
            builder.putString(KEY_DOCS_ROOT, root.toString())
            // Convert strings to Latin-1 charset to reduce memory footprint (10k limit for Data)
            val allNames = TextUtils.join("?", names)
            builder.putByteArray(KEY_DOCS_NAMES, allNames.toByteArray(LATIN_1))
        }

        fun setDocUris(value: Collection<String>) {
            builder.putStringArray(KEY_DOC_URIS, value.toTypedArray())
        }

        fun setDeleteAllQueueRecords(value: Boolean) {
            builder.putBoolean(KEY_DELETE_ALL_QUEUE_RECORDS, value)
        }

        fun setDeleteGroupsOnly(value: Boolean) {
            builder.putBoolean(KEY_DELETE_GROUPS_ONLY, value)
        }

        fun setOperation(value: BaseDeleteWorker.Operation) {
            builder.putInt(KEY_OPERATION, value.ordinal)
        }

        fun setContentFilter(value: Bundle) {
            val data = value.toByteArray()
            builder.putByteArray(KEY_CONTENT_FILTER, data)
        }

        fun setInvertFilterScope(value: Boolean) {
            builder.putBoolean(KEY_INVERT_FILTER_SCOPE, value)
        }

        fun setKeepFavGroups(value: Boolean) {
            builder.putBoolean(KEY_KEEP_FAV_GROUPS, value)
        }

        fun setIsCleaning(value: Boolean) {
            builder.putBoolean(KEY_IS_CLEANING, value)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val operation: BaseDeleteWorker.Operation?
            get() = BaseDeleteWorker.Operation.entries.firstOrNull {
                it.ordinal == data.getInt(KEY_OPERATION, -1)
            }
        val contentIds: LongArray
            get() = data.getLongArray(KEY_CONTENT_IDS) ?: longArrayOf()
        val contentPurgeKeepCovers: Boolean
            get() = data.getBoolean(KEY_CONTENT_PURGE_KEEPCOVERS, false)
        val groupIds: LongArray
            get() = data.getLongArray(KEY_GROUP_IDS) ?: longArrayOf()
        val queueIds: LongArray
            get() = data.getLongArray(KEY_QUEUE_IDS) ?: longArrayOf()
        val isDeleteFlaggedImages: Boolean
            get() = data.getBoolean(KEY_DELETE_FLAGGED_IMAGES, false)
        val docUris: List<Uri>
            get() = data.getStringArray(KEY_DOC_URIS)?.map { it.toUri() } ?: emptyList()
        val docsRoot: Uri
            get() = data.getString(KEY_DOCS_ROOT)?.toUri() ?: Uri.EMPTY
        val docsNames: Set<String>
            get() {
                val bytes = data.getByteArray(KEY_DOCS_NAMES)
                if (null == bytes) return emptySet()
                return String(bytes, LATIN_1).split("?").toSet()
            }
        val isDeleteAllQueueRecords: Boolean
            get() = data.getBoolean(KEY_DELETE_ALL_QUEUE_RECORDS, false)
        val isDeleteGroupsOnly: Boolean
            get() = data.getBoolean(KEY_DELETE_GROUPS_ONLY, false)
        val isCleaning: Boolean
            get() = data.getBoolean(KEY_IS_CLEANING, false)
        val contentFilter: Bundle
            get() = data.getByteArray(KEY_CONTENT_FILTER)
                ?.let { Bundle().fromByteArray(it) }
                ?: Bundle()
        val isInvertFilterScope: Boolean
            get() = data.getBoolean(KEY_INVERT_FILTER_SCOPE, false)
        val isKeepFavGroups: Boolean
            get() = data.getBoolean(KEY_KEEP_FAV_GROUPS, false)
    }
}
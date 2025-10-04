package me.devsaki.hentoid.workers.data

import androidx.work.Data
import me.devsaki.hentoid.workers.SplitMergeType

private const val KEY_OPERATION = "operation"
private const val KEY_CONTENT_IDS = "contentIds"
private const val KEY_CHAPTER_IDS = "chapterIds"
private const val KEY_DELETE_AFTER_OPS = "deletAfter"
private const val KEY_NEW_TITLE = "newTitle"
private const val KEY_USE_BOOKS_AS_CHAPTERS = "useBooksAsChapters"
private const val KEY_CHAPTER_IDS_FOR_SPLIT = "chapterIdsForSplit"
private const val KEY_KEEP_FIRST_BOOK_CHAPS = "keepFirstBookChaps"

class SplitMergeData {
    class Builder {
        private val builder = Data.Builder()

        fun setOperation(value: SplitMergeType) {
            builder.putInt(KEY_OPERATION, value.ordinal)
        }

        fun setContentIds(value: List<Long>) {
            builder.putLongArray(KEY_CONTENT_IDS, value.toLongArray())
        }

        fun setChapterIds(value: List<Long>) {
            builder.putLongArray(KEY_CHAPTER_IDS, value.toLongArray())
        }

        fun setChapterIdsForSplit(value: List<Long>) {
            builder.putLongArray(KEY_CHAPTER_IDS_FOR_SPLIT, value.toLongArray())
        }

        fun setDeleteAfterOps(value: Boolean) {
            builder.putBoolean(KEY_DELETE_AFTER_OPS, value)
        }

        fun setNewTitle(value: String) {
            builder.putString(KEY_NEW_TITLE, value)
        }

        fun setUseBooksAsChapters(value: Boolean) {
            builder.putBoolean(KEY_USE_BOOKS_AS_CHAPTERS, value)
        }

        fun setKeepFirstBookChaps(value: Boolean) {
            builder.putBoolean(KEY_KEEP_FIRST_BOOK_CHAPS, value)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val operation: Int
            get() = data.getInt(KEY_OPERATION, 0)
        val contentIds: LongArray
            get() = data.getLongArray(KEY_CONTENT_IDS) ?: longArrayOf()
        val chapterIds: LongArray
            get() = data.getLongArray(KEY_CHAPTER_IDS) ?: longArrayOf()
        val chapterIdsForSplit: LongArray
            get() = data.getLongArray(KEY_CHAPTER_IDS_FOR_SPLIT) ?: longArrayOf()
        val deleteAfterOps: Boolean
            get() = data.getBoolean(KEY_DELETE_AFTER_OPS, false)
        val newTitle: String
            get() = data.getString(KEY_NEW_TITLE) ?: ""
        val useBooksAsChapters: Boolean
            get() = data.getBoolean(KEY_USE_BOOKS_AS_CHAPTERS, false)
        val keepFirstBookChaps: Boolean
            get() = data.getBoolean(KEY_KEEP_FIRST_BOOK_CHAPS, false)
    }
}
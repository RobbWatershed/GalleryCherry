package me.devsaki.hentoid.widget

import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.THUMBS_CACHE
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.StorageCache
import me.devsaki.hentoid.util.getPictureThumbCached
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.isSupportedArchivePdf
import me.devsaki.hentoid.util.string
import kotlin.math.roundToInt

class LrrSearchManager {
    private val values = LrrSearchBundle()


    fun toBundle(): Bundle {
        val result = Bundle()
        saveToBundle(result)
        return result
    }

    fun saveToBundle(b: Bundle) {
        b.putAll(values.bundle)
    }

    fun loadFromBundle(b: Bundle) {
        values.bundle.putAll(b)
    }

    fun setQuery(value: String) {
        values.query = value
    }

    fun setSortField(value: Int) {
        values.sortField = value
    }

    fun setSortDesc(value: Boolean) {
        values.sortDesc = value
    }

    fun clearFilters() {
        setQuery("")
    }

    fun clear() {
        clearFilters()
    }

    /*
    suspend fun getArchiveDetails(archives: List<Content>): Flow<Content> = withContext(Dispatchers.IO) {
            val flowFiles =
                archives.map {
                    // Count contents to see if we have a folder book
                    val imgChildren = if (it.isDirectory) {
                        theExplorer.listFiles(context, it, imageNamesFilter)
                    } else emptyList()
                    // TODO get number of images inside archives and PDFs
                    // Extract archive and PDF covers using private storage (same as bona library books)
                    val fileName = it.name ?: ""
                    val archiveCover =
                        if (isSupportedArchivePdf(fileName)) {
                            getPictureThumbCached(
                                context, it.uri,
                                context.resources.getDimension(R.dimen.thumb_max_dim)
                                    .roundToInt(),
                                null,
                                StorageCache.createFinder(THUMBS_CACHE),
                                StorageCache.createCreator(THUMBS_CACHE)
                            ) ?: Uri.EMPTY
                        } else Uri.EMPTY
                    val coverUri = imgChildren.firstOrNull()?.uri ?: archiveCover
                    val res = DisplayFile(it, imgChildren.size > 1, root)
                    res.coverUri = coverUri
                    res.nbChildren = imgChildren.size
                    res
                }
                    .flowOn(Dispatchers.IO)

            return@withContext merge(flowUp, flowFiles)
        }

     */

    class LrrSearchBundle(val bundle: Bundle = Bundle()) {

        var query by bundle.string(default = "")

        var sortField by bundle.int(default = Settings.lrrSortField)

        var sortDesc by bundle.boolean(default = Settings.isLrrSortDesc)

        fun isFilterActive(): Boolean {
            return query.isNotEmpty()
        }
    }
}
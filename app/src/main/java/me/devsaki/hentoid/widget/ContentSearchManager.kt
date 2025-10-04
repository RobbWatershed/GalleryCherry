package me.devsaki.hentoid.widget

import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.parseSearchUri
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.SearchCriteria
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.intArray
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.string

class ContentSearchManager() {

    private val values = ContentSearchBundle()


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

    fun setFilterBookFavourites(value: Boolean) {
        values.filterBookFavourites = value
    }

    fun setFilterBookNonFavourites(value: Boolean) {
        values.filterBookNonFavourites = value
    }

    fun setFilterBookCompleted(value: Boolean) {
        values.filterBookCompleted = value
    }

    fun setFilterBookNotCompleted(value: Boolean) {
        values.filterBookNotCompleted = value
    }

    fun setFilterRating(value: Int) {
        values.filterRating = value
    }

    fun setFilterPageFavourites(value: Boolean) {
        values.filterPageFavourites = value
    }

    fun setLoadAll(value: Boolean) {
        values.loadAll = value
    }

    fun setQuery(value: String) {
        values.query = value
    }

    fun setContentSortField(value: Int) {
        values.sortField = value
    }

    fun setContentSortDesc(value: Boolean) {
        values.sortDesc = value
    }

    fun setLocation(value: Int) {
        values.location = value
    }

    fun setContentType(value: Int) {
        values.contentType = value
    }

    fun setGroup(value: Group?) {
        if (value != null) values.groupId = value.id else values.groupId = -1
    }

    fun setTags(tags: Set<Attribute>) {
        values.attributes = SearchActivityBundle.buildSearchUri(tags).toString()
    }

    fun setExcludedAttrs(types: Set<AttributeType>) {
        values.excludedAttributeTypes = types.map { it.code }.toIntArray()
    }

    fun clearTags() {
        setTags(emptySet())
    }

    fun clearExcludedAttrs() {
        setExcludedAttrs(emptySet())
    }

    fun clearFilters() {
        clearTags()
        clearExcludedAttrs()
        setQuery("")
        setFilterBookFavourites(false)
        setFilterBookNonFavourites(false)
        setFilterBookCompleted(false)
        setFilterBookNotCompleted(false)
        setFilterPageFavourites(false)
        setFilterRating(-1)
        setLocation(0)
        setContentType(0)
    }

    fun getLibrary(dao: CollectionDAO): LiveData<PagedList<Content>> {
        val tags = parseSearchUri(values.attributes.toUri()).attributes
        return when {
            // Universal search
            values.query.isNotEmpty() -> dao.searchBooksUniversal(values)
            // Advanced search
            tags.isNotEmpty() || values.excludedAttributeTypes?.isNotEmpty() ?: false || values.location > 0 || values.contentType > 0 ->
                dao.searchBooks(values, tags)
            // Default search (display recent)
            else -> dao.selectRecentBooks(values)
        }
    }

    fun searchContentIds(dao: CollectionDAO): List<Long> {
        return searchContentIds(values, dao)
    }

    companion object {
        fun searchContentIds(data: ContentSearchBundle, dao: CollectionDAO): List<Long> {
            val tags = parseSearchUri(data.attributes.toUri()).attributes
            return when {
                // Universal search
                data.query.isNotEmpty() -> dao.searchBookIdsUniversal(data)
                // Advanced search
                tags.isNotEmpty() || data.excludedAttributeTypes?.isNotEmpty() ?: false || data.location > 0 || data.contentType > 0 ->
                    dao.searchBookIds(data, tags)
                // Default search (display recent)
                else -> dao.selectRecentBookIds(data)
            }
        }
    }


    // INNER CLASS

    class ContentSearchBundle(val bundle: Bundle = Bundle()) {

        var loadAll by bundle.boolean(default = false)

        var filterPageFavourites by bundle.boolean(default = false)

        var filterBookFavourites by bundle.boolean(default = false)

        var filterBookNonFavourites by bundle.boolean(default = false)

        var filterBookCompleted by bundle.boolean(default = false)

        var filterBookNotCompleted by bundle.boolean(default = false)

        var filterRating by bundle.int(default = -1)

        var query: String by bundle.string(default = "")

        var sortField by bundle.int(default = Settings.contentSortField)

        var sortDesc by bundle.boolean(default = Settings.isContentSortDesc)

        var attributes: String by bundle.string(default = "") // Stored using a search URI for convenience

        var excludedAttributeTypes by bundle.intArray()

        var location by bundle.int(default = 0)

        var contentType by bundle.int(default = 0)

        var groupId by bundle.long(default = -1)


        fun isFilterActive(): Boolean {
            val tags = parseSearchUri(attributes.toUri()).attributes
            return query.isNotEmpty()
                    || tags.isNotEmpty()
                    || location > 0
                    || contentType > 0
                    || excludedAttributeTypes?.isNotEmpty() ?: false
                    || filterBookFavourites
                    || filterBookNonFavourites
                    || filterBookCompleted
                    || filterBookNotCompleted
                    || filterRating > -1
                    || filterPageFavourites
        }

        companion object {
            fun fromSearchCriteria(data: SearchCriteria): ContentSearchBundle {
                val result = ContentSearchBundle()

                result.apply {
                    groupId = -1 // Not applicable
                    attributes = SearchActivityBundle.buildSearchUri(data).toString()
                    excludedAttributeTypes =
                        data.excludedAttributeTypes.map { it.code }.toIntArray()
                    location = data.location.value
                    contentType = data.contentType.value
                    query = data.query
                }

                return result
            }
        }
    }
}
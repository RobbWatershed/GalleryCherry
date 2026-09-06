package me.devsaki.hentoid.widget

import android.os.Bundle
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Value.ORDER_FIELD_READ_DATE
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string

class LrrSearchManager {
    private val values = LrrSearchBundle()
    private var resumeFromIndex = 0


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

    fun setFilterBooksFavourite(value: Boolean) {
        values.filterBookFavourites = value
    }

    fun setCategory(value: String) {
        values.categoryId = value
    }

    fun setSortField(value: Int) {
        values.sortField = value
    }

    fun setSortDesc(value: Boolean) {
        values.sortDesc = value
    }

    fun setResumeFrom(value: Int) {
        resumeFromIndex = value
    }

    fun clearFilters() {
        setQuery("")
        setCategory("")
    }

    fun clear() {
        clearFilters()
    }

    fun populateSearchQuery(q: HashMap<String, String>) {
        if (values.query.isNotBlank()) q["filter"] = values.query
        if (values.categoryId.isNotBlank()) q["category"] = values.categoryId
        q["sortby"] = when (values.sortField) {
            ORDER_FIELD_READ_DATE -> "lastread"
            else -> "title"
        }
        q["order"] = if (values.sortDesc) "desc" else "asc"
        if (resumeFromIndex > -1) q["start"] = resumeFromIndex.toString()
    }

    class LrrSearchBundle(val bundle: Bundle = Bundle()) {

        var query by bundle.string(default = "")

        var categoryId by bundle.string(default = "")

        var sortField by bundle.int(default = Settings.lrrSortField)

        var sortDesc by bundle.boolean(default = Settings.isLrrSortDesc)

        var filterBookFavourites by bundle.boolean(default = false)

        fun isFilterActive(): Boolean {
            return query.isNotEmpty()
                    || categoryId.isNotEmpty()
        }
    }
}
package me.devsaki.hentoid.json.sources.lrr

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LrrCategories(
    val categories: List<LrrCategory>
) {
    @JsonClass(generateAdapter = true)
    data class LrrCategory(
        val id: String,
        val name: String,
        val archives : List<String>
    )
}
package me.devsaki.hentoid.json.sources.lrr

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LrrExtraction(
    val pages: List<String>
)
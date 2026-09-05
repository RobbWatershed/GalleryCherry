package me.devsaki.hentoid.json.sources.lrr

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LrrServerInfo(
    val version: String?,
    @Json(name = "archives_per_page")
    val archivesPerPage : Int?
)
package me.devsaki.hentoid.json.sources.pawchive

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PawAttachment(
    //val server: String?,
    val name: String?,
    val path: String?
)
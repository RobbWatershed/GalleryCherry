package me.devsaki.hentoid.json.sources.lrr

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LrrSuccess(
    val success: Int
) {
    val isSuccess = (1 == success)
}
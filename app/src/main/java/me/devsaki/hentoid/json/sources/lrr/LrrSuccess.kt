package me.devsaki.hentoid.json.sources.lrr

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LrrSuccess(
    val success: Int
) {
    val isSuccess = (1 == success)
}

@JsonClass(generateAdapter = true)
data class LrrCategorySuccess(
    val success: Int,
    @Json(name = "category_id")
    val catId : String?
) {
    val isSuccess = (1 == success)
}
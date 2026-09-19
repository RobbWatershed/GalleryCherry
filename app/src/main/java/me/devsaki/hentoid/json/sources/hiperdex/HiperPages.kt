package me.devsaki.hentoid.json.sources.hiperdex

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiperPages(
    val result: HiperPageResult
) {
    @JsonClass(generateAdapter = true)
    data class HiperPageResult(
        val data: HiperPageData
    )

    @JsonClass(generateAdapter = true)
    data class HiperPageData(
        val json: List<HiperPageJson>
    )

    @JsonClass(generateAdapter = true)
    data class HiperPageJson(
        val id: Long,
        val chapterId: Long,
        val pageOrder: Int,
        val webpUrl: String?,
        val avifUrl: String?
    )

    fun toPages(): List<String> {
        val imgList = result.data.json
        if (imgList.isEmpty()) return emptyList()

        return imgList.sortedBy { it.pageOrder }.map {
            it.webpUrl ?: it.avifUrl ?: ""
        }
    }
}
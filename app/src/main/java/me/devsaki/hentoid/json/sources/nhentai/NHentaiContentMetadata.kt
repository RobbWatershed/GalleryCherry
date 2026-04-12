package me.devsaki.hentoid.json.sources.nhentai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.activities.sources.NhentaiActivity.Companion.DOMAIN_FILTER
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.fixUrl

@JsonClass(generateAdapter = true)
data class NHentaiContentMetadata(
    val id: Int,
    @Json(name = "media_id")
    val mediaId: String,
    val title: NHentaiTitle,
    val cover: NHentaiThumbnail?,
    val thumbnail: NHentaiThumbnail?,
    @Json(name = "upload_date")
    val uploadDate: Long,
    val tags: List<NHentaiTag>?,
    @Json(name = "num_pages")
    val numPages: Int,
    val pages: List<NHentaiPage>
) {
    @JsonClass(generateAdapter = true)
    data class NHentaiTitle(
        val english: String?,
        val japanese: String?,
        val pretty: String?
    )

    @JsonClass(generateAdapter = true)
    data class NHentaiThumbnail(
        val path: String
    )

    @JsonClass(generateAdapter = true)
    data class NHentaiTag(
        val type: String,
        val name: String,
        val url: String
    )

    @JsonClass(generateAdapter = true)
    data class NHentaiPage(
        val number: Int,
        val path: String
    )

    fun update(content: Content, updateImages: Boolean) {
        content.url = "https://$DOMAIN_FILTER/g/$id"
        content.title = cleanup(title.pretty ?: title.english ?: "")
        content.status = StatusContent.SAVED
        content.uploadDate = uploadDate

        val attributes = AttributeMap()
        tags?.forEach {
            attributes.add(
                Attribute(
                    valToType(it.type),
                    it.name,
                    fixUrl(it.url, Site.NHENTAI.url),
                    Site.NHENTAI
                )
            )
        }
        content.putAttributes(attributes)



        content.coverImageUrl = cover?.let { covr ->
            fixUrl(covr.path, "https://t1.nhentai.net")
        } ?: thumbnail?.let { covr ->
            fixUrl(covr.path, "https://t1.nhentai.net")
        } ?: ""

        if (updateImages) {
            val imageUrls = pages
                .sortedBy { it.number }
                .map { fixUrl(it.path, "https://i2.nhentai.net") }
            content.qtyPages = numPages
            content.setImageFiles(
                urlsToImageFiles(
                    imageUrls,
                    content.downloadRange,
                    StatusContent.SAVED,
                    content.coverImageUrl
                )
            )
        }
    }

    private fun valToType(value: String): AttributeType {
        return when (value) {
            "language" -> AttributeType.LANGUAGE
            "category" -> AttributeType.CATEGORY
            "artist" -> AttributeType.ARTIST
            "circle" -> AttributeType.CIRCLE
            "group" -> AttributeType.CIRCLE
            "character" -> AttributeType.CHARACTER
            "parody" -> AttributeType.SERIE
            else -> AttributeType.TAG
        }
    }

    companion object {
        fun updateFromData(
            data: String,
            content: Content,
            updateImages: Boolean
        ): Boolean {
            content.site = Site.NHENTAI
            content.status = StatusContent.IGNORED
            jsonToObject(data, NHentaiContentMetadata::class.java)?.update(content, updateImages)
            return (StatusContent.SAVED == content.status)
        }
    }
}
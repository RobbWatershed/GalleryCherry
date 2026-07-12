package me.devsaki.hentoid.json.sources.hiperdex

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.activities.sources.HiperdexActivity.Companion.DOMAIN_FILTER
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.chapterStr
import me.devsaki.hentoid.util.parseDatetimeToEpoch

@JsonClass(generateAdapter = true)
data class HiperContent(
    val result: HiperContentResult
) {
    @JsonClass(generateAdapter = true)
    data class HiperContentResult(
        val data: HiperContentData
    )

    @JsonClass(generateAdapter = true)
    data class HiperContentData(
        val json: HiperContentJson
    )

    @JsonClass(generateAdapter = true)
    data class HiperContentJson(
        val id: Long,
        val slug: String,
        val title: String,
        val coverUrl: String,
        val type: String,
        val status: String, // completed / ongoing
        val updatedAt: String, // e.g. 2026-07-11T18:03:26.000Z
        val genres: List<String>,
        val authors: List<String>,
        val artists: List<String>
    )

    val contentId: Long
        get() = result.data.json.id

    fun update(content: Content, chapterId: String, updateImages: Boolean): Content {
        val hcj = result.data.json
        content.site = Site.HIPERDEX
        content.title = hcj.title
        if (chapterId.isNotEmpty()) content.title += " $chapterStr $chapterId"
        content.coverImageUrl = hcj.coverUrl
        var url = "https://$DOMAIN_FILTER/${hcj.type}/${hcj.slug}"
        if (chapterId.isNotEmpty()) url += "/$chapterId"
        content.setRawUrl(url)
        content.uniqueSiteId = hcj.id.toString()
        if (chapterId.isNotEmpty()) content.uniqueSiteId += ".$chapterId"

        val attributes = AttributeMap()

        attributes.add(
            Attribute(
                AttributeType.CATEGORY,
                hcj.type,
                "https://$DOMAIN_FILTER/search?type=${hcj.type}",
                Site.HIPERDEX
            )
        )

        hcj.genres.forEach {
            attributes.add(
                Attribute(
                    AttributeType.TAG,
                    it,
                    "https://$DOMAIN_FILTER/search?genre=$it",
                    Site.HIPERDEX
                )
            )
        }

        hcj.artists.forEach {
            attributes.add(
                Attribute(
                    AttributeType.ARTIST,
                    it,
                    "https://$DOMAIN_FILTER/search?artist=$it",
                    Site.HIPERDEX
                )
            )
        }

        content.putAttributes(attributes)

        content.completed = hcj.status.equals("completed", true)

        if (hcj.updatedAt.isNotEmpty())
            content.uploadDate = parseDatetimeToEpoch(hcj.updatedAt, "yyyy-MM-dd'T'HH:mm:ss.SSSX")

        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }

        return content
    }
}
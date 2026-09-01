package me.devsaki.hentoid.json.sources.lrr

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Settings

@JsonClass(generateAdapter = true)
data class LrrArchives(
    val data: List<LrrArchive>,
    val recordsFiltered: Int,
    val recordsTotal: Int
) {
    val contentList: List<Content>
        get() = data.map { it.toContent() }

    @JsonClass(generateAdapter = true)
    data class LrrArchive(
        val arcid: String,
        val title: String,
        val pagecount: Int,
        val tags: String,
    ) {
        fun toContent(): Content {
            val result = Content(
                site = Site.NONE,
                uniqueSiteId = arcid,
                title = title,
                qtyPages = pagecount,
                coverImageUrl = thumbUrl
            )
            // TODO tags

            return result
        }

        val thumbUrl
            get() = "${Settings.lrrEndpoint}/api/archives/$arcid/thumbnail"
    }
}
package me.devsaki.hentoid.json.sources.lrr

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
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
        val progress: Int,
        val tags: String
    ) {
        fun toContent(): Content {
            val result = Content(
                site = Site.LRR,
                status = StatusContent.ONLINE,
                uniqueSiteId = arcid,
                title = title,
                qtyPages = pagecount,
                coverImageUrl = thumbUrl,
                downloadMode = DownloadMode.STREAM,
                lastReadPageIndex = progress,
                archiveId = arcid
            )
            val tagList = tags.split(',').map { it.trim() }
            result.addAttributes(tagList.map { toAttr(it) })

            return result
        }

        private fun toAttr(attrName: String): Attribute {
            var name = attrName
            var attrType = AttributeType.TAG

            val sepIdx = attrName.indexOf(':')
            if (sepIdx > -1) {
                val typeStr = attrName.substring(0, sepIdx).trim()
                name = attrName.substring(sepIdx + 1).trim()
                attrType = when (typeStr) {
                    "artist" -> AttributeType.ARTIST
                    "parody", "series" -> AttributeType.SERIE
                    "group", "circle" -> AttributeType.CIRCLE
                    "language" -> AttributeType.LANGUAGE
                    "character" -> AttributeType.CHARACTER
                    else -> AttributeType.TAG
                }
            }
            return Attribute(type = attrType, name = name)
        }

        val thumbUrl
            get() = "${Settings.lrrEndpoint}/api/archives/$arcid/thumbnail"
    }
}
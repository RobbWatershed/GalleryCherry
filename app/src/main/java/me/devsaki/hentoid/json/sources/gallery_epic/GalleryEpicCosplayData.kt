package me.devsaki.hentoid.json.sources.gallery_epic

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.content.NO_TITLE
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.LIST_STRINGS
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.parseDatetimeToEpoch

const val GE_IMG_SERVER = "https://static.galleryepic.xyz/image/"

@JsonClass(generateAdapter = true)
data class GalleryEpicCosplayData(
    val title: String?,
    val cover: String,
    val createdAt: String?,
    val images: String,
    val charactersAndCosers: List<CharactersAndCosers>
) {
    val imageList: List<String>
        get() = if (images.contains('['))
            jsonToObject<List<String>>(images, LIST_STRINGS)?.map { GE_IMG_SERVER + it }
                ?: emptyList() else emptyList()

    fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.GALLERYEPIC
        content.url = url.replace(Site.GALLERYEPIC.url, "")
        content.title = title ?: charactersAndCosers.firstOrNull()?.title ?: NO_TITLE
        content.coverImageUrl = GE_IMG_SERVER + cover
        content.status = StatusContent.SAVED
        content.uploadDate = 0L
        createdAt?.let {
            // e.g. 2023-10-09T18:58:09.308Z
            if (it.isNotEmpty())
                content.uploadDate = parseDatetimeToEpoch(it, "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        }

        val attributes = AttributeMap()
        charactersAndCosers.forEach { cac ->
            cac.characterName?.let {
                attributes.add(
                    Attribute(AttributeType.CHARACTER, it, "", Site.GALLERYEPIC)
                )
            }
            cac.coserName?.let {
                attributes.add(
                    Attribute(AttributeType.MODEL, it, "", Site.GALLERYEPIC)
                )
            }
            cac.parodyName?.let {
                attributes.add(
                    Attribute(AttributeType.SERIE, it, "", Site.GALLERYEPIC)
                )
            }
        }
        content.putAttributes(attributes)

        if (updateImages) {
            val imgs = if (imageList.isNotEmpty()) urlsToImageFiles(
                imageList,
                content.downloadRange,
                StatusContent.SAVED,
                Site.GALLERYEPIC,
                content.coverImageUrl
            ) else emptyList()
            content.setImageFiles(imgs)
            content.qtyPages = imgs.count { it.isReadable }
        }

        return content
    }
}
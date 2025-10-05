package me.devsaki.hentoid.json.sources.luscious

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent


@JsonClass(generateAdapter = true)
data class LusciousGalleryMetadata(
    val data: PictureData
) {
    @JsonClass(generateAdapter = true)
    data class PictureData(
        val picture: PictureInfoContainer
    )

    @JsonClass(generateAdapter = true)
    data class PictureInfoContainer(
        val list: PictureInfo
    )

    @JsonClass(generateAdapter = true)
    data class PictureInfo(
        val info: PictureContainerMetadata,
        val items: List<PictureMetadata>
    )

    @JsonClass(generateAdapter = true)
    data class PictureContainerMetadata(
        @Json(name = "total_pages")
        var totalPages: Int = 0
    )

    @JsonClass(generateAdapter = true)
    data class PictureMetadata(
        @Json(name = "url_to_original")
        val urlToOriginal: String,
        val thumbnails: List<PictureThumbnail>
    ) {
        val bestPic: String
            get() {
                val thumb = thumbnails.maxByOrNull { it.width }?.url ?: ""
                return thumb.ifEmpty { urlToOriginal }
            }
    }

    @JsonClass(generateAdapter = true)
    data class PictureThumbnail(
        val width: Int,
        val height: Int,
        val url: String
    )

    fun getNbPages(): Int {
        return data.picture.list.info.totalPages
    }

    fun toImageFileList(offset: Int = 0): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        var order = offset
        val imageList: List<PictureMetadata> = data.picture.list.items
        imageList.forEach {
            result.add(
                ImageFile.fromImageUrl(
                    ++order,
                    it.bestPic,
                    StatusContent.SAVED,
                    imageList.size
                )
            )
        }
        return result
    }
}

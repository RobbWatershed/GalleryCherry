package me.devsaki.hentoid.json.sources.luscious

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.rangeToNumbers


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
        var totalPages: Int = 0,
        @Json(name = "items_per_page")
        var perPage: Int = 0,
        @Json(name = "total_items")
        var total: Int = 0
    )

    @JsonClass(generateAdapter = true)
    data class PictureMetadata(
        @Json(name = "url_to_original")
        val urlToOriginal: String?,
        @Json(name = "url_to_video")
        val urlToVideo: String?,
        val thumbnails: List<PictureThumbnail>
    ) {
        val biggestThumb: String
            get() = thumbnails.maxByOrNull { it.width }?.url ?: ""
        val original: String
            get() = urlToOriginal ?: ""
        val video: String
            get() = urlToVideo ?: ""
        val bestUrl: String
            get() {
                var result = original
                if (result.isEmpty()) result = video
                if (result.isEmpty()) result = biggestThumb
                return result
            }
        val bestBackupUrl: String
            get() {
                var result = video
                if (result.isEmpty()) result = biggestThumb
                return result
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

    fun toImageFileList(range: String, offset: Int = 1): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        var order = offset
        val imageList: List<PictureMetadata> = data.picture.list.items

        val rangeIndexes =
            if (range.isBlank()) imageList.indices
            else rangeToNumbers(range)
                .filter { it >= offset && it < offset + imageList.count() }
                .map { it - offset }

        rangeIndexes.forEach {
            val img = ImageFile.fromImageUrl(
                order++,
                imageList[it].bestUrl,
                StatusContent.SAVED,
                imageList.size
            )
            img.backupUrl = imageList[it].bestBackupUrl
            result.add(img)
        }
        return result
    }
}

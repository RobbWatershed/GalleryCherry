package me.devsaki.hentoid.json.sources.hiperdex

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.util.chapterStr
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class HiperChapters(
    val result: HiperChapterResult
) {
    @JsonClass(generateAdapter = true)
    data class HiperChapterResult(
        val data: HiperChapterData
    )

    @JsonClass(generateAdapter = true)
    data class HiperChapterData(
        val json: List<HiperChapterJson>
    )

    @JsonClass(generateAdapter = true)
    data class HiperChapterJson(
        val id: Long,
        val seriesId: Long,
        val number: Float, // e.g. 100.5
        val title: String?,
        val pagesCount: Int,
        val dirHash: String?
    )

    val chaptersPages: Map<Int, Int>
        get() = result.data.json.groupingBy { (it.number * 10).roundToInt() }
            .aggregate { key, accumulator, element, first -> element.pagesCount }

    fun toChapters(galleryUrl: String): List<Chapter> {
        val chapList = result.data.json
        if (chapList.isEmpty()) return emptyList()

        return chapList.sortedBy { it.number }.map {
            val ch = Chapter(
                (it.number * 10).roundToInt(),
                "$galleryUrl/${it.number}",
                it.title ?: "$chapterStr ${it.number}",
                it.id.toString()
            )
            ch.nbPages = it.pagesCount
            ch
        }
    }
}
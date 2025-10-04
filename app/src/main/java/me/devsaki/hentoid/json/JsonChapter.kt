package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Chapter

@JsonClass(generateAdapter = true)
data class JsonChapter(
    val order: Int,
    val url: String,
    val name: String,
    val uniqueId: String,
    val uploadDate: Long?
) {
    constructor(c: Chapter) : this(c.order, c.url, c.name, c.uniqueId, c.uploadDate)

    fun toEntity(): Chapter {
        return Chapter(
            order = order,
            url = url,
            name = name,
            uniqueId = uniqueId,
            uploadDate = uploadDate ?: 0
        )
    }
}
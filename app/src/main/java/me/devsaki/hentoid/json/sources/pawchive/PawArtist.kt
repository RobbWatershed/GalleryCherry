package me.devsaki.hentoid.json.sources.pawchive

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.activities.sources.PAW_DOMAIN_FILTER
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site

@JsonClass(generateAdapter = true)
data class PawArtist(
    val id: String,
    val name: String,
    val service: String
) {
    fun toAttribute(): Attribute {
        return Attribute(
            AttributeType.ARTIST,
            name,
            "https://$PAW_DOMAIN_FILTER/$service/user/$id",
            Site.PAWCHIVE
        )
    }

    val iconUrl
        get() = "https://$PAW_DOMAIN_FILTER/icons/$service/$id"
}
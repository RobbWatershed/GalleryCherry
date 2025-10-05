package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class FapalityContent : BaseContentParser() {

    @Selector("h1")
    private var titles: MutableList<Element>? = null

    @Selector(value = ".tags_list a")
    private var tags: MutableList<Element>? = null

    @Selector(value = "img[itemprop]", attr = "src")
    private var thumbs: MutableList<String>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.FAPALITY
        val photosIndex = url.indexOf("/photos/")
        content.url = url.substring(photosIndex + 8)

        var title = ""
        titles?.let {
            if (!it.isEmpty()) {
                title = it[0].text()
                val titleEnd = title.lastIndexOf(" - ")
                if (titleEnd > -1) title = title.substring(0, title.lastIndexOf(" - "))
            }
        }
        content.title = title

        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.TAG, tags, true, Site.FAPALITY)
        content.addAttributes(attributes)

        thumbs?.let {
            if (updateImages) {
                if (!it.isEmpty()) content.coverImageUrl = it[0]
                content.qtyPages = it.size
            }
        }

        return content
    }
}
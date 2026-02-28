package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class V2PhContent : BaseContentParser() {

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private lateinit var thumb: String

    @Selector(value = "a[href*='/actor/']")
    private var models: MutableList<Element>? = null

    @Selector(value = "a[href*='/category/']")
    private var tags: MutableList<Element>? = null

    @Selector(value = ".album-photo img")
    private var imgs: MutableList<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.V2PH

        content.url = url.replace(Site.V2PH.url, "")
        content.title = title
        content.coverImageUrl = thumb

        val attributes = AttributeMap()

        parseAttributes(attributes, AttributeType.MODEL, models, false, Site.V2PH)
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.V2PH)

        content.addAttributes(attributes)

        if (thumb.isBlank()) {
            imgs?.let {
                val pics = it.map { i -> getImgSrc(i) }
                if (pics.isNotEmpty()) content.coverImageUrl = pics[0]
            }
        }

        if (updateImages) {
            content.qtyPages = 0
            content.setImageFiles(emptyList())
        }

        return content
    }
}
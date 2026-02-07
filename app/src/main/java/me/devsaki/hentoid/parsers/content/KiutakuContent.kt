package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class KiutakuContent : BaseContentParser() {

    @Selector("head title")
    private var title: String? = null

    @Selector(value = "a.is-medium[href*='/girl/']")
    private var models: MutableList<Element>? = null

    @Selector(value = "a.is-medium[href*='/tag/']")
    private var tags: MutableList<Element>? = null

    @Selector(value = ".article-fulltext img")
    private var imgs: MutableList<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.KIUTAKU

        content.url = url.replace(Site.KIUTAKU.url, "")
        content.title = title ?: NO_TITLE

        val attributes = AttributeMap()

        parseAttributes(attributes, AttributeType.MODEL, models, false, Site.KIUTAKU)
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.KIUTAKU)

        content.addAttributes(attributes)

        imgs?.let {
            val pics = it.map { i -> getImgSrc(i) }
            if (pics.isNotEmpty()) content.coverImageUrl = pics[0]
        }

        if (updateImages) {
            content.qtyPages = 0
            content.setImageFiles(emptyList())
        }

        return content
    }
}
package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class XinmeiContent : BaseContentParser() {

    @Selector("head title")
    private var title: String? = null

    @Selector(value = ".container .figure a img")
    private var imgs: MutableList<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.XINMEI

        content.url = url.replace(Site.XINMEI.url, "")
        content.title = title ?: NO_TITLE

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
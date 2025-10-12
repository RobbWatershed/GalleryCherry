package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class BestGirlSexyContent : BaseContentParser() {

    @Selector("head title")
    private var title: String? = null

    @Selector(value = "a[href*='/tag/']", attr = "href")
    private var models: MutableList<Element>? = null

    @Selector(value = "div[data-widget_type='theme-post-content.default'] img")
    private var images: MutableList<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.BESTGIRLSEXY

        content.url = url
        content.title = title ?: NO_TITLE

        val attributes = AttributeMap()

        parseAttributes(attributes, AttributeType.MODEL, models, true, Site.BESTGIRLSEXY)
        content.addAttributes(attributes)

        if (updateImages) {
            val images = images?.let { elts ->
                val imageLinks = elts.map { getImgSrc(it) }.distinct()
                if (imageLinks.isNotEmpty())
                    urlsToImageFiles(imageLinks, imageLinks[0], StatusContent.SAVED)
                else emptyList()
            } ?: emptyList()

            if (!images.isEmpty()) content.coverImageUrl = images[0].url
            content.qtyPages = images.size - 1
            content.setImageFiles(images)
        }

        return content
    }
}
package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.util.parseDateToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class FoamGirlContent : BaseContentParser() {

    @Selector(
        value = "head meta[name=\"description\"]",
        attr = "content",
        defValue = ""
    )
    private lateinit var title: String

    @Selector(value = ".image-info-time")
    private var publishDate: Element? = null

    @Selector(value = "a[rel='category tag']")
    private var tags: MutableList<Element>? = null

    @Selector(value = "a[href*='/tag/']")
    private var model: MutableList<Element>? = null

    @Selector(value = "#image_div img")
    private var imgs1: MutableList<Element>? = null

    @Selector(value = "#image_div_all img")
    private var imgs2: MutableList<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.FOAMGIRL

        content.url = url.replace(Site.FOAMGIRL.url, "")
        content.title = title

        publishDate?.let {
            val date = it.ownText() // e.g. 2025.11.9
            if (date.isNotEmpty()) content.uploadDate =
                parseDateToEpoch(date, "uuuu.M.d")
        }

        val attributes = AttributeMap()

        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.FOAMGIRL)
        parseAttributes(attributes, AttributeType.MODEL, model, false, Site.FOAMGIRL)

        content.addAttributes(attributes)

        val imgs = if (imgs1?.isEmpty() ?: true) imgs2 else imgs1
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
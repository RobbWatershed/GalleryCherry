package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class GirlsTopContent : BaseContentParser() {
    @Selector("head title")
    private var title: String? = null

    @Selector(value = "a[href*='/models.php?name']")
    private var models: MutableList<Element>? = null

    @Selector(value = ".taglist a[href*='/tags.php']")
    private var tags: MutableList<Element>? = null

    @Selector(value = ".psto_item .full", attr = "href")
    private var imageLinks: MutableList<String>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.GIRLSTOP

        content.url = url
        content.title = title ?: NO_TITLE

        if (null == imageLinks || imageLinks!!.isEmpty()) {
            return Content(status = StatusContent.IGNORED)
        }

        val attributes = AttributeMap()

        if (models != null) {
            parseAttributes(attributes, AttributeType.MODEL, models, false, Site.GIRLSTOP)
        }

        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.GIRLSTOP)
        content.addAttributes(attributes)

        if (updateImages) {
            val images: MutableList<ImageFile> = ArrayList()

            var order = 1
            for (s in imageLinks) {
                images.add(
                    ImageFile.fromImageUrl(
                        order++,
                        s,
                        StatusContent.SAVED,
                        imageLinks!!.size
                    )
                )
            }
            if (images.isNotEmpty()) content.coverImageUrl = images[0].url
            content.setImageFiles(images)
            content.qtyPages = images.size
        }

        return content
    }
}
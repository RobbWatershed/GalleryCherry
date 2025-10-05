package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

private const val GALLERY_FOLDER = "/galleries/"

class PornPicsContent : BaseContentParser() {
    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(".title-section h1")
    private var title: String? = null

    @Selector(value = ".tags a")
    private var tags: MutableList<Element>? = null

    @Selector(value = ".gallery-info__item a[href*='/?q']")
    private var models: MutableList<Element>? = null

    @Selector(value = ".gallery-info__item a[href*='/pornstars/']")
    private var models2: MutableList<Element>? = null

    @Selector(value = "#tiles .rel-link", attr = "href")
    private var imageLinks: MutableList<String>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.PORNPICS

        val theUrl = galleryUrl.ifEmpty { url }
        val galleryLocation = theUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length
        content.url = theUrl.substring(galleryLocation)
        content.title = title?:NO_TITLE

        if (null == imageLinks || imageLinks!!.isEmpty()) {
            return Content(status = StatusContent.IGNORED)
        }

        val attributes = AttributeMap()

        if (models != null && models!!.size > 1) {
            var first = true
            for (e in models) {
                if (e.childNodes().isEmpty()) continue
                if (e.childNode(0).childNodes().isEmpty()) continue
                if (first) {
                    first = false
                    continue
                }
                attributes.add(
                    Attribute(
                        AttributeType.MODEL,
                        e.childNode(0).childNode(0).toString(),
                        e.attr("href"),
                        Site.PORNPICS
                    )
                )
            }
        }

        if (models2 != null) {
            for (e in models2) {
                if (e.childNodes().isEmpty()) continue
                if (e.childNode(0).childNodes().isEmpty()) continue
                attributes.add(
                    Attribute(
                        AttributeType.MODEL,
                        e.childNode(0).childNode(0).toString(),
                        e.attr("href"),
                        Site.PORNPICS
                    )
                )
            }
        }

        parseAttributes(attributes, AttributeType.TAG, tags, true, Site.PORNPICS)
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
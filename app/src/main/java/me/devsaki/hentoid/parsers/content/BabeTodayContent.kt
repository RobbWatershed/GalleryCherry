package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.SmartContent.Companion.addLinksToImages
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class BabeTodayContent : BaseContentParser() {

    @Selector("head title")
    private var title: String? = null

    @Selector(value = "a[href^='/mobile/']", attr = "href")
    private var tags: MutableList<Element>? = null

    @Selector(value = "a[href*='.jpg']", attr = "href")
    private var imageLinksJpg: MutableList<String>? = null

    @Selector(value = "a[href*='.jpeg']", attr = "href")
    private var imageLinksJpeg: MutableList<String>? = null

    @Selector(value = "a[href*='.png']", attr = "href")
    private var imageLinksPng: MutableList<String>? = null

    private val imageLinks: MutableList<String> = ArrayList()

    // Remove duplicates in found images and stored them to an unified container
    private fun processImages() {
        imageLinksJpg?.let { l ->
            imageLinks.addAll(
                l.distinct().map { it.replace("//", "https://") }
            )
        }

        imageLinksJpeg?.let { l ->
            imageLinks.addAll(
                l.distinct().map { it.replace("//", "https://") }
            )
        }

        imageLinksPng?.let { l ->
            imageLinks.addAll(
                l.distinct().map { it.replace("//", "https://") }
            )
        }
    }

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.BABETODAY

        content.url = url
        content.title = title ?: NO_TITLE

        val attributes = AttributeMap()

        // Remove 1st tag (sponsor site)
        tags?.let {
            if (!it.isEmpty()) it.removeAt(0)
        }
        parseAttributes(attributes, AttributeType.TAG, tags, true, Site.BABETODAY)
        content.addAttributes(attributes)

        if (updateImages) {
            val images = ArrayList<ImageFile>()
            processImages()
            addLinksToImages(imageLinks, images, url)
            if (!images.isEmpty()) content.coverImageUrl = images[0].url

            content.qtyPages = images.size
            content.setImageFiles(images)
        }

        return content
    }
}
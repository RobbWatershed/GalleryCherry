package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber

open class SmartContent : BaseContentParser() {

    @Selector(":root")
    private var root: Element? = null

    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private lateinit var galleryUrl: String

    @Selector("head title")
    private var title: String? = null

    @Selector(value = "a[href*='.jpg']", attr = "href")
    private var imageLinksJpg: MutableList<String>? = null

    @Selector(value = "a[href*='.jpeg']", attr = "href")
    private var imageLinksJpeg: MutableList<String>? = null

    @Selector(value = "a[href*='.png']", attr = "href")
    private var imageLinksPng: MutableList<String>? = null

    // Alternate images are alone on the page, without links, zishy-style (else we would capture clickable thumbs)
    @Selector(value = ":not(a)>img[src*='.jpg']")
    private var imageEltsJpg: MutableList<Element>? = null

    @Selector(value = ":not(a)>img[src*='.jpeg']")
    private var imageEltsJpeg: MutableList<Element>? = null

    @Selector(value = ":not(a)>img[src*='.png']")
    private var imageEltsPng: MutableList<Element>? = null

    private val imageLinks: MutableList<String> = ArrayList()
    private val imageElts: MutableList<String> = ArrayList()

    private fun processImages() {
        imageLinksJpg?.let { imageLinks.addAll(it.distinct()) }
        imageLinksJpeg?.let { imageLinks.addAll(it.distinct()) }
        imageLinksPng?.let { imageLinks.addAll(it.distinct()) }

        imageEltsJpg?.let { e ->
            imageElts.addAll(
                e.map { it.attr("src") }.distinct()
            )
        }
        imageEltsJpeg?.let { e ->
            imageElts.addAll(
                e.map { it.attr("src") }.distinct()
            )
        }
        imageEltsPng?.let { e ->
            imageElts.addAll(
                e.map { it.attr("src") }.distinct()
            )
        }
    }

    private fun isGallery(): Boolean {
        return (imageLinks.size > 4 || imageElts.size > 4)
    }

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        processImages()

        // Temp but needed for the rest of the operations; will be overwritten
        content.site = Site.NONE

        var theUrl = galleryUrl.ifEmpty { url }

        Timber.i("galleryUrl : %s", theUrl)
        if (theUrl.startsWith("//")) theUrl = "http:$theUrl"
        if (!theUrl.isEmpty()) {
            val httpUrl = theUrl.toHttpUrl()
            content.url = httpUrl.scheme + "://" + httpUrl.host + httpUrl.encodedPath
        } else content.url = ""

        if (!isGallery()) return Content(status = StatusContent.IGNORED)

        content.title = title ?: NO_TITLE

        val attributes = AttributeMap()
        content.addAttributes(attributes)

        if (updateImages) {
            val images = ArrayList<ImageFile>()

            if (imageLinks.size > 4) addLinksToImages(imageLinks, images, url)
            else if (imageElts.size > 4) addLinksToImages(imageElts, images, url)
            if (images.isNotEmpty()) content.coverImageUrl = images[0].url

            content.qtyPages = images.size
            content.setImageFiles(images)
        }

        return content
    }

    companion object {
        fun addLinksToImages(
            links: MutableList<String>,
            images: MutableList<ImageFile>,
            url: String
        ) {
            var order = 1
            val urlHost = url.substring(0, url.indexOf("/", url.indexOf("://") + 3))
            val urlLocation = url.substring(0, url.lastIndexOf("/") + 1)

            for (s in links) {
                var s = s
                if (!s.startsWith("http")) {
                    s = if (s.startsWith("/")) urlHost + s
                    else urlLocation + s
                }
                images.add(ImageFile.fromImageUrl(order++, s, StatusContent.SAVED, links.size))
            }
        }
    }
}
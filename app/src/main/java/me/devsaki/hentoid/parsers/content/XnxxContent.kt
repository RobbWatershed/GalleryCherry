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

private const val PORNSTARS_MARKER = "Pornstars : "
private const val TAGS_MARKER = " - Tags : "

class XnxxContent : BaseContentParser() {

    @Selector(value = "head meta[name='twitter:url']", attr = "content", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(value = "head title")
    private var title: String? = null

    @Selector(value = ".descriptionGalleryPage")
    private var rawMetadata: String? = null

    @Selector(value = ".descriptionGalleryPage a")
    private var tags: MutableList<Element>? = null

    @Selector(value = ".downloadLink", attr = "href")
    private var imageLinks: MutableList<String>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.XNXX

        title?.let {
            val galleryIndex = it.lastIndexOf("gallery")
            if (galleryIndex > -1) content.title = it.substring(0, it.lastIndexOf("gallery") - 1)
            else content.title = it
        }

        val theUrl = galleryUrl.ifEmpty { url }
        content.url = theUrl.replace(Site.XNXX.url, "").replace("gallery/", "")

        val attributes = AttributeMap()


        // Models
        rawMetadata?.let { rm ->
            if (rm.contains(PORNSTARS_MARKER)) {
                val tagsPosition = rm.indexOf(TAGS_MARKER)
                val stars = if (tagsPosition > -1) rm.substring(0, tagsPosition)
                    .replace(PORNSTARS_MARKER, "")
                    .split(",")
                else rm.replace(PORNSTARS_MARKER, "").split(",")

                for (s in stars) {
                    attributes.add(
                        Attribute(
                            AttributeType.MODEL,
                            s.trim(),
                            "/" + s.trim(),
                            Site.XNXX
                        )
                    )
                }
            }
        }

        if (tags != null) {
            parseAttributes(attributes, AttributeType.TAG, tags, true, Site.XNXX)
        }
        content.addAttributes(attributes)

        if (updateImages) {
            val images: MutableList<ImageFile> = ArrayList()

            var order = 1
            imageLinks?.forEach { s ->
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
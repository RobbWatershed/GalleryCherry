package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.parseAttribute
import me.devsaki.hentoid.parsers.urlsToImageFiles
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import java.util.Locale

class CosplayTeleContent : BaseContentParser() {

    @Selector(value = "head meta[name='description']", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = ".article-inner strong")
    private var tags: MutableList<Element>? = null

    @Selector(value = ".gallery-icon a", attr = "href")
    private var imageLinks: MutableList<String>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.COSPLAYTELE

        content.url = url
        content.title = title

        val attributes = AttributeMap()
        tags?.forEach { tag ->
            if (0 == tag.childrenSize()) return@forEach
            val tagStr = tag.toString().lowercase(Locale.getDefault())
            val elt = tag.child(0)
            if (tagStr.contains("cosplayer")) {
                parseAttribute(
                    attributes,
                    AttributeType.MODEL,
                    elt,
                    true,
                    Site.COSPLAYTELE
                )
            } else if (tagStr.contains("appears in")) {
                parseAttribute(
                    attributes,
                    AttributeType.SERIE,
                    elt,
                    true,
                    Site.COSPLAYTELE
                )
            } else if (tagStr.contains("character")) {
                parseAttribute(
                    attributes,
                    AttributeType.CHARACTER,
                    elt,
                    true,
                    Site.COSPLAYTELE
                )
            }
        }
        content.addAttributes(attributes)

        imageLinks?.let { links ->
            if (updateImages && !links.isEmpty()) {
                val images =
                    urlsToImageFiles(
                        links,
                        links[0],
                        StatusContent.SAVED
                    )
                if (!images.isEmpty()) content.coverImageUrl = images[0].url
                content.setImageFiles(images)
                content.qtyPages = images.size - 1
            }
        }

        return content
    }
}
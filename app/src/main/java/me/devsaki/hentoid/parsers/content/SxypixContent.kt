package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import pl.droidsonroids.jspoon.annotation.Selector

class SxypixContent : BaseContentParser() {

    @Selector(value = "head meta[name='keywords']", attr = "content", defValue = "")
    private lateinit var title: String

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.SXYPIX
        content.url = url
        content.title = title

        val attributes = AttributeMap()

        val parts = title.split(" - ")
        val model = if (parts.size > 1) parts[1] else ""

        if (!model.isBlank()) {
            attributes.add(
                Attribute(
                    AttributeType.MODEL,
                    model,
                    model.replace(" ", "-"),
                    Site.SXYPIX
                )
            )
        }

        content.addAttributes(attributes)

        if (updateImages) {
            val images: MutableList<ImageFile> = ArrayList()
            content.setImageFiles(images)
            content.qtyPages = images.size
        }

        return content
    }
}
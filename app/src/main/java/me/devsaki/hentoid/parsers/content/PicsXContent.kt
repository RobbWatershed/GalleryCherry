package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.images.PicsXParser
import me.devsaki.hentoid.parsers.urlsToImageFiles
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class PicsXContent : BaseContentParser() {

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = ".image-container img")
    private var images: MutableList<Element>? = null

    @Selector(value = "#images-container script")
    private var imageData: MutableList<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.PICS_X
        content.url = url.replace(Site.PICS_X.url + "gallery", "")
        content.title = title

        if (updateImages) {
            val imageFiles: MutableList<ImageFile> = ArrayList()
            val imgUrls =
                PicsXParser.parseImages(images ?: mutableListOf(), imageData ?: mutableListOf())
            if (!imgUrls.isEmpty()) imageFiles.addAll(
                urlsToImageFiles(
                    imgUrls,
                    imgUrls[0],
                    StatusContent.SAVED
                )
            )

            content.setImageFiles(imageFiles)
            content.qtyPages = imageFiles.size - 1
        }

        return content
    }
}
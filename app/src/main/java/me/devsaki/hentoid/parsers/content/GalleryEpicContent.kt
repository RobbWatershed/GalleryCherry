package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.images.GE_COSPLAY_START
import me.devsaki.hentoid.parsers.images.GalleryEpicParser.Companion.fromCosplayJsBlock
import me.devsaki.hentoid.parsers.removeTextualTags
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class GalleryEpicContent : BaseContentParser() {

    @Selector(value = "head meta[name='description']", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = "body script")
    private var scripts: List<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.GALLERYEPIC
        content.url = url.replace(Site.GALLERYEPIC.url, "")
        content.title = removeTextualTags(title)

        var found = false
        scripts?.forEach { script ->
            if (found) return@forEach
            val str = script.toString()
            if (str.contains(GE_COSPLAY_START, true)) {
                fromCosplayJsBlock(str)?.let { meta ->
                    meta.update(content, url, updateImages)
                    found = true
                }
            }
        }
        if (!found) return Content(status = StatusContent.IGNORED)

        return content
    }
}
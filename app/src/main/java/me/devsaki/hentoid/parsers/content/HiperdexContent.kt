package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.images.HiperdexParser.Companion.parseContent

class HiperdexContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.HIPERDEX
        if (url.isEmpty()) return Content(status = StatusContent.IGNORED)
        content.setRawUrl(url)

        return parseContent(content, updateImages)
    }
}
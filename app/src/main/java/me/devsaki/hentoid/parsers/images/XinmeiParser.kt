package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.getOnlineDocument

class XinmeiParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        return getOnlineDocument(content.galleryUrl)
            ?.select(".container .figure a img")
            ?.map { getImgSrc(it) }
            ?: emptyList()
    }
}
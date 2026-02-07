package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineDocument

class XiutakuParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val doc = getOnlineDocument(content.galleryUrl) ?: return emptyList()
        val pages = doc.select(".pagination-link").map { fixUrl(it.attr("href"), Site.XIUTAKU.url) }

        val result = ArrayList<String>()
        pages.forEach { page ->
            getOnlineDocument(page)?.let { doc ->
                val imgs = doc.select(".article-fulltext img")
                result.addAll(imgs.map { getImgSrc(it) })
            }
        }
        return result
    }
}
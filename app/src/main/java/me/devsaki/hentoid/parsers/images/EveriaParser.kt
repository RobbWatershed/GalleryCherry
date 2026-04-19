package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.getOnlineDocument

class EveriaParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val doc = getOnlineDocument(content.galleryUrl) ?: return emptyList()
        val nbPages = doc.select(".post-page-numbers").count()

        val result = ArrayList<String>()
        repeat(nbPages) { time ->
            getOnlineDocument("${content.galleryUrl}${time + 1}/")?.let { doc ->
                val imgs = doc.select(".wp-block-image img")
                result.addAll(imgs.map { getImgSrc(it) })
            }
        }
        return result
    }
}
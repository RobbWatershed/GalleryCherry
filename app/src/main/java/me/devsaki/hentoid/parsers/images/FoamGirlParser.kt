package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.getOnlineDocument

class FoamGirlParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        var nbPages = 1

        getOnlineDocument(content.galleryUrl)?.let { doc ->
            val pages = doc.select(".page-numbers")
            if (pages.isNotEmpty()) {
                val lastPage = pages.last {
                    it.attr("title").startsWith("page", true)
                            || it.attr("title").startsWith("last page", true)
                }
                nbPages = lastPage.ownText().replace(",", "").toInt()
            }
        }

        val result = ArrayList<String>()
        repeat(nbPages) { times ->
            getOnlineDocument(
                content.galleryUrl.replace(
                    ".html",
                    "_${times + 1}.html"
                )
            )?.let { doc ->
                var imgs = doc.select("#image_div img")
                if (imgs.isEmpty()) imgs = doc.select("#image_div_all img")
                result.addAll(imgs.map { getImgSrc(it) })
            }
        }
        return result
    }
}
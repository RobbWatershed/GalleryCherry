package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.getOnlineDocument

class V2PhParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val baseUrl = content.galleryUrl

        val result = ArrayList<String>()
        var found = true
        repeat(100) { time ->
            if (!found) return@repeat
            val url = if (1 == time) baseUrl else "$baseUrl?page=$time"
            getOnlineDocument(url)?.let { doc ->
                val imgs = doc.select(".album-photo img")
                if (imgs.isEmpty()) found = false
                result.addAll(imgs.map { getImgSrc(it) })
            }
        }
        return result
    }
}
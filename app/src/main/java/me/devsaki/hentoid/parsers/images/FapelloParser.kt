package me.devsaki.hentoid.parsers.images

import androidx.core.net.toUri
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.getOnlineResource
import org.jsoup.Jsoup

class FapelloParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val artist = content.galleryUrl.toUri().lastPathSegment

        val result = ArrayList<String>()
        var stop = false
        repeat(500) { time ->
            if (stop) return@repeat
            getOnlineResource(
                "https://fapello.com/ajax/model/$artist/page-${time + 1}/",
                null,
                Site.FAPELLO.useMobileAgent,
                Site.FAPELLO.useHentoidAgent,
                Site.FAPELLO.useWebviewAgent
            ).let { res ->
                if (res.code >= 400) stop = true
                else {
                    res.body.use { bdy ->
                        val doc = Jsoup.parse(bdy.string())
                        val imgs = doc.select("img")
                        if (imgs.isNotEmpty()) {
                            result.addAll(imgs.map {
                                getImgSrc(it).replace("_300px", "")
                            })
                        } else stop = true
                    }
                }
            }
        }
        return result
    }
}
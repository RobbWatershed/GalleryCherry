package me.devsaki.hentoid.parsers.images

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.POST_MIME_TYPE
import me.devsaki.hentoid.util.network.getOnlineDocument
import me.devsaki.hentoid.util.network.postOnlineResource
import org.jsoup.Jsoup

class SxypixParser : BaseImageListParser() {

    @JsonClass(generateAdapter = true)
    data class SxypixGallery(val r: List<String>) {
        val pics: List<String>
            get() {
                if (r.isEmpty()) return mutableListOf()

                val res = ArrayList<String>()
                for (s in r) {
                    val doc = Jsoup.parse(s)
                    var elts = doc.select(".gall_pix_el")
                    if (elts.isEmpty()) elts = doc.select(".gall_pix_pix")

                    if (elts.isEmpty()) return emptyList()
                    val pics = elts.map { getImgSrc(it) }
                    res.addAll(
                        pics
                            .map { it.replace("\\/", "/") }
                            .map { "https:$it" }
                            .toList()
                    )
                }
                return res
            }
    }

    override fun parseImages(content: Content): List<String> {
        var subdomain = ""
        var aid = ""
        var ghash = ""

        val doc = getOnlineDocument(content.galleryUrl)
        if (doc != null) {
            val elt = doc.selectFirst(".gallgrid")
            if (elt != null) {
                subdomain = elt.attr("data-x")
                aid = elt.attr("data-aid")
                ghash = elt.attr("data-ghash")
            }
        }

        postOnlineResource(
            "https://sxypix.com/php/gall.php",
            null,
            useMobileAgent = false, useHentoidAgent = false, useWebviewAgent = false,
            body = "x=$subdomain&ghash=$ghash&aid=$aid",
            mimeType = POST_MIME_TYPE
        ).use { res ->
            val g = jsonToObject(res.body.string(), SxypixGallery::class.java)
            return g?.pics ?: emptyList()
        }
    }
}
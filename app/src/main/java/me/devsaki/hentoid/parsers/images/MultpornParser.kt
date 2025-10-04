package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.addCurrentCookiesToHeader
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Locale

class MultpornParser : BaseImageListParser() {
    companion object {
        fun getJuiceboxRequestUrl(scripts: List<Element>): String {
            for (e in scripts) {
                val scriptText = e.toString().lowercase(Locale.getDefault()).replace("\\/", "/")
                val juiceIndex = scriptText.indexOf("/juicebox/xml/")
                if (juiceIndex > -1) {
                    val juiceEndIndex = scriptText.indexOf("\"", juiceIndex)
                    return fixUrl(
                        scriptText.substring(juiceIndex, juiceEndIndex),
                        Site.MULTPORN.url
                    )
                }
            }
            return ""
        }

        @Throws(IOException::class)
        fun getImagesUrls(juiceboxUrl: String, galleryUrl: String): List<String> {
            val result: MutableList<String> = java.util.ArrayList()
            val headers: MutableList<Pair<String, String>> = java.util.ArrayList()
            addCurrentCookiesToHeader(juiceboxUrl, headers)
            headers.add(Pair(HEADER_REFERER_KEY, galleryUrl))
            getOnlineDocument(
                juiceboxUrl,
                headers,
                Site.MULTPORN.useHentoidAgent, Site.MULTPORN.useWebviewAgent
            )?.let { doc ->
                var images: List<Element> = doc.select("juicebox image")
                if (images.isEmpty()) images = doc.select("juicebox img")
                for (img in images) {
                    val link = img.attr("linkURL")
                    if (!result.contains(link)) result.add(link) // Make sure we're not adding duplicates
                }
            }
            return result
        }
    }

    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()
        processedUrl = content.galleryUrl

        getOnlineDocument(
            content.galleryUrl,
            fetchHeaders(content),
            Site.ALLPORNCOMIC.useHentoidAgent, Site.ALLPORNCOMIC.useWebviewAgent
        )?.let { doc ->
            val juiceboxUrl = getJuiceboxRequestUrl(doc.select("head script"))
            result.addAll(getImagesUrls(juiceboxUrl, processedUrl))
        }

        return result
    }
}
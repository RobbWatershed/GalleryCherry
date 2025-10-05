package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.decode64
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.nio.charset.StandardCharsets

class PicsXParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val doc = getOnlineDocument(content.galleryUrl)
        if (doc != null) {
            val images = doc.select(".image-container img")
            val imgData = doc.select("#images-container script")
            return parseImages(images, imgData)
        } else return mutableListOf()
    }

    companion object {
        fun parseImages(
            images: MutableList<Element>,
            imageData: MutableList<Element>
        ): List<String> {
            // Using images directly
            if (!images.isEmpty() && imageData.isEmpty()) return images.map { getImgSrc(it) }
                .distinct()
                .toList()

            // Using script data
            var scriptData: String? = null
            for (e in imageData) {
                if (e.html().contains("imagesHtml")) {
                    scriptData = e.html()
                    break
                }
            }
            if (scriptData != null) {
                // Get image HTML
                var data = scriptData.replace(" ", "")
                val stringStart = data.indexOf('"')
                val stringEnd = data.indexOf('"', stringStart + 1)
                data = data.substring(stringStart + 1, stringEnd)
                data = String(decode64(data), StandardCharsets.UTF_8)
                data = "<html><body>$data</body></html>"

                // Parse image HTML
                val doc = Jsoup.parse(data)
                return doc.select(".image-container img").map { getImgSrc(it) }.distinct().toList()
            }
            return mutableListOf()
        }
    }
}
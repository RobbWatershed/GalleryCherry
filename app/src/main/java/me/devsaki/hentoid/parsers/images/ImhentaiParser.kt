package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getExtensionFromFormat
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException

class ImhentaiParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()
        val headers = fetchHeaders(content)

        // The whole algorithm is in app.js
        // 1- Get image extension from gallery data (JSON on HTML body)
        // 2- Generate image URL from imagePath constant, gallery ID, page number and extension

        // 1- Get image extension from gallery data (JSON on HTML body)
        val doc = getOnlineDocument(
            content.readerUrl,
            headers,
            Site.IMHENTAI.useHentoidAgent,
            Site.IMHENTAI.useWebviewAgent
        )
        if (doc != null) {
            val thumbs: List<Element> = doc.select(".gthumb img")
            val scripts: List<Element> = doc.select("body script")

            // Parse the image format list to get the whole list and the correct extensions
            var imageFormats: Map<String, String>? = null
            for (s in scripts) {
                try {
                    val jsonBeginIndex = s.data().indexOf("'{\"1\"")
                    if (jsonBeginIndex > -1) {
                        imageFormats = jsonToObject(
                            s.data().substring(jsonBeginIndex + 1).replace("\"}');", "\"}")
                                .replace("\n", ""), MAP_STRINGS
                        )
                        break
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }

            // 2- Generate image URL from imagePath constant, gallery ID, page number and extension
            if (thumbs.isNotEmpty() && imageFormats != null) {
                val thumbUrl = getImgSrc(thumbs[0])
                val thumbPath = thumbUrl.substring(0, thumbUrl.lastIndexOf("/") + 1)

                // Forge all page URLs
                for (i in 0 until imageFormats.size) {
                    val imgUrl = thumbPath + (i + 1) + "." +
                            getExtensionFromFormat(imageFormats, i)
                    result.add(imgUrl)
                }
            }
        }

        return result
    }
}
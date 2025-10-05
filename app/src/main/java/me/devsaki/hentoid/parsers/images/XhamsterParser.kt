package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.json.sources.XhamsterGalleryContent
import me.devsaki.hentoid.json.sources.XhamsterGalleryQuery
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.getOnlineDocument
import me.devsaki.hentoid.util.serializeToJson
import okhttp3.HttpUrl
import kotlin.math.ceil

class XhamsterParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val result = ArrayList<String>()
        var i = 0
        while (i < ceil(content.qtyPages / 16.0)) {
            val query = XhamsterGalleryQuery(content.uniqueSiteId, i + 1)

            val url = HttpUrl.Builder()
                .scheme("https")
                .host("xhamster.com")
                .addPathSegment("x-api")
                .addQueryParameter(
                    "r",
                    "[" + serializeToJson(
                        query,
                        XhamsterGalleryQuery::class.java
                    ) + "]"
                ) // Not a 100% JSON-compliant format
                .build()

            val headers: MutableList<Pair<String, String>> = ArrayList()
            headers.add(Pair<String, String>("x-requested-with", "XMLHttpRequest"))

            val doc = getOnlineDocument(
                url.toString(), headers,
                useHentoidAgent = false,
                useWebviewAgent = false
            )
            if (doc != null) {
                // JSON response is wrapped between [ ... ]'s
                val body = doc.body().childNode(0).toString()
                    .replace("\n[", "")
                    .replace("}]}]", "}]}")

                val galleryContent = jsonToObject(body, XhamsterGalleryContent::class.java)
                if (galleryContent != null) result.addAll(galleryContent.toImageUrlList())
            }
            i++
        }

        return result
    }
}
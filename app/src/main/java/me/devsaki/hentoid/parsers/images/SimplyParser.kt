package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.sources.simply.SimplyGalleryMetadata
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineDocument

class SimplyParser : BaseImageListParser() {
    override fun parseImages(content: Content): List<String> {
        var result: List<String> = ArrayList()
        processedUrl = content.galleryUrl

        val headers = fetchHeaders(content)

        // 1. Scan the gallery page for viewer URL (can't be deduced)
        var viewerUrl: String? = null
        var doc = getOnlineDocument(
            content.galleryUrl,
            headers,
            Site.SIMPLY.useHentoidAgent,
            Site.SIMPLY.useWebviewAgent
        )
        if (doc != null) {
            val page = doc.select(".image-wrapper").first() ?: return result
            var parent = page.parent()
            while (parent != null && !parent.`is`("a")) parent = parent.parent()
            if (null == parent) return result
            viewerUrl = fixUrl(parent.attr("href"), Site.SIMPLY.url)
        }
        if (null == viewerUrl) return result

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()

        // 2. Get the metadata on the viewer page
        doc = getOnlineDocument(
            viewerUrl,
            headers,
            Site.SIMPLY.useHentoidAgent,
            Site.SIMPLY.useWebviewAgent
        )
        if (doc != null) {
            val jsonData =
                doc.select("body script[type='application/json']").first() ?: return result
            val data = jsonData.data()
            if (!data.contains("thumb")) return result
            jsonToObject(
                data,
                SimplyGalleryMetadata::class.java
            )?.let {
                result = it.getPageUrls()
            }
        }

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()

        return result
    }
}
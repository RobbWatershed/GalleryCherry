package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.addSavedCookiesToHeader
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FapalityParser : BaseImageListParser() {

    @Throws(Exception::class)
    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()

        val headers = ArrayList<Pair<String, String>>()
        addSavedCookiesToHeader(content.downloadParams, headers)

        // 1. Scan the gallery page for page viewer URLs
        val pageUrls: MutableList<String> = ArrayList()
        var doc: Document? = getOnlineDocument(
            content.galleryUrl,
            headers,
            Site.FAPALITY.useHentoidAgent,
            Site.FAPALITY.useWebviewAgent
        )
        if (doc != null) {
            val chapters: MutableList<Element> = doc.select("a[itemprop][href*=com/photos/]")
            for (e in chapters) pageUrls.add(e.attr("href"))
        }

        progressStart(content, null, pageUrls.size)

        // 2. Open each page URL and get the image data until all images are found
        pageUrls.forEachIndexed { idx, url ->
            if (processHalted.get()) return@forEachIndexed
            doc = getOnlineDocument(
                url,
                headers,
                Site.FAPALITY.useHentoidAgent,
                Site.FAPALITY.useWebviewAgent
            )
            if (doc != null) {
                val images = doc.select(".simple-content img")
                result.addAll(
                    images.map { it.attr("src") }.filterNot { it.isEmpty() }.toList()
                )
            }
            progressPlus(idx * 1f / pageUrls.size)
        }
        progressComplete()

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()

        return result
    }
}
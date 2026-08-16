package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.fetchHeaders
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.chapterStr
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Document

class MrmParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").filterNot { it.isEmpty() }.count() > 3
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("div.entry-pagination"))
    }

    /*
    override fun parseImageFiles(onlineContent: Content, storedContent: Content?): List<ImageFile> {
        return urlsToImageFiles(
            parseContentImages(onlineContent),
            onlineContent.downloadRange,
            StatusContent.SAVED,
            Site.MRM,
            onlineContent.coverImageUrl
        )
    }
     */

    override fun getChapters(
        content: Content,
        galleryPage: Document
    ): List<Chapter> {
        processedUrl = content.galleryUrl

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        val chapterUrls: MutableList<String> = ArrayList()
        galleryPage.select("div.entry-pagination").first()?.let { chapterContainer ->
            for (e in chapterContainer.children()) {
                if (e.hasClass("current")) chapterUrls.add(content.galleryUrl) // current chapter
                else if (e.hasAttr("href")) chapterUrls.add(e.attr("href"))
            }
        }
        if (chapterUrls.isEmpty()) chapterUrls.add(content.galleryUrl) // "one-shot" book

        val result: MutableList<Chapter> = ArrayList()
        for ((order, chpUrl) in chapterUrls.withIndex()) {
            val chp = Chapter(
                order = order + 1,
                url = chpUrl,
                name = "$chapterStr ${order + 1}"
            )
            chp.setContentId(content.id)
            result.add(chp)
        }
        return result
    }

    /*
    private fun parseContentImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()
        processedUrl = content.galleryUrl

        val headers = fetchHeaders(content)

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        val chapterUrls: MutableList<String> = ArrayList()
        getOnlineDocument(
            content.galleryUrl,
            headers,
            Site.MRM.useMobileAgent, Site.MRM.useHentoidAgent, Site.MRM.useWebviewAgent
        )?.let { doc ->
            doc.select("div.entry-pagination").first()?.let { chapterContainer ->
                for (e in chapterContainer.children()) {
                    if (e.hasClass("current")) chapterUrls.add(content.galleryUrl) // current chapter; this is the reason why MrmParser still has its own parseImageFiles override
                    else if (e.hasAttr("href")) chapterUrls.add(e.attr("href"))
                }
            }
        }
        if (chapterUrls.isEmpty()) chapterUrls.add(content.galleryUrl) // "one-shot" book

        progressStart(content)

        // 2. Open each chapter URL and get the image data until all images are found
        val isRangeChapters = isRangeChapters(content.downloadRange)
        val range =
            if (isRangeChapters) rangeToNumbers(content.downloadRange) else emptyList()

        chapterUrls.forEachIndexed { index, url ->
            if (processHalted.get()) return@forEachIndexed
            result.addAll(parseChapterImages(url, headers))
            progressPlus((index + 1f) / chapterUrls.size)
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()

        if (result.isNotEmpty()) content.coverImageUrl = result[0]

        progressComplete()
        return result
    }

     */

    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        return urlsToImageFiles(
            parseChapterImages(chp.url, headers),
            content.downloadRange,
            targetOrder, StatusContent.SAVED, 1000, chp
        )
    }

    private fun parseChapterImages(
        chapterUrl: String,
        headers: List<Pair<String, String>>? = null
    ): List<String> {
        if (processedUrl.isEmpty()) processedUrl = chapterUrl

        getOnlineDocument(
            chapterUrl,
            headers ?: fetchHeaders(chapterUrl),
            Site.MRM.useMobileAgent, Site.MRM.useHentoidAgent, Site.MRM.useWebviewAgent
        )?.let { doc ->
            val images =
                doc.select(".entry-content img[decoding=async][src*='https://i']").filterNotNull()
            return images.map { getImgSrc(it) }.filterNot { it.isEmpty() }
        }
        return emptyList()
    }

}
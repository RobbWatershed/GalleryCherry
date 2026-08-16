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
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import org.jsoup.nodes.Document
import timber.log.Timber

class MrmParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").filterNot { it.isEmpty() }.count() > 3
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("div.entry-pagination"))
    }

    override fun getChapters(
        content: Content,
        galleryPage: Document
    ): List<Chapter> {
        processedUrl = content.galleryUrl

        val headers = fetchHeaders(content)

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        val chapterUrls: MutableList<String> = ArrayList()
        galleryPage.select("div.entry-pagination").first()?.let { chapterContainer ->
            var previousLink = ""
            var ellipsisFrom = -1
            chapterContainer.children().forEach { e ->
                val skip = e.hasClass("next") || e.hasClass("prev")
                if (!skip) {
                    val link = if (e.hasClass("current")) content.galleryUrl // current chapter
                    else if (e.hasAttr("href")) e.attr("href")
                    else if (e.text() == "…") {
                        val previousElts = previousLink.split('/')
                        if (previousElts.size > 2)
                            ellipsisFrom = previousElts[previousElts.size - 2].toInt() + 1
                        ""
                    } else ""

                    if (link.isNotBlank()) {
                        if (ellipsisFrom > -1) {
                            // Close the "..." gap by guessing chapter URLs
                            // NB : Fails when the site provides "subchapters" (e.g. 4.6, 2.5), hence the sanity check
                            val curElts = link.split('/')
                            val ellipsisTo = curElts[curElts.size - 2].toInt()
                            val template = link.replace("/${ellipsisTo}/", "/$$$/")
                            for (i in ellipsisFrom..ellipsisTo) {
                                val chpUrl = template.replace("$$$", i.toString())
                                // Sanity check
                                getOnlineResourceFast(
                                    chpUrl,
                                    headers,
                                    Site.MRM.useMobileAgent,
                                    Site.MRM.useHentoidAgent,
                                    Site.MRM.useWebviewAgent
                                ).use {
                                    if (it.code >= 400) Timber.d("Failed to guess chapter URL (${it.code}) : $chpUrl")
                                    else chapterUrls.add(chpUrl)
                                }
                            }
                            ellipsisFrom = -1
                        } else chapterUrls.add(link)
                    }
                    previousLink = link
                }
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
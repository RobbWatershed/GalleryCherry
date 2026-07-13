package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.retrofit.sources.HiperdexServer
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.getCookies
import org.jsoup.nodes.Document

class HiperdexParser : BaseChapteredImageListParser() {

    // Key : chapter number; Value : number of pages
//    var chaptersPages: MutableMap<Int, Int> = HashMap()

    companion object {
        fun getUrlParts(url: String): Pair<String, String> {
            val urlParts = url.split('/')
            val contentSlug = urlParts[4]
            val chapterNumber = if (urlParts.size > 5) urlParts[5] else ""
            return Pair(contentSlug, chapterNumber)
        }

        fun parseContent(content: Content, updateImages: Boolean): Content {
            val cookieStr = getCookies(
                content.galleryUrl,
                null,
                Site.HIPERDEX.useMobileAgent,
                Site.HIPERDEX.useHentoidAgent,
                Site.HIPERDEX.useWebviewAgent
            )
            val parts = getUrlParts(content.galleryUrl)
            val contentSlug = parts.first
            val chapterNumber = parts.second
            val response = HiperdexServer.api.getContent(
                cookieStr,
                content.galleryUrl,
                "{\"0\":{\"json\":{\"slug\":\"$contentSlug\"}}}"
            ).execute()
            if (!response.isSuccessful) return content
            response.body()?.let {
                if (it.isNotEmpty()) {
                    it[0].update(content, chapterNumber, updateImages)
                }
            }
            return content
        }

        fun parseChapters(galleryUrl: String): List<Chapter> {
            val cookieStr = getCookies(
                galleryUrl,
                null,
                Site.HIPERDEX.useMobileAgent,
                Site.HIPERDEX.useHentoidAgent,
                Site.HIPERDEX.useWebviewAgent
            )
            val contentSlug = getUrlParts(galleryUrl).first
            val contentResponse = HiperdexServer.api.getContent(
                cookieStr,
                galleryUrl,
                "{\"0\":{\"json\":{\"slug\":\"$contentSlug\"}}}"
            ).execute()
            if (!contentResponse.isSuccessful) throw EmptyResultException("No content found for $galleryUrl")
            var contentId = 0L
            contentResponse.body()?.let {
                if (it.isNotEmpty()) {
                    contentId = it[0].contentId
                }
            }
            if (0L == contentId) throw EmptyResultException("No content ID found for $galleryUrl")
            val response = HiperdexServer.api.getChapters(
                cookieStr,
                galleryUrl,
                "{\"0\":{\"json\":{\"seriesId\":$contentId,\"chapterId\":null,\"sort\":\"best\",\"page\":1,\"limit\":20}}}"
            ).execute()
            if (!response.isSuccessful) return emptyList()
            response.body()?.let {
                if (it.isNotEmpty())
                    return it[0].toChapters(galleryUrl)
            }
            return emptyList()
        }

        fun parseChapterPages(chapterUrl: String): List<String> {
            val cookieStr = getCookies(
                chapterUrl,
                null,
                Site.HIPERDEX.useMobileAgent,
                Site.HIPERDEX.useHentoidAgent,
                Site.HIPERDEX.useWebviewAgent
            )
            val parts = getUrlParts(chapterUrl)
            val contentSlug = parts.first
            val chapterNumber = parts.second
            val response = HiperdexServer.api.getChapterPages(
                cookieStr,
                chapterUrl,
                "{\"0\":{\"json\":{\"seriesSlug\":\"$contentSlug\",\"chapterNumber\":$chapterNumber}}}"
            ).execute()
            if (!response.isSuccessful) return emptyList()
            response.body()?.let {
                if (it.isNotEmpty()) return it[0].toPages()
            }
            return emptyList()
            /*
                        val result: MutableList<String> = ArrayList()
                        getOnlineDocument(
                            chapterUrl,
                            headers ?: fetchHeaders(chapterUrl),
                            Site.HIPERDEX.useMobileAgent,
                            Site.HIPERDEX.useHentoidAgent,
                            Site.HIPERDEX.useWebviewAgent
                        )?.let { doc ->
                            doc.select("head [property=og:image]").first()?.let { ogImg ->
                                val imgUrl = ogImg.attr("content")
                                val parts = UriParts(imgUrl)
                                val firstImg = parts.fileNameNoExt
                                try {
                                    val firstNum = firstImg.toInt()
                                    val lastNum = firstNum + pagesCount - 1
                                    for (i in firstNum..lastNum) {
                                        parts.fileNameNoExt = formatIntAsStr(i, firstImg.length)
                                        result.add(parts.toUri())
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "An error occured while parsing chapter $chapterUrl")
                                }
                            }
                        }
                        return result
                    }
             */
        }
    }

    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split('/')
        var part = parts[parts.size - 1]
        if (part.isEmpty()) part = parts[parts.size - 2]
        return isNumeric(part)
    }

    override fun getChapterSelector(): ChapterSelector {
        // Don't need to do anything as we directly override getChapters
        return ChapterSelector(emptyList(), "", "")
    }

    override fun getChapters(content: Content, galleryPage: Document): List<Chapter> {
        val chps = parseChapters(content.galleryUrl)
        /*
        chaptersPages.clear()
        chps.forEach {
            chaptersPages[it.order] = it.nbPages
        }
         */
        return chps
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        // pause(1000) // Rate-limited but only on site pages
        /*
        if (chaptersPages.isEmpty()) // Cache number of pages for each chapter
            getChapters(content, Document("bogus", ""))
         */
        val imgUrls = parseChapterPages(chp.url/*, headers, chaptersPages[chp.order] ?: 0*/)
        if (imgUrls.isEmpty()) throw EmptyResultException("No images detected for ${chp.url}")
        return urlsToImageFiles(
            imgUrls,
            content.downloadRange,
            targetOrder,
            StatusContent.SAVED,
            10000,
            chp
        )
    }
}
package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.retrofit.sources.HiperdexServer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.download.DownloadRateLimiter.take
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.waitBlocking429
import org.jsoup.nodes.Document
import retrofit2.Call
import retrofit2.Response
import timber.log.Timber

class HiperdexParser : BaseChapteredImageListParser() {

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
            val response = call429(call = {
                HiperdexServer.api.getChapterPages(
                    cookieStr,
                    chapterUrl,
                    "{\"0\":{\"json\":{\"seriesSlug\":\"$contentSlug\",\"chapterNumber\":$chapterNumber}}}"
                )
            })
            if (!response.isSuccessful) {
                Timber.i("chapter $chapterNumber for $contentSlug : couldn't get pages (bad response : ${response.code()} ${response.message()})")
                return emptyList()
            }
            val body = response.body()
            if (body.isNullOrEmpty()) {
                Timber.i("chapter $chapterNumber for $contentSlug : couldn't get pages (empty body)")
                return emptyList()
            }

            return body[0].toPages()
        }

        private fun <T> call429(call: () -> Call<T>, id: String = ""): Response<T> {
            var waited = 0
            take()
            var resp = call.invoke().execute()
            while (
                waitBlocking429(resp, Settings.http429DefaultDelaySecs * 1000)
                && waited < 2
            ) {
                waited++
                take()
                resp = call.invoke().execute()
            }
            require(resp.code() < 400) {
                String.format(
                    "Unreachable illust : code=${resp.code()} (${resp.message()}) [$id - $waited]",
                )
            }
            return resp
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
        chps.forEach { it.setContentId(content.id) }
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
        val imgUrls = parseChapterPages(chp.url)
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
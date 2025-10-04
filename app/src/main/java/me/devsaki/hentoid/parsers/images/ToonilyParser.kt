package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.download.getCanonicalUrl
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.network.POST_MIME_TYPE
import me.devsaki.hentoid.util.network.getOnlineDocument
import me.devsaki.hentoid.util.network.postOnlineDocument
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber

class ToonilyParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split("/")
        var part = parts[parts.size - 1]
        if (part.isEmpty()) part = parts[parts.size - 2]
        return part.contains("chap")
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("[class^=wp-manga-chapter] a"))
    }

    override fun getChapterLinks(
        doc: Document,
        onlineContent: Content,
        selector: ChapterSelector
    ): List<Element> {
        val canonicalUrl = getCanonicalUrl(doc)
        val headers = fetchHeaders(onlineContent)
        // Retrieve the chapters page chunk
        postOnlineDocument(
            canonicalUrl + "ajax/chapters/",
            headers,
            Site.TOONILY.useHentoidAgent, Site.TOONILY.useWebviewAgent,
            "",
            POST_MIME_TYPE
        )?.let { return it.select(selector.selectors[0]) }
        throw EmptyResultException("Chapters page couldn't be downloaded @ $canonicalUrl")
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            content.site.useHentoidAgent,
            content.site.useWebviewAgent
        )?.let { doc ->
            val images = doc.select(".reading-content img").filterNotNull()
            val imageUrls = images.map { getImgSrc(it) }.filter { it.isNotEmpty() }
            if (imageUrls.isNotEmpty()) {
                progressPlus(1f)
                return urlsToImageFiles(
                    imageUrls,
                    targetOrder,
                    StatusContent.SAVED,
                    1000,
                    chp
                )
            } else Timber.i("Chapter parsing failed for %s : no pictures found", chp.url)
        } ?: run {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
    }
}
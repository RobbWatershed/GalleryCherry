package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.sources.gallery_epic.GE_IMG_SERVER
import me.devsaki.hentoid.json.sources.gallery_epic.GalleryEpicCosplayData
import me.devsaki.hentoid.util.LIST_STRINGS
import me.devsaki.hentoid.util.findClosure
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getOnlineDocument
import java.util.regex.Pattern

// Only capture data, not array delimiters, to avoid hanging on incomplete/truncated scripts
val GE_IMGS_PATTERN: Pattern by lazy { Pattern.compile("(\\\\\"([a-f0-9]{4,}-?)+\\\\\",?){2,}") }
const val GE_COSPLAY_START = "\\\"cosplay\\\":{"

class GalleryEpicParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val headers: MutableList<Pair<String, String>> = ArrayList()
        val cookieStr = getCookies(
            content.galleryUrl,
            null,
            Site.GALLERYEPIC.useMobileAgent,
            Site.GALLERYEPIC.useHentoidAgent,
            Site.GALLERYEPIC.useWebviewAgent
        )
        headers.add(Pair(HEADER_REFERER_KEY, Site.GALLERYEPIC.url))
        if (cookieStr.isNotEmpty())
            headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))

        val doc = getOnlineDocument(content.galleryUrl, headers) ?: return emptyList()
        val scripts = doc.select("body script")

        val imgs: MutableList<String> = ArrayList()
        scripts.forEach { scr ->
            val matcher = GE_IMGS_PATTERN.matcher(scr.toString())
            if (matcher.find())
                matcher.group(0)?.let { imgs.addAll(fromImgsJsBlock(it) ?: emptyList()) }
        }
        if (imgs.isNotEmpty()) return imgs.map { GE_IMG_SERVER + it }

        return scripts.firstOrNull {
            it.toString().contains(GE_COSPLAY_START, true)
        }
            ?.toString()?.let {
                return fromCosplayJsBlock(it)?.imageList ?: emptyList()
            } ?: emptyList()
    }

    companion object {
        fun fromImgsJsBlock(data: String): List<String>? {
            val cleanData1 = if (data.endsWith(',')) data.substring(0, data.length - 2) else data
            val cleanData2 = cleanData1
                .replace("\\\\\\\"", "'")
                .replace("\\\"", "\"")
                .replace("\"\"", "\"")
                .replace("\"_\"", "\"")
            return jsonToObject("[$cleanData2]", LIST_STRINGS)
        }

        fun fromCosplayJsBlock(data: String): GalleryEpicCosplayData? {
            val startIndex = data.indexOf(GE_COSPLAY_START) + 12
            if (startIndex < 0) return null
            val data1 = data.substring(startIndex)
            val endIndex = findClosure(data1)
            val cleanData1 = data1.substring(0, endIndex + 1)
                .replace("\\\\\\\"", "'")
                .replace("\\\"", "\"")
                .replace("\"\"", "\"")
                .replace("\"_\"", "\"")
            return jsonToObject(cleanData1, GalleryEpicCosplayData::class.java)
        }
    }
}
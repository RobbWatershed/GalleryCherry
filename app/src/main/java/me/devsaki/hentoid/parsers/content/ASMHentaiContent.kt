package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import java.util.Locale

private const val BADGE_CONST = "badge"

class ASMHentaiContent : BaseContentParser() {

    @Selector(value = "div.cover a img")
    private var cover: Element? = null

    @Selector(value = "div.info h1:first-child", defValue = NO_TITLE)
    private lateinit var title: String

    @Selector("div.pages h3")
    private var pages: List<String>? = null

    @Selector(value = "div.info div.tags a[href^='/artist']")
    private var artists: List<Element>? = null

    @Selector(value = "div.info div.tags a[href^='/tag']")
    private var tags: List<Element>? = null

    @Selector(value = "div.info div.tags a[href^='/parod']")
    private var series: List<Element>? = null

    @Selector(value = "div.info div.tags a[href^='/character']")
    private var characters: List<Element>? = null

    @Selector(value = "div.info div.tags a[href^='/language']")
    private var languages: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val theUrl = canonicalUrl.ifEmpty { url }
        if (theUrl.isEmpty()) return Content(site = Site.ASMHENTAI, status = StatusContent.IGNORED)

        content.site = if (theUrl.lowercase(Locale.getDefault())
                .contains("comics")
        ) Site.ASMHENTAI_COMICS else Site.ASMHENTAI

        content.setRawUrl(theUrl)
        cover?.let {
            content.coverImageUrl = "https:" + getImgSrc(it)
        }
        content.title = cleanup(title)

        val attributes = AttributeMap()
        parseAttributes(
            attributes,
            AttributeType.ARTIST,
            artists,
            false,
            BADGE_CONST,
            Site.ASMHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.TAG,
            tags,
            false,
            BADGE_CONST,
            Site.ASMHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.SERIE,
            series,
            false,
            BADGE_CONST,
            Site.ASMHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.CHARACTER,
            characters,
            false,
            BADGE_CONST,
            Site.ASMHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.LANGUAGE,
            languages,
            false,
            BADGE_CONST,
            Site.ASMHENTAI
        )
        content.putAttributes(attributes)
        if (updateImages) {
            content.qtyPages = 0
            content.setImageFiles(emptyList())
        }
        return content
    }
}
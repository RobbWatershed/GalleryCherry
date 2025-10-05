package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.util.regex.Pattern

private const val GALLERY_FOLDER = "/photos/gallery/"

// e.g. "Big bewbs - 50 Pics | xHamster.com"
private val TITLE_NUMBER_PATTERN = Pattern.compile(".* - (\\d+) .*amster.*")

class XhamsterContent : BaseContentParser() {

    @Selector(value = "head meta[name='twitter:url']", attr = "content", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(value = "img.thumb", attr = "src", defValue = "")
    private lateinit var thumbs: MutableList<String>

    @Selector(value = "h1.page-title", defValue = "")
    private lateinit var title1: String

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private lateinit var title2: String

    @Selector("head title")
    private var headTitle: String? = null

    @Selector(value = ".categories_of_pictures .categories-container__item")
    private var tags: MutableList<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.XHAMSTER

        val theUrl = galleryUrl.ifEmpty { url }
        val galleryLocation =
            theUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length
        content.url = theUrl.substring(galleryLocation)
        content.coverImageUrl = if (thumbs.isEmpty()) "" else thumbs[0]
        if (!title1.isEmpty()) content.title = title1
        else content.title = title2

        if (updateImages) {
            val matcher = TITLE_NUMBER_PATTERN.matcher(headTitle ?: NO_TITLE)

            Timber.d("Match found? %s", matcher.find())

            if (matcher.groupCount() > 0) {
                val results = matcher.group(1)
                if (results != null) content.qtyPages = results.toInt()
            }
        }

        val attributes = AttributeMap()

        parseAttributes(attributes, AttributeType.TAG, tags, true, Site.XHAMSTER)

        content.addAttributes(attributes)

        content.status = StatusContent.SAVED

        return content
    }
}
package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.parsers.images.PawParser
import me.devsaki.hentoid.parsers.images.PawParser.Companion.parseGallery
import me.devsaki.hentoid.parsers.images.PawParser.Companion.parseUser
import me.devsaki.hentoid.util.network.getCookies

class PawContent : BaseContentParser() {

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val parts = PawParser.Companion.PawParts(url)

        val cookieStr = getCookies(
            url,
            null,
            Site.PAWCHIVE.useMobileAgent,
            Site.PAWCHIVE.useHentoidAgent,
            Site.PAWCHIVE.useWebviewAgent
        )
        val userAgent = getUserAgent(Site.PAWCHIVE)

        if (parts.isGallery) return parseGallery(
            content,
            url,
            updateImages,
            parts.service,
            parts.userId,
            parts.postId,
            cookieStr,
            userAgent
        )
        else if (parts.isUser) return parseUser(
            content,
            url,
            parts.service,
            parts.userId,
            cookieStr,
            userAgent
        )

        return Content(site = Site.PAWCHIVE, status = StatusContent.IGNORED)
    }
}
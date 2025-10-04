package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class ManhwaActivity : BaseBrowserActivity() {
    companion object {
        const val GALLERY_PATTERN = "//manhwahentai.me/[%\\w\\-]+/[%\\w\\-]{3,}/$"

        private const val DOMAIN_FILTER = "manhwahentai.me"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "") + "ch[%\\w]+-[0-9]+/$")
        private val REMOVABLE_ELEMENTS =
            arrayOf(
                ".hide-sticky-menu",
                ".c-ads",
                "iframe",
                "\$x//a[contains(@href,\"ourdream.ai\")]/.."
            )
        private val JS_CONTENT_BLACKLIST = arrayOf("adprovider")
        private val BLOCKED_CONTENT = arrayOf(".cloudfront.net")
    }


    override fun getStartSite(): Site {
        return Site.MANHWA
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.addJavascriptBlacklist(*JS_CONTENT_BLACKLIST)
        return client
    }
}
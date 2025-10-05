package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "cosplaytele.com"
private val GALLERY_FILTER = arrayOf("cosplaytele.com/[\\w-]+/$")
private val DIRTY_ELEMENTS = arrayOf<String>()
private val JS_CONTENT_BLACKLIST = arrayOf("mobilePopunderTargetBlankLinks")

class CosplayTeleActivityK : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.COSPLAYTELE
    }

    override fun allowMixedContent(): Boolean {
        return false
    }


    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*DIRTY_ELEMENTS)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        return client
    }
}
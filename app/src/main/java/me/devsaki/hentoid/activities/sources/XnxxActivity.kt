package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "xnxx.com"
private val GALLERY_FILTER = arrayOf("gallery/")

class XnxxActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.XNXX
    }

    override fun allowMixedContent(): Boolean {
        return false
    }


    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        return client
    }
}
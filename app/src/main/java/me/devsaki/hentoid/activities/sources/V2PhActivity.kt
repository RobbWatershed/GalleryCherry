package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "v2ph.com"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/album/[%\\w\\d_-]+")

class V2PhActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.V2PH
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
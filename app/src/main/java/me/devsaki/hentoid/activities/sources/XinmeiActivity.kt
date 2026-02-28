package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "xinmeitulu.com"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/photo/[%\\w\\d_-]+$")

class XinmeiActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.XINMEI
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
package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "babe.today"
private val GALLERY_FILTER =
    arrayOf("https://babe.today/[%\\w\\-_/]+$", "https://m.babe.today/[%\\w\\-_/]+$")

class BabeTodayActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.BABETODAY
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
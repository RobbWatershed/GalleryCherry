package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "everia.club"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/[\\d]{4}/[\\d]{2}/[\\d]{2}/[%\\w\\d_-]+(/[\\d]+){0,1}/$")

class EveriaActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.EVERIA
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
package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "fapello.com"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/[%\\w\\d_-]+/$"/*, "$DOMAIN_FILTER/[%\\w\\d_-]+/[\\d]+/$"*/)

class FapelloActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.FAPELLO
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
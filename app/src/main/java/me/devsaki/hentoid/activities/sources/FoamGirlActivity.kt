package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "foamgirl.net"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/[\\d_]+.html$")

class FoamGirlActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.FOAMGIRL
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
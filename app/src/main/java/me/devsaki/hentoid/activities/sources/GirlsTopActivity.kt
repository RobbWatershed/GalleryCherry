package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "girlstop.info"
private val GALLERY_FILTER = arrayOf("/psto.php\\?id=[\\d]+$")

class GirlsTopActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.GIRLSTOP
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
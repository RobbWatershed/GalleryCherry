package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "fapality.com"
private val GALLERY_FILTER = arrayOf("fapality.com/.*/[0-9]+/.*/\$")
private val DIRTY_ELEMENTS = arrayOf(".had\",\"a[rel^='nofollow noopener']")

class FapalityActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.FAPALITY
    }

    override fun allowMixedContent(): Boolean {
        return false
    }


    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*DIRTY_ELEMENTS)
        return client
    }
}
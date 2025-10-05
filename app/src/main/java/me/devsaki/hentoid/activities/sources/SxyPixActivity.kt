package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "sxypix.com"
private val GALLERY_FILTER = arrayOf("sxypix.com/w/.*")
private val DIRTY_ELEMENTS = arrayOf(".unit-main", ".unit-dt-blk", ".unit-mobblk")

class SxyPixActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.SXYPIX
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
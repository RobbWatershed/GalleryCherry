package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "pics-x.com"
private val GALLERY_FILTER = arrayOf("pics-x.com/gallery/[\\w_%\\-]+")
private val DIRTY_ELEMENTS = arrayOf(".unit-main", ".unit-dt-blk", ".unit-mobblk")

class PicsXActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.PICS_X
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
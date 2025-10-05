package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "jjgirls.com"
private val GALLERY_FILTER = arrayOf("jjgirls.com/.*/.*/.*/$")
private val DIRTY_ELEMENTS = arrayOf(".unit-main", ".unit-dt-blk", ".unit-mobblk")

class JjgirlsActivityK : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.JJGIRLS
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
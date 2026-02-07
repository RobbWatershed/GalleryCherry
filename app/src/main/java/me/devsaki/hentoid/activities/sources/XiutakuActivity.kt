package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "xiutaku.com"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/[\\d_]+")
private val REMOVABLE_ELEMENTS = arrayOf(".mod-ads-auto-title", ".mod-ads-auto-container")

class XiutakuActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.XIUTAKU
    }

    override fun allowMixedContent(): Boolean {
        return false
    }


    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        return client
    }
}
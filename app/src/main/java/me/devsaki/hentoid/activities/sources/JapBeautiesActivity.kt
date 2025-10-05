package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "japanesebeauties.one"
private val GALLERY_FILTER =
    arrayOf("japanesebeauties.one/[\\w_%\\-]+/[\\w_%\\-]+/[\\w_%\\-]+/([\\w_%\\-]+){0,1}$")
private val DIRTY_ELEMENTS = arrayOf(".unit-main", ".unit-dt-blk", ".unit-mobblk")

class JapBeautiesActivityK : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.JAPBEAUTIES
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
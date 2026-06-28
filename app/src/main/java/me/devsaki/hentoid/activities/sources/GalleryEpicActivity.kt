package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "galleryepic.com"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/[\\w_]+/[\\w_]+/[\\d_]+")
private val REMOVABLE_ELEMENTS = arrayOf(".mod-ads-auto-title", ".mod-ads-auto-container", ".mod-ads-title", ".mod-ads-container")

class GalleryEpicActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.GALLERYEPIC
    }

    override fun allowMixedContent(): Boolean {
        return false
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToJsUrlWhitelist("galleryepic.xyz")
        client.adBlocker.addToJsUrlWhitelist("galleryepic.com/_next/static/chunks/")
        return client
    }
}
package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "imhentai.xxx"
private val GALLERY_FILTER = arrayOf("//$DOMAIN_FILTER/gallery/")
private val MANAGED_URLS = arrayOf("m[0-9]+.$DOMAIN_FILTER")
private val REMOVABLE_ELEMENTS = arrayOf(".bblocktop", ".er_container", "#slider")

class ImhentaiActivity : BaseBrowserActivity() {

    override fun getStartSite(): Site {
        return Site.IMHENTAI
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addManagedUrls(*MANAGED_URLS)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }
}
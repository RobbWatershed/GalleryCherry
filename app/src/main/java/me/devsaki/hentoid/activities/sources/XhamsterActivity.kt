package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "xhamster.com"
private val GALLERY_FILTER = arrayOf("/gallery/(?!null)")
private val DIRTY_ELEMENTS = arrayOf("section.advertisement")

class XhamsterActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.XHAMSTER
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
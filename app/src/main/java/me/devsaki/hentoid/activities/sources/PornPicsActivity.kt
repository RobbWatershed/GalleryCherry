package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "pornpics.com"
private val GALLERY_FILTER = arrayOf("/galleries/[\\w-]+/$")

class PornPicsActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.PORNPICS
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
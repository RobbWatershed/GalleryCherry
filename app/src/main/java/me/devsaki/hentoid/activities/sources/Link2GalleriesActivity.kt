package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private val GALLERY_FILTER = emptyArray<String>()

class Link2GalleriesActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.LINK2GALLERIES
    }

    override fun allowMixedContent(): Boolean {
        return false
    }


    override fun createWebClient(): CustomWebViewClient {
        return CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
    }
}
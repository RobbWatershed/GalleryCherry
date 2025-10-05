package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private val GALLERY_FILTER = arrayOf(".*")

class PornPicGalleriesActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.PORNPICGALLERIES
    }

    override fun allowMixedContent(): Boolean {
        return false
    }


    override fun createWebClient(): CustomWebViewClient {
        //client.adBlocker.addUrlWhitelist(getStartSite().getUrl()); blocks too many things when the gallery filter is open and there's no JS grey list
        return CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
    }
}
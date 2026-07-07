package me.devsaki.hentoid.activities.sources

import android.webkit.WebResourceResponse
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.PawContent
import timber.log.Timber

const val PAW_DOMAIN_FILTER = "pawchive.pw"
private val GALLERY_FILTER = arrayOf(
    "$PAW_DOMAIN_FILTER/[\\w%\\-]+/user/[\\w\\-]+$",
    "$PAW_DOMAIN_FILTER/api/v1/[\\w%\\-]+/user/[\\w\\-]+$",
    "$PAW_DOMAIN_FILTER/[\\w%\\-]+/user/[\\w\\-]+/post/[\\d\\-]+$",
    "$PAW_DOMAIN_FILTER/api/v1/[\\w%\\-]+/user/[\\w\\-]+/post/[\\d\\-]+$"
)
private val BLOCKED_CONTENT = arrayOf("popunder")
private val REMOVABLE_ELEMENTS = arrayOf("section.advertisement")

class PawActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.PAWCHIVE
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = PawViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(PAW_DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(PAW_DOMAIN_FILTER)
        return client
    }

    private inner class PawViewClient(
        site: Site,
        filter: Array<String>,
        activity: BrowserActivity
    ) : CustomWebViewClient(site, filter, activity) {
        // We call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            if (analyzeForDownload || quickDownload) {
                activity?.onGalleryPageStarted()
                val contentParser: ContentParser = PawContent()

                lifecycleScope.launch {
                    try {
                        var content = withContext(Dispatchers.IO) {
                            contentParser.toContent(url)
                        }
                        content = super.processContent(content, url, quickDownload)
                        resConsumer?.onContentReady(content, quickDownload)
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            }
            return if (isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags() || isMarkQueued())
                super.parseResponse(
                    url,
                    requestHeaders,
                    analyzeForDownload = false,
                    quickDownload = false
                ) // Rewrite HTML
            else null
        }
    }
}
package me.devsaki.hentoid.activities.sources

import android.webkit.WebResourceResponse
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.KemonoContent
import timber.log.Timber

const val COOMER_DOMAIN_FILTER = "coomer.st"
private val GALLERY_FILTER = arrayOf(
    "$COOMER_DOMAIN_FILTER/[\\w%\\-]+/user/[\\w\\-]+$",
    "$COOMER_DOMAIN_FILTER/api/v1/[\\w%\\-]+/user/[\\w\\-]+/posts[-legacy]{0,1}$",
    "$COOMER_DOMAIN_FILTER/[\\w%\\-]+/user/[\\w\\-]+/post/[\\d\\-]+$",
    "$COOMER_DOMAIN_FILTER/api/v1/[\\w%\\-]+/user/[\\w\\-]+/post/[\\d\\-]+$"
)
private val BLOCKED_CONTENT =
    arrayOf("popunder", "AdParameters", "trackIFrameClick", "METRICS_COLLECTOR_URL")
private val REMOVABLE_ELEMENTS = arrayOf("section.advertisement")

class CoomerActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.COOMER
    }

    override fun allowMixedContent(): Boolean {
        return false
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = KemonoViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(COOMER_DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        BLOCKED_CONTENT.forEach {
            client.adBlocker.addJsContentBlacklist(it)
        }
        client.adBlocker.addToJsUrlWhitelist(COOMER_DOMAIN_FILTER)
        return client
    }

    private inner class KemonoViewClient(
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
                val contentParser: ContentParser = KemonoContent()

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
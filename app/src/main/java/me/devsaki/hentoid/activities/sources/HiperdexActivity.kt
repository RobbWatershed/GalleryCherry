package me.devsaki.hentoid.activities.sources

import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.HiperdexContent
import me.devsaki.hentoid.util.pause
import timber.log.Timber

class HiperdexActivity : BaseBrowserActivity() {
    companion object {
        private const val API_ENDPOINT = $$"$endpoint"
        const val DOMAIN_FILTER = "hiperdex.com"
        const val GALLERY_PATTERN = "//$DOMAIN_FILTER/[manga|comic]+/[%\\w\\-]+[/]{0,1}$"
        const val API_PATTERN = "//$DOMAIN_FILTER/api/trpc/[\\w\\.,]*$API_ENDPOINT[\\w\\.,]*\\?"

        private val GALLERY_FILTER =
            arrayOf(
                GALLERY_PATTERN,
                GALLERY_PATTERN.replace("$", "ch[%\\w]+-[0-9]+[%\\w\\-]*/$"),
                GALLERY_PATTERN.replace("$", "[0-9\\.]+")
//                API_PATTERN.replace(API_ENDPOINT, "series.bySlugWithGenres"),
//                API_PATTERN.replace(API_ENDPOINT, "reader.chapterPages"),
            )
        private val REMOVABLE_ELEMENTS = arrayOf(".c-ads")
        private val JS_CONTENT_BLACKLIST = arrayOf("'iframe'", "'plu_slider_frame'")
        private val BLOCKED_CONTENT = arrayOf(".cloudfront.net")
    }

    override fun getStartSite(): Site {
        return Site.HIPERDEX
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = HiperViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        return client
    }

    private inner class HiperViewClient(
        site: Site,
        filter: Array<String>,
        activity: BrowserActivity
    ) : CustomWebViewClient(site, filter, activity) {

        // Prevents using API calls as alternate page change triggers
        override fun doUpdateVisitedHistory(
            view: WebView?,
            url: String?,
            isReload: Boolean
        ) {
            super.doUpdateVisitedHistory(view, url, isReload)
            url ?: return
            if (!isReload) {
                scope.launch(Dispatchers.Default) {
                    pause(150)
                    parseResponse(url, null, isDownloadable(url), false)
                }
            }
        }

        // We call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            if (analyzeForDownload || quickDownload) {
                val contentParser: ContentParser = HiperdexContent()

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
            return if (
                isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags() || isMarkQueued()
                || (activity != null && activity.customCss.isNotEmpty())
            )
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
package me.devsaki.hentoid.activities.sources

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.parseParameters
import timber.log.Timber
import java.io.IOException

class NhentaiActivity : BaseBrowserActivity() {
    companion object {
        private const val DOMAIN_FILTER = "nhentai.net"
        const val FAVS_FILTER = "$DOMAIN_FILTER/favorites/"
        private val GALLERY_FILTER =
            arrayOf(
                "$DOMAIN_FILTER/g/[%0-9]+[/]{0,1}$",
                "$DOMAIN_FILTER/search/\\?q=[%0-9]+$",
                "$DOMAIN_FILTER/api/v2/galleries/[%0-9]+(\\?include=comments){0,1}$",
                FAVS_FILTER
            )
        private val RESULTS_FILTER = arrayOf(
            "//$DOMAIN_FILTER[/]*$",
            "//$DOMAIN_FILTER/\\?",
            "//$DOMAIN_FILTER/search/\\?",
            "//$DOMAIN_FILTER/(character|artist|parody|tag|group)/"
        )
        private val BLOCKED_CONTENT = arrayOf("popunder")
        private val REMOVABLE_ELEMENTS = arrayOf("section.advertisement", ".ad-wrapper")
    }

    override fun getStartSite(): Site {
        return Site.NHENTAI
    }


    override fun createWebClient(): CustomWebViewClient {
        val client = NhentaiWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.setResultsUrlPatterns(*RESULTS_FILTER)
        client.setResultUrlRewriter { resultsUri: Uri, page: Int ->
            rewriteResultsUrl(resultsUri, page)
        }
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }

    private fun rewriteResultsUrl(resultsUri: Uri, page: Int): String {
        val builder = resultsUri.buildUpon()
        val params = parseParameters(resultsUri).toMutableMap()
        params["page"] = page.toString() + ""
        builder.clearQuery()
        params.forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }
        return builder.toString()
    }

    private inner class NhentaiWebClient(
        site: Site,
        filter: Array<String>,
        activity: BrowserActivity
    ) : CustomWebViewClient(site, filter, activity) {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (isResultsPage(request.url.toString())) onNoResult()
            return super.shouldInterceptRequest(view, request)
        }

        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            val launchCode = url.toUri().lastPathSegment ?: ""
            val isApiGallery = url.contains("/api/v2/galleries/") && isNumeric(launchCode)
            return if (isApiGallery) {
                super.parseResponse("https://$DOMAIN_FILTER/g/$launchCode", requestHeaders, analyzeForDownload, quickDownload)
            } else super.parseResponse(url, requestHeaders, analyzeForDownload, quickDownload)
        }
/*
        private fun onData(url: String, launchCode: String) {
            Timber.d("onData $url $launchCode")
            try {
                lifecycleScope.launch {
                    var content = Content()
                    try {
                        withContext(Dispatchers.IO) {
//                            fetchBodyFast("$DOMAIN_FILTER/g/$launchCode", getStartSite()).first?.let { body ->
                            super.parseResponse("$DOMAIN_FILTER/g/$launchCode", requestHeaders, analyzeForDownload, quickDownload)
                            /*
                            content =
                                super.processContent(content, "$DOMAIN_FILTER/g/$launchCode", false)
                            resConsumer?.onContentReady(content, false)
                             */
//                            }
                        }
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }

 */
    }
}
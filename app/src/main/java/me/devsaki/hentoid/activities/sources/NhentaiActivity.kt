package me.devsaki.hentoid.activities.sources

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.sources.nhentai.NHentaiContentMetadata
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.parseParameters
import timber.log.Timber
import java.io.IOException

class NhentaiActivity : BaseBrowserActivity() {
    companion object {
        const val DOMAIN_FILTER = "nhentai.net"
        const val FAVS_FILTER = "$DOMAIN_FILTER/favorites/"
        private val GALLERY_FILTER =
            arrayOf(
                "$DOMAIN_FILTER/g/[%0-9]+[/]{0,1}$",
                "$DOMAIN_FILTER/search/\\?q=[%0-9]+$",
//                "$DOMAIN_FILTER/api/v2/galleries/[%0-9]+(\\?[%=\\w\\-]+){0,1}$",
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

        // Init fetch handler here for convenience
        fetchResponseHandler = { url, body -> client.onData(url, body) }

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

        fun onData(url: String, responseBody: String) {
            val launchCode = url.toUri().lastPathSegment ?: ""
            val isApiGallery = url.contains("/api/v2/galleries/") && isNumeric(launchCode)
            if (!isApiGallery) return

            Timber.d("onData $url")
            try {
                lifecycleScope.launch {
                    var content = Content()
                    try {
                        withContext(Dispatchers.IO) {
                            NHentaiContentMetadata.updateFromData(
                                responseBody,
                                content,
                                updateImages = true
                            )
                            content = super.processContent(content, content.galleryUrl, false)
                            resConsumer?.onContentReady(content, false)
                        }
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }
    }
}
package me.devsaki.hentoid.activities.sources

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.oauth2.Oauth2AccessToken
import me.devsaki.hentoid.json.sources.RedditUser
import me.devsaki.hentoid.retrofit.RedditOAuthApiServer
import me.devsaki.hentoid.retrofit.RedditPublicApiServer
import me.devsaki.hentoid.util.OAuthSessionManager
import timber.log.Timber
import java.time.Instant

private const val DOMAIN_FILTER = "reddit.com"

// regular posts : //"https://gateway.reddit.com/desktopapi/v1/postcomments"; => XML received when browsing posts
private val GALLERY_FILTER = arrayOf("§§§")
private const val OAUTH_REDIRECT_URL = "https://github.com/RobbWatershed/GalleryCherry"

class RedditActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.REDDIT
    }

    override fun allowMixedContent(): Boolean {
        return true
    }

    override fun createWebClient(): CustomWebViewClient {
        val client: CustomWebViewClient = RedditWebViewClient(GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        return client
    }

    private fun showLoadingDialog() {
        val bar = ProgressBar(this)
        bar.isIndeterminate = true

        val builder = MaterialAlertDialogBuilder(this)
        builder.setMessage(R.string.please_wait)
            .setView(bar)
            .setCancelable(false)
            .create().show()
    }

    private fun launchRedditActivity() {
        val intent = Intent(this, RedditLaunchActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent, null)

        finish()
    }


    private inner class RedditWebViewClient(
        filteredUrl: Array<String>,
        activity: BaseBrowserActivity
    ) : CustomWebViewClient(Site.REDDIT, filteredUrl, activity) {
        init {
            preventAugmentedBrowser = true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return shouldOverrideUrlLoading(request.url)
        }

        fun shouldOverrideUrlLoading(uri: Uri): Boolean {
            if (uri.toString().startsWith(OAUTH_REDIRECT_URL)) {
                processIntent(uri)
                return true
            }
            return false
        }

        fun processIntent(data: Uri) {
            Timber.d("Uri: %s", data)

            if (data.getQueryParameter("error") != null) {
                val error = data.getQueryParameter("error")
                Timber.e("An error has occurred : %s", error)
            } else {
                val state = data.getQueryParameter("state")
                val session = OAuthSessionManager.getSessionByState(state)
                if (session != null) {
                    val code = data.getQueryParameter("code") ?: ""
                    getAccessToken(session, code)
                } else Timber.e("Session has not been initialized")
            }
        }

        fun getAccessToken(session: OAuthSessionManager.OauthSession, code: String) {
            val authString = session.clientId + ":"
            val encodedAuthString = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)

            showLoadingDialog()

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        RedditPublicApiServer.API.getAccessToken(
                            code,
                            session.redirectUri,
                            "authorization_code",
                            "Basic $encodedAuthString"
                        ).execute().body()?.let { token ->
                            onTokenSuccess(token, session)
                        }
                    } catch (t: Throwable) {
                        onTokenError(t)
                    }
                }
            }
        }

        private fun onTokenSuccess(
            token: Oauth2AccessToken,
            session: OAuthSessionManager.OauthSession
        ) {
            Timber.i("Reddit OAuth response received")
            session.accessToken = token.accessToken
            session.refreshToken = token.refreshToken
            session.expiry = Instant.now().plusSeconds(token.expiryDelaySeconds)

            try {
                RedditOAuthApiServer.API.getUser("bearer " + token.accessToken).execute().body()
                    ?.let {
                        onUserSuccess(it, session)
                    }
            } catch (t: Throwable) {
                onUserError(t)
            }
        }

        fun onTokenError(t: Throwable?) {
            Timber.e(t, "Error fetching Reddit OAuth response")
        }

        fun onUserSuccess(user: RedditUser, session: OAuthSessionManager.OauthSession) {
            Timber.i("Reddit user information received")
            session.userName = user.name

            launchRedditActivity()
        }

        fun onUserError(t: Throwable?) {
            Timber.e(t, "Error fetching Reddit user information")
        }
    }
}
package me.devsaki.hentoid.activities.sources;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.oauth2.Oauth2AccessToken;
import me.devsaki.hentoid.json.sources.RedditUser;
import me.devsaki.hentoid.retrofit.RedditOAuthApiServer;
import me.devsaki.hentoid.retrofit.RedditPublicApiServer;
import me.devsaki.hentoid.util.OauthSessionManager;
import timber.log.Timber;

import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;

import java.time.Instant;

public class RedditActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "reddit.com";
    private static final String[] GALLERY_FILTER = {"§§§"}; // regular posts : //"https://gateway.reddit.com/desktopapi/v1/postcomments"; => XML received when browsing posts

    private static final String OAUTH_REDIRECT_URL = "https://github.com/RobbWatershed/GalleryCherry";


    Site getStartSite() {
        return Site.REDDIT;
    }

    @Override
    boolean allowMixedContent() {
        return true;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new RedditWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private void showLoadingDialog() {
        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setMessage(R.string.please_wait)
                .setView(bar)
                .setCancelable(false)
                .create().show();
    }

    private void launchRedditActivity() {
        Intent intent = new Intent(this, RedditLaunchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ContextCompat.startActivity(this, intent, null);

        finish();
    }


    private class RedditWebViewClient extends CustomWebViewClient {
        RedditWebViewClient(String[] filteredUrl, CustomWebActivity activity) {
            super(Site.REDDIT, filteredUrl, activity);
            preventAugmentedBrowser = true;
        }

        @Override
        @Deprecated
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return shouldOverrideUrlLoading(Uri.parse(url));
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return shouldOverrideUrlLoading(request.getUrl());
        }

        private boolean shouldOverrideUrlLoading(Uri uri) {
            if (uri.toString().startsWith(OAUTH_REDIRECT_URL)) {
                processIntent(uri);
                return true;
            }
            return false;
        }

        private void processIntent(Uri data) {
            Timber.d("Uri: %s", data);

            if (data.getQueryParameter("error") != null) {
                String error = data.getQueryParameter("error");
                Timber.e("An error has occurred : %s", error);
            } else {
                String state = data.getQueryParameter("state");
                OauthSessionManager.OauthSession session = OauthSessionManager.getInstance().getSessionByState(state);
                if (session != null) {
                    String code = data.getQueryParameter("code");
                    getAccessToken(session, code);
                } else Timber.e("Session has not been initialized");
            }
        }

        private void getAccessToken(OauthSessionManager.OauthSession session, String code) {
            String authString = session.getClientId() + ":";
            String encodedAuthString = Base64.encodeToString(authString.getBytes(), Base64.NO_WRAP);

            showLoadingDialog();

            compositeDisposable.add(
                    RedditPublicApiServer.API.getAccessToken(code, session.getRedirectUri(), "authorization_code", "Basic " + encodedAuthString)
                            .observeOn(mainThread())
                            .subscribe(t -> onTokenSuccess(t, session),
                                    this::onTokenError)
            );
        }

        private void onTokenSuccess(Oauth2AccessToken token, OauthSessionManager.OauthSession session) {
            Timber.i("Reddit OAuth response received");
            session.setAccessToken(token.getAccessToken());
            session.setRefreshToken(token.getRefreshToken());
            session.setExpiry(Instant.now().plusSeconds(token.getExpiryDelaySeconds()));

            compositeDisposable.add(
                    RedditOAuthApiServer.API.getUser("bearer " + token.getAccessToken())
                            .observeOn(mainThread())
                            .subscribe(t -> onUserSuccess(t, session),
                                    this::onUserError)
            );
        }

        private void onTokenError(Throwable t) {
            Timber.e(t, "Error fetching Reddit OAuth response");
        }

        private void onUserSuccess(RedditUser user, OauthSessionManager.OauthSession session) {
            Timber.i("Reddit user information received");
            session.setUserName(user.getName());

            launchRedditActivity();
        }

        private void onUserError(Throwable t) {
            Timber.e(t, "Error fetching Reddit user information");
        }
    }
}

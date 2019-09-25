package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.core.content.ContextCompat;

import org.threeten.bp.Instant;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.activities.sources.RedditLaunchActivity;
import me.devsaki.hentoid.model.Oauth2AccessToken;
import me.devsaki.hentoid.model.RedditUser;
import me.devsaki.hentoid.retrofit.RedditOAuthApiServer;
import me.devsaki.hentoid.retrofit.RedditPublicApiServer;
import me.devsaki.hentoid.util.OauthManager;
import timber.log.Timber;

import static android.content.Intent.ACTION_VIEW;
import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;

/**
 * Created by Robb on 09/2019
 * Responsible for resolving intents coming from the 1st step of OAuth2.0 flow
 */
public class OauthIntentActivity extends BaseActivity {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        if (ACTION_VIEW.equals(action) && data != null) {
            processIntent(data);
        } else {
            Timber.d("Unrecognized intent. Cannot process.");
        }
    }

    @Override
    public void onDestroy() {
        compositeDisposable.clear();
        super.onDestroy();
    }

    private void processIntent(Uri data) {
        Timber.d("Uri: %s", data);

        if (data.getQueryParameter("error") != null) {
            String error = data.getQueryParameter("error");
            Timber.e("An error has occurred : %s", error);
        } else {
            String state = data.getQueryParameter("state");
            OauthManager.OauthSession session = OauthManager.getInstance().getSessionByState(state);
            if (session != null) {
                String code = data.getQueryParameter("code");
                getAccessToken(session, code);
            } else Timber.e("Session has not been initialized");
        }
    }

    private void getAccessToken(OauthManager.OauthSession session, String code) {
        String authString = session.getClientId() + ":";
        String encodedAuthString = Base64.encodeToString(authString.getBytes(), Base64.NO_WRAP);

        compositeDisposable.add(
                RedditPublicApiServer.API.getAccessToken(code, session.getRedirectUri(), "authorization_code", "Basic " + encodedAuthString)
                        .observeOn(mainThread())
                        .subscribe(t -> onTokenSuccess(t, session),
                                this::onTokenError)
        );
    }

    private void onTokenSuccess(Oauth2AccessToken token, OauthManager.OauthSession session) {
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

    private void onUserSuccess(RedditUser user, OauthManager.OauthSession session) {
        Timber.i("Reddit user information received");
        session.setUserName(user.getName());

        launchRedditActivity();
    }

    private void onUserError(Throwable t) {
        Timber.e(t, "Error fetching Reddit user information");
    }

    private void launchRedditActivity() {
        Intent intent = new Intent(this, RedditLaunchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ContextCompat.startActivity(this, intent, null);

        finish();
    }

}

package me.devsaki.hentoid.activities.sources;

import android.os.Bundle;

import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.fragments.downloads.LandingHistoryFragment;
import me.devsaki.hentoid.fragments.downloads.RedditLauncherDialogFragment;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.OauthManager;

/**
 * Created by Robb on 09/2019
 * Landing page history launcher for Reddit
 */
public class RedditLaunchActivity extends BaseActivity implements LandingHistoryFragment.Parent {

    private static final String AUTH_URL =
            "https://www.reddit.com/api/v1/authorize.compact?client_id=%s"
                    + "&response_type=code"
                    + "&state=%s"
                    + "&redirect_uri=%s"
                    + "&duration=permanent"
                    + "&scope=%s";

    private static final String CLIENT_ID = "SYu446X8u5hTTQ";

    private static final String REDIRECT_URI = "https://github.com/RobbWatershed/GalleryCherry";

    private static final String SCOPE = "history";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RedditLauncherDialogFragment.invoke(getSupportFragmentManager());
    }

    @Override
    public void goToUrl(String url) {
        OauthManager.OauthSession session = OauthManager.getInstance().addSession(REDIRECT_URI);
        session.setState(Double.toString(Math.random()));
        session.setClientId(CLIENT_ID);
        session.setRedirectUri(REDIRECT_URI);
        session.setTargetUrl(url);

        String authUrl = String.format(AUTH_URL, CLIENT_ID, session.getState(), REDIRECT_URI, SCOPE);
        Helper.openUrl(this, authUrl);
    }
}
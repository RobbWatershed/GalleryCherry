package me.devsaki.hentoid.activities.sources;

import android.view.WindowManager;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Preferences;

public class RedditActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "reddit.com";
    private static final String GALLERY_FILTER = ""; // regular posts : //"https://gateway.reddit.com/desktopapi/v1/postcomments"; => XML received when browsing posts

    Site getStartSite() {
        return Site.REDDIT;
    }

    @Override
    boolean allowMixedContent() {
        return true;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
}

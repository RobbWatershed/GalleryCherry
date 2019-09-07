package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class RedditActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "reddit.com";
    private static final String GALLERY_FILTER = "/user/.+/saved"; // regular posts : /r/*/comments/*/*/

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
}

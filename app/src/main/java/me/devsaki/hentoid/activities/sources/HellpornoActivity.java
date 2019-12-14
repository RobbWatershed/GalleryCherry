package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class HellpornoActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hellporno.com";
    private static final String[] GALLERY_FILTER = {"/albums/....+"};

    Site getStartSite() {
        return Site.HELLPORNO;
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

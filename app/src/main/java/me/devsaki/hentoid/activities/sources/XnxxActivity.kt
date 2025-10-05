package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class XnxxActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "xnxx.com";
    private static final String[] GALLERY_FILTER = {"gallery/"};

    Site getStartSite() {
        return Site.XNXX;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}

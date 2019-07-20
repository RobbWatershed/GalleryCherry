package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class JpegworldActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "jpegworld.com";
    private static final String GALLERY_FILTER = "/galleries/";

    Site getStartSite() {
        return Site.JPEGWORLD;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }
}

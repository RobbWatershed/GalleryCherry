package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class BabeTodayActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "babe.today";
    private static final String[] GALLERY_FILTER = {"https://babe.today/.+$"};

    Site getStartSite() {
        return Site.BABETODAY;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}

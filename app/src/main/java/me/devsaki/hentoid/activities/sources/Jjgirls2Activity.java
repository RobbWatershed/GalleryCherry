package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class Jjgirls2Activity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "jjgirls.com";
    private static final String[] GALLERY_FILTER = {"jjgirls.com/pornpics/.+$"};

    Site getStartSite() {
        return Site.JJGIRLS2;
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

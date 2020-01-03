package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class JjgirlsActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "jjgirls.com";
    private static final String[] GALLERY_FILTER = {"jjgirls.com/.*/.*/.*/$"};

    Site getStartSite() {
        return Site.JJGIRLS;
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

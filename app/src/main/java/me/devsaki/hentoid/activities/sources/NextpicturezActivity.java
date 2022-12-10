package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class NextpicturezActivity extends BaseWebActivity {

    private static final String[] DOMAIN_FILTERS = {"nextpicturez.com", "young-whores.net"};
    private static final String[] GALLERY_FILTER = {"/gallery/"};

    Site getStartSite() {
        return Site.NEXTPICTUREZ;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTERS);
        return client;
    }
}

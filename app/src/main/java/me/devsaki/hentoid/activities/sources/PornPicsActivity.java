package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class PornPicsActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "pornpics.com";
    private static final String GALLERY_FILTER[] = {"/galleries/[\\w-]+/$"};

    Site getStartSite() {
        return Site.PORNPICS;
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

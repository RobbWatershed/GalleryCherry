package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class HellpornoActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hellporno.com";
    private static final String[] GALLERY_FILTER = {"/albums/....+"};
    private static final String[] DIRTY_ELEMENTS = {".pignr"};

    Site getStartSite() {
        return Site.HELLPORNO;
    }

    @Override
    boolean allowMixedContent() {
        return true;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addDirtyElements(DIRTY_ELEMENTS);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}

package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class AsianSisterActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "asiansister.com";
    private static final String[] GALLERY_FILTER = {"asiansister.com/view_.*$"};
//    private static final String[] DIRTY_ELEMENTS = {".had"};

    Site getStartSite() {
        return Site.ASIANSISTER;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
//        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}

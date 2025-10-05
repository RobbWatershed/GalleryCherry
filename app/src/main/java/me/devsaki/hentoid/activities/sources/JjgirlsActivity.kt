package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class JjgirlsActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "jjgirls.com";
    private static final String[] DIRTY_ELEMENTS = {".unit-main", ".unit-dt-blk", ".unit-mobblk"};
    private static final String[] GALLERY_FILTER = {"jjgirls.com/.*/.*/.*/$"};

    Site getStartSite() {
        return Site.JJGIRLS;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(DIRTY_ELEMENTS);
        return client;
    }
}

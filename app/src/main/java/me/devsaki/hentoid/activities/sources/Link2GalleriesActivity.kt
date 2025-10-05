package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class Link2GalleriesActivity extends BaseWebActivity {

    private static final String[] GALLERY_FILTER = {".*"};

    Site getStartSite() {
        return Site.LINK2GALLERIES;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient createWebClient() {
        return new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
    }
}

package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class JpegworldActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "jpegworld.com";
    private static final String[] GALLERY_FILTER = {"/galleries/", "//comics.8muses.com/comics/album/"};
//    private static final String[] DIRTY_ELEMENTS = {".c-tile:not([href])"}; // <-- even when removing empty tiles, ads are generated and force-inserted by the ad JS (!)

    Site getStartSite() {
        return Site.JPEGWORLD;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
//        addDirtyElements(DIRTY_ELEMENTS);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }
}

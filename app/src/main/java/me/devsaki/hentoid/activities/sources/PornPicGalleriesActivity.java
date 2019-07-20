package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class PornPicGalleriesActivity extends BaseWebActivity {

    private static final String GALLERY_FILTER = ".*";

    Site getStartSite() {
        return Site.PORNPICGALLERIES;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        return new CustomWebViewClient(GALLERY_FILTER, this);
    }
}

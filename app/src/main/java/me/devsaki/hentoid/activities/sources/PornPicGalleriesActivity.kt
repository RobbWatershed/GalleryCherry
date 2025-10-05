package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class PornPicGalleriesActivity extends BaseWebActivity {

    private static final String[] GALLERY_FILTER = {".*"};

    Site getStartSite() {
        return Site.PORNPICGALLERIES;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        //client.adBlocker.addUrlWhitelist(getStartSite().getUrl()); blocks too many things when the gallery filter is open and there's no JS grey list
        return client;
    }
}

package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class CosplayTeleActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "cosplaytele.com";
    private static final String[] DIRTY_ELEMENTS = {};
    private static final String[] GALLERY_FILTER = {"cosplaytele.com/[\\w-]+/$"};
    private static final String[] JS_CONTENT_BLACKLIST = {"mobilePopunderTargetBlankLinks"};

    Site getStartSite() {
        return Site.COSPLAYTELE;
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
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        return client;
    }
}

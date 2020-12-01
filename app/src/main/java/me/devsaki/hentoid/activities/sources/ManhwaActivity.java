package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class ManhwaActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "manhwahentai.me";
    private static final String[] GALLERY_FILTER = {"//manhwahentai.me/[\\w\\-]+/[\\w\\-]+/$"};
    private static final String[] DIRTY_ELEMENTS = {".c-ads"};
    private static final String[] BLOCKED_CONTENT = {".cloudfront.net"};

    Site getStartSite() {
        return Site.MANHWA;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addContentBlockFilter(BLOCKED_CONTENT);
        addDirtyElements(DIRTY_ELEMENTS);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}

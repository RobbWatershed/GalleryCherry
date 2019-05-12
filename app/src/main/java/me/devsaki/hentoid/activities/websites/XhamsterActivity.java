package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.XhamsterGalleryServer;
import timber.log.Timber;

/**
 * Created by Robb on 01/2019
 * Implements Xhamster source
 */
public class XhamsterActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "xhamster.com";
    private static final String GALLERY_FILTER = "/gallery/";

    Site getStartSite() {
        return Site.XHAMSTER;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new XhamsterWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class XhamsterWebViewClient extends CustomWebViewClient {

        XhamsterWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] galleryUrlParts = url.split("/");
            String page, id;
            if (galleryUrlParts[galleryUrlParts.length - 1].length() < 4) {
                page = galleryUrlParts[galleryUrlParts.length - 1];
                id = galleryUrlParts[galleryUrlParts.length - 2];
            } else {
                page = "1";
                id = galleryUrlParts[galleryUrlParts.length - 1];
            }
            compositeDisposable.add(XhamsterGalleryServer.API.getGalleryMetadata(id, page)
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content for page %s", url);
                                listener.onResultFailed("");
                            })
            );
        }
    }
}

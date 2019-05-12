package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.PornPicsGalleryServer;
import timber.log.Timber;

/**
 * Created by Robb on 01/2019
 * Implements PornPics source
 */
public class PornPicsActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "pornpics.com";
    private static final String GALLERY_FILTER = "/galleries/";

    Site getStartSite() {
        return Site.PORNPICS;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new PornPicsWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class PornPicsWebViewClient extends CustomWebViewClient {

        PornPicsWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] parts = url.split("/");

            compositeDisposable.add(PornPicsGalleryServer.API.getGalleryMetadata(parts[parts.length - 1])
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content for page %s", url);
                                listener.onResultFailed("");
                            })
            );
        }
    }
}

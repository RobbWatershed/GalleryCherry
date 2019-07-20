package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.NextpicturezGalleryServer;
import timber.log.Timber;

/**
 * Created by Robb on 01/2019
 * Implements PornPics source
 */
public class NextpicturezActivity extends BaseWebActivity {

    private static final String[] DOMAIN_FILTERS = {"nextpicturez.com", "young-whores.net"};
    private static final String GALLERY_FILTER = "/gallery/";

    Site getStartSite() {
        return Site.NEXTPICTUREZ;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new PornPicsWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTERS);
        return client;
    }

    private class PornPicsWebViewClient extends CustomWebViewClient {

        PornPicsWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] parts = url.split("/");

            compositeDisposable.add(NextpicturezGalleryServer.API.getGalleryMetadata(parts[parts.length - 2], parts[parts.length - 1])
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content for page %s", url);
                                listener.onResultFailed("");
                            })
            );
        }
    }
}

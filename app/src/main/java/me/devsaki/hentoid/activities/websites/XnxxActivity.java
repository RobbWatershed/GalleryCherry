package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.XnxxGalleryServer;
import timber.log.Timber;

/**
 * Created by Robb on 01/2019
 * Implements XNXX source
 */
public class XnxxActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "xnxx.com";
    private static final String GALLERY_FILTER = "gallery/";

    Site getStartSite() {
        return Site.XNXX;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new XnxxWebViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class XnxxWebViewClient extends CustomWebViewClient {

        XnxxWebViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] parts = url.split("/");
            String page;
            int nameIndex;
            if (parts[parts.length - 1].length() > 3) {
                nameIndex = parts.length - 1;
                page = "1";
            } else {
                nameIndex = parts.length - 2;
                page = parts[parts.length - 1];
            }

            compositeDisposable.add(XnxxGalleryServer.API.getGalleryMetadata(parts[nameIndex - 2], parts[nameIndex - 1], parts[nameIndex], page)
                    .subscribe(
                            metadata -> {
                                Content content = metadata.toContent();
                                content.setUrl(url.substring((getStartSite().getUrl() + GALLERY_FILTER).length()));
                                listener.onResultReady(content, 1);
                            }, throwable -> {
                                Timber.e(throwable, "Error parsing content for page %s", url);
                                listener.onResultFailed("");
                            })
            );
        }
    }
}

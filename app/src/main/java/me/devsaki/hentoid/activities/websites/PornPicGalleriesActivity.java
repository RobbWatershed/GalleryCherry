package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.GenericServer;
import timber.log.Timber;

/**
 * Created by Robb on 01/2019
 * Implements PornPicGalleries source
 */
public class PornPicGalleriesActivity extends BaseWebActivity {

    private static final String GALLERY_FILTER = ".*";

    Site getStartSite() {
        return Site.PORNPICGALLERIES;
    }

    @Override
    boolean allowMixedContent() {
        return true;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        return new PornPicsGalleriesWebViewClient(GALLERY_FILTER, getStartSite(), this);
    }

    private class PornPicsGalleriesWebViewClient extends CustomWebViewClient {

        PornPicsGalleriesWebViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, startSite, listener);
        }

        private WebResourceResponse shouldInterceptRequestCommon(@NonNull WebView view,
                                                                 @NonNull String url) {
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.endsWith("pornpicgalleries.com") || url.endsWith("pornpicgalleries.com/") || url.contains("pornpicgalleries.com/tag/")) {
                return new WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        loadAndReplace(url, "target=\"_blank\"", "")
                );
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            return shouldInterceptRequestCommon(view, url);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            return shouldInterceptRequestCommon(view, request.getUrl().toString());
        }

        @Override
        protected void onGalleryFound(String url) {
            compositeDisposable.add(GenericServer.API.getGalleryMetadata(url)
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content for page %s", url);
                                listener.onResultFailed("");
                            })
            );
        }
    }
}

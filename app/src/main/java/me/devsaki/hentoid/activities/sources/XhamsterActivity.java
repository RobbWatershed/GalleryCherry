package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

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
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
/*

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

        // We keep calling the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> headers) {
            String[] galleryUrlParts = urlStr.split("/");
            String page, id;
            if (galleryUrlParts[galleryUrlParts.length - 1].length() < 4) {
                page = galleryUrlParts[galleryUrlParts.length - 1];
                id = galleryUrlParts[galleryUrlParts.length - 2];
            } else {
                page = "1";
                id = galleryUrlParts[galleryUrlParts.length - 1];
            }

            List<Pair<String, String>> headersList = new ArrayList<>();
            String cookie = CookieManager.getInstance().getCookie(urlStr);
            if (cookie != null) headersList.add(new Pair<>("cookie", cookie));

            compositeDisposable.add(XhamsterGalleryServer.API.getGalleryMetadata(id, page)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata -> processContent(metadata.toContent(urlStr), headersList),
                            throwable -> {
                                Timber.e(throwable, "Error parsing content for page %s", urlStr);
                                isHtmlLoaded = true;
                                listener.onResultFailed("");
                            })
            );
            return null;
        }
    }
    */
}

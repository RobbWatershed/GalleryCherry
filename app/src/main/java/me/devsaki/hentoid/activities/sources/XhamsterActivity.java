package me.devsaki.hentoid.activities.sources;

import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.XhamsterGalleryServer;
import timber.log.Timber;

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
}

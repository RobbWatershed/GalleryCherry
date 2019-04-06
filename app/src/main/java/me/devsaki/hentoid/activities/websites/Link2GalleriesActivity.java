package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.GenericServer;
import timber.log.Timber;

/**
 * Created by Robb on 01/2019
 * Implements PornPicGalleries source
 */
public class Link2GalleriesActivity extends BaseWebActivity {

    private static final String GALLERY_FILTER = ".*";

    Site getStartSite() {
        return Site.LINK2GALLERIES;
    }

    @Override
    boolean allowMixedContent() {
        return false;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        return new PornPicsGalleriesWebViewClient(GALLERY_FILTER, getStartSite(), this);
    }

    private class PornPicsGalleriesWebViewClient extends CustomWebViewClient {

        PornPicsGalleriesWebViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected void onGalleryFound(String url) {

            compositeDisposable.add(GenericServer.API.getGalleryMetadata(url)
                    .subscribe(
                            metadata -> {
                                Content content = metadata.toContent();

                                content.setSite(Site.LINK2GALLERIES);

                                if (content.getUrl() != null && content.getUrl().isEmpty()) {
                                    content.setUrl(url);
                                    String urlHost = url.substring(0, url.indexOf("/", url.indexOf("://") + 3));
                                    String urlLocation = url.substring(0, url.lastIndexOf("/") + 1);
                                    for (ImageFile img : content.getImageFiles()) {
                                        if (!img.getUrl().startsWith("http")) {
                                            if (img.getUrl().startsWith("/"))
                                                img.setUrl(urlHost + img.getUrl());
                                            else
                                                img.setUrl(urlLocation + img.getUrl());
                                        }
                                    }
                                }

                                listener.onResultReady(content, 1);
                            }, throwable -> {
                                Timber.e(throwable, "Error parsing content for page %s", url);
                                listener.onResultFailed("");
                            })
            );
        }
    }
}

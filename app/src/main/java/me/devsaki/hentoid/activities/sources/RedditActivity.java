package me.devsaki.hentoid.activities.sources;

import android.util.Pair;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.ResultListener;

public class RedditActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "reddit.com";
    private static final String GALLERY_FILTER = "/user/.+/saved"; // regular posts : /r/*/comments/*/*/

    Site getStartSite() {
        return Site.REDDIT;
    }

    @Override
    boolean allowMixedContent() {
        return true;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new RedditWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class RedditWebViewClient extends CustomWebViewClient {

        RedditWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        // Reddit has one single book that is updated incrementally with every download
        @Override
        void processContent(@Nonnull Content content, @Nonnull List<Pair<String, String>> headersList) {
            // Get the reddit book
            Content redditBook = db.selectContentBySourceAndUrl(Site.REDDIT, content.getUrl());
            if (redditBook != null)
            {
                // Remove the images that are already contained in the Reddit book
                List<ImageFile> newImages = content.getImageFiles();
                List<ImageFile> existingImages = redditBook.getImageFiles();
                if (newImages != null && existingImages != null) {
                    newImages.removeAll(redditBook.getImageFiles());
                    redditBook.addImageFiles(newImages);
                }
                content = redditBook;
            }

            super.processContent(content, headersList);
        }
    }
}

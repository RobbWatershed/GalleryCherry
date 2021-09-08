package me.devsaki.hentoid.parsers.images;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import timber.log.Timber;

public abstract class BaseImageListParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();
    protected boolean processHalted = false;

    private static final int TIMEOUT = 30000; // 30 seconds

    protected abstract List<String> parseImages(@NonNull Content content) throws Exception;

    @Nullable
    Document getOnlineDocument(HttpUrl url, Interceptor interceptor) throws IOException {
        OkHttpClient okHttp;
        if (interceptor != null) okHttp = OkHttpClientSingleton.getInstance(TIMEOUT, interceptor);
        else okHttp = OkHttpClientSingleton.getInstance(TIMEOUT);

        Request request = new Request.Builder().url(url).get().build();
        ResponseBody body = okHttp.newCall(request).execute().body();
        if (body != null) {
            return Jsoup.parse(body.string());
        }
        return null;
    }

    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        String readerUrl = content.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);
        content.populateUniqueSiteId();

        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            List<String> imgUrls = parseImages(content);
            result = ParseHelper.urlsToImageFiles(imgUrls, content.getCoverImageUrl(), StatusContent.SAVED, null);
            ParseHelper.setDownloadParams(result, content.getSite().getUrl());
        } finally {
            EventBus.getDefault().unregister(this);
        }

        Timber.d("%s", result);

        return result;
    }

    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) {
        ImageFile img = ImageFile.fromImageUrl(order, url, StatusContent.SAVED, maxPages);
        if (chapter != null) img.setChapter(chapter);
        return Optional.of(img);
    }

    public ImmutablePair<String, Optional<String>> parseImagePage(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        throw new NotImplementedException();
    }

    void progressStart(long contentId, int maxSteps) {
        progress.start(contentId, maxSteps);
    }

    void progressPlus() {
        progress.advance();
    }

    void progressComplete() {
        progress.complete();
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType) {
            case DownloadEvent.EV_PAUSE:
            case DownloadEvent.EV_CANCEL:
            case DownloadEvent.EV_SKIP:
                processHalted = true;
                break;
            default:
                // Other events aren't handled here
        }
    }
}

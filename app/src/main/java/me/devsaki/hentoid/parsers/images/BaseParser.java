package me.devsaki.hentoid.parsers.images;

import android.webkit.URLUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.annimon.stream.Optional;
import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import timber.log.Timber;

public abstract class BaseParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    private static final int TIMEOUT = 30000; // 30 seconds

    protected abstract List<String> parseImages(@NonNull Content content) throws Exception;

    @Nullable
    Document getOnlineDocument(HttpUrl url) throws IOException {
        return getOnlineDocument(url, null);
    }

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

        List<String> imgUrls = parseImages(content);
        List<ImageFile> images = ParseHelper.urlsToImageFiles(imgUrls, content.getCoverImageUrl(), StatusContent.SAVED);

        Timber.d("%s", images);

        return images;
    }

    public Optional<ImageFile> parseBackupUrl(@NonNull String url, int order, int maxPages) {
        return Optional.of(new ImageFile(order, url, StatusContent.SAVED, maxPages));
    }

    void progressStart(int maxSteps) {
        progress.start(maxSteps);
    }

    void progressPlus() {
        progress.advance();
    }

    void progressComplete() {
        progress.complete();
    }
}

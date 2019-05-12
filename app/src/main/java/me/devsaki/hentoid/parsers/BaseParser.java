package me.devsaki.hentoid.parsers;

import android.support.annotation.Nullable;
import android.webkit.URLUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import timber.log.Timber;

public abstract class BaseParser implements ContentParser {

    private static final int TIMEOUT = 30000; // 30 seconds

    protected abstract List<String> parseImages(Content content) throws Exception;

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

    public List<ImageFile> parseImageList(Content content) throws Exception {
        String readerUrl = content.getReaderUrl();
        List<ImageFile> images = Collections.emptyList();

        if (!URLUtil.isValidUrl(readerUrl)) {
            throw new Exception("Invalid gallery URL : " + readerUrl);
        }
        Timber.d("Gallery URL: %s", readerUrl);

        List<String> imgUrls = parseImages(content);
        images = ParseHelper.urlsToImageFiles(imgUrls);

        Timber.d("%s", images);

        return images;
    }

}

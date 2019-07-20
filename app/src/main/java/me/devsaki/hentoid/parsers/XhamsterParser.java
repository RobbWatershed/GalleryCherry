package me.devsaki.hentoid.parsers;

import com.google.gson.Gson;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.parsers.content.XhamsterGalleryContent;
import me.devsaki.hentoid.parsers.content.XhamsterGalleryQuery;
import me.devsaki.hentoid.util.Consts;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;

/**
 * Created by avluis on 07/26/2016.
 * Handles parsing of content from Xhamster
 */
public class XhamsterParser extends BaseParser {

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        Gson gson = new Gson();

        for (int i = 0; i < Math.ceil(content.getQtyPages() / 16.0); i++) {
            XhamsterGalleryQuery query = new XhamsterGalleryQuery(content.getUniqueSiteId(), i + 1);

            HttpUrl url = new HttpUrl.Builder()
                    .scheme("https")
                    .host("xhamster.com")
                    .addPathSegment("x-api")
                    .addQueryParameter("r", "[" + gson.toJson(query) + "]") // Not a 100% JSON-compliant format
                    .build();

            Document doc = getOnlineDocument(url, XhamsterParser::onIntercept);
            if (doc != null) {
                // JSON response is wrapped between [ ... ]'s
                String body = doc.body().childNode(0).toString()
                        .trim()
                        .replace("\n[", "")
                        .replace("}]}]", "}]}");

                XhamsterGalleryContent galleryContent = gson.fromJson(body, XhamsterGalleryContent.class);
                result.addAll(galleryContent.toImageUrlList());
            }
        }

        return result;
    }

    private static okhttp3.Response onIntercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request()
                .newBuilder()
                .header("User-Agent", Consts.USER_AGENT)
                .header("x-requested-with", "XMLHttpRequest")
                .build();
        return chain.proceed(request);
    }
}

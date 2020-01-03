package me.devsaki.hentoid.parsers.images;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.json.sources.XhamsterGalleryContent;
import me.devsaki.hentoid.json.sources.XhamsterGalleryQuery;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.JsonHelper;
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

        for (int i = 0; i < Math.ceil(content.getQtyPages() / 16.0); i++) {
            XhamsterGalleryQuery query = new XhamsterGalleryQuery(content.getUniqueSiteId(), i + 1);

            HttpUrl url = new HttpUrl.Builder()
                    .scheme("https")
                    .host("xhamster.com")
                    .addPathSegment("x-api")
                    .addQueryParameter("r", "[" + JsonHelper.serializeToJson(query, XhamsterGalleryQuery.class) + "]") // Not a 100% JSON-compliant format
                    .build();

            Document doc = getOnlineDocument(url, XhamsterParser::onIntercept);
            if (doc != null) {
                // JSON response is wrapped between [ ... ]'s
                String body = doc.body().childNode(0).toString()
                        .replace("\n[", "")
                        .replace("}]}]", "}]}");

                XhamsterGalleryContent galleryContent = JsonHelper.jsonToObject(body, XhamsterGalleryContent.class);
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

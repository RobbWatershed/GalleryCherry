package me.devsaki.hentoid.parsers.images;

import androidx.core.util.Pair;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.json.sources.XhamsterGalleryContent;
import me.devsaki.hentoid.json.sources.XhamsterGalleryQuery;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.HttpUrl;

/**
 * Created by avluis on 07/26/2016.
 * Handles parsing of content from Xhamster
 */
public class XhamsterParser extends BaseImageListParser {

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

            List<Pair<String, String>> headers = new ArrayList<>();
            headers.add(new Pair<>("x-requested-with", "XMLHttpRequest"));

            Document doc = HttpHelper.getOnlineDocument(url.toString(), headers, false, false);
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
}

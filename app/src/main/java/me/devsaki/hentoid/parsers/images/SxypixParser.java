package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.POST_MIME_TYPE;

import com.annimon.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;

/**
 * Handles parsing of content from Sxypix
 */
public class SxypixParser extends BaseImageListParser {

    @SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
    private static class SxypixGallery {
        private List<String> r;

        public List<String> getPics() {
            if (r.isEmpty()) return Collections.emptyList();

            List<String> res = new ArrayList<>();
            for (String s : r) {
                Document doc = Jsoup.parse(s);
                Elements elts = doc.select(".gall_pix_el");
                if (elts.isEmpty()) elts = doc.select(".gall_pix_pix");

                if (elts.isEmpty()) return Collections.emptyList();
                List<String> pics = elts.stream().map(ParseHelper::getImgSrc).collect(Collectors.toUnmodifiableList());
                res.addAll(Stream.of(pics)
                        .map(s2 -> s2.replace("\\/", "/"))
                        .map(s3 -> "https:" + s3)
                        .toList()
                );

            }
            return res;
        }
    }

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        String subdomain = "";
        String aid = "";
        String ghash = "";

        Document doc = HttpHelper.getOnlineDocument(content.getGalleryUrl());
        if (doc != null) {
            Element elt = doc.selectFirst(".gallgrid");
            if (elt != null) {
                subdomain = elt.attr("data-x");
                aid = elt.attr("data-aid");
                ghash = elt.attr("data-ghash");
            }
        }

        try (Response res = HttpHelper.postOnlineResource(
                "https://sxypix.com/php/gall.php",
                null,
                false, false, false,
                "x=" + subdomain + "&ghash=" + ghash + "&aid=" + aid,
                POST_MIME_TYPE)) {
            SxypixGallery g = JsonHelper.jsonToObject(res.body().string(), SxypixGallery.class);
            return new ArrayList<>(g.getPics());
        }
    }
}

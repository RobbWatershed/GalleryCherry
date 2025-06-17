package me.devsaki.hentoid.parsers.images;

import com.annimon.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.network.HttpHelper;

/**
 * Handles parsing of content from pics-x
 */
public class PicsXParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        Document doc = HttpHelper.getOnlineDocument(content.getGalleryUrl());
        if (doc != null) {
            Elements images = doc.select(".image-container img");
            Elements imgData = doc.select("#images-container script");
            return parseImages(images, imgData);
        } else return Collections.emptyList();
    }

    public static List<String> parseImages(List<Element> images, List<Element> imageData) {
        // Using images directly
        if (!images.isEmpty() && imageData.isEmpty())
            return Stream.of(images).map(ParseHelper::getImgSrc).distinct().toList();

        // Using script data
        String scriptData = null;
        for (Element e : imageData) {
            if (e.html().contains("imagesHtml")) {
                scriptData = e.html();
                break;
            }
        }
        if (scriptData != null) {
            // Get image HTML
            String data = scriptData.replace(" ", "");
            int stringStart = data.indexOf('"');
            int stringEnd = data.indexOf('"', stringStart + 1);
            data = data.substring(stringStart + 1, stringEnd);
            data = new String(StringHelper.decode64(data), StandardCharsets.UTF_8);
            data = "<html><body>" + data + "</body></html>";

            // Parse image HTML
            Document doc = Jsoup.parse(data);
            return Stream.of(doc.select(".image-container img")).map(ParseHelper::getImgSrc).distinct().toList();
        }
        return Collections.emptyList();
    }
}

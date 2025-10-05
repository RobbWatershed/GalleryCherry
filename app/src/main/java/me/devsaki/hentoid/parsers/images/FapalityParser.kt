package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;

public class FapalityParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        // 1. Scan the gallery page for page viewer URLs
        List<String> pageUrls = new ArrayList<>();
        Document doc = HttpHelper.getOnlineDocument(content.getGalleryUrl(), headers, Site.FAPALITY.useHentoidAgent(), Site.FAPALITY.useWebviewAgent());
        if (doc != null) {
            List<Element> chapters = doc.select("a[itemprop][href*=com/photos/]");
            for (Element e : chapters) pageUrls.add(e.attr("href"));
        }

        progressStart(content, null, pageUrls.size());

        // 2. Open each page URL and get the image data until all images are found
        for (String url : pageUrls) {
            if (processHalted.get()) break;
            doc = HttpHelper.getOnlineDocument(url, headers, Site.FAPALITY.useHentoidAgent(), Site.FAPALITY.useWebviewAgent());
            if (doc != null) {
                List<Element> images = doc.select(".simple-content img");
                result.addAll(Stream.of(images).map(i -> i.attr("src")).filterNot(String::isEmpty).toList());
            }
            progressPlus();
        }
        progressComplete();

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        return result;
    }
}

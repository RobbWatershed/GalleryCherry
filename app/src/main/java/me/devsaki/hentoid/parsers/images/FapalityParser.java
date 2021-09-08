package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;

public class FapalityParser extends BaseImageListParser {

    @Override
    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        String readerUrl = content.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);

        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            result = parseImageFiles(content);
            ParseHelper.setDownloadParams(result, content.getSite().getUrl());
        } finally {
            EventBus.getDefault().unregister(this);
        }

        Timber.d("%s", result);

        return result;
    }

    private List<ImageFile> parseImageFiles(@NonNull Content content) throws Exception {
        List<ImageFile> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        HttpHelper.addCurrentCookiesToHeader(content.getGalleryUrl(), headers);

        // 1. Scan the gallery page for page viewer URLs
        List<String> pageUrls = new ArrayList<>();
        Document doc = HttpHelper.getOnlineDocument(content.getGalleryUrl(), headers, Site.FAPALITY.useHentoidAgent(), Site.FAPALITY.useWebviewAgent());
        if (doc != null) {
            List<Element> chapters = doc.select("a[itemprop][href*=com/photos/]");
            for (Element e : chapters) pageUrls.add(e.attr("href"));
        }

        progressStart(content.getId(), pageUrls.size());

        // 2. Open each page URL and get the image data until all images are found
        for (String url : pageUrls) {
            if (processHalted) break;
            doc = HttpHelper.getOnlineDocument(chp.getUrl(), headers, Site.FAPALITY.useHentoidAgent(), Site.FAPALITY.useWebviewAgent());
            if (doc != null) {
                List<Element> images = doc.select(".simple-content img");
                List<String> urls = new ArrayList<>();
                for (Element e : images) {
                    String url = e.attr("data-src").trim();
                    if (url.isEmpty()) url = e.attr("src").trim();
                    if (!url.isEmpty()) urls.add(url);
                }
                if (!urls.isEmpty())
                    result.addAll(ParseHelper.urlsToImageFiles(urls, orderOffset + result.size() + 1, StatusContent.SAVED, chp, 1000));
                else
                    Timber.w("Chapter parsing failed for %s : no pictures found", chp.getUrl());
            } else {
                Timber.w("Chapter parsing failed for %s : no response", chp.getUrl());
            }
            progressPlus();
        }
        progressComplete();

        // Add cover
        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted) throw new PreparationInterruptedException();

        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageList is overriden directly
        return null;
    }
}

package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import okhttp3.HttpUrl;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class SmartContent implements ContentParser {

    @Selector(":root")
    private Element root;
    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector("head title")
    private String title;
    @Selector(value = "a[href*='.jp']", attr = "href")
    private List<String> imageLinksJpg;
    @Selector(value = "a[href*='.png']", attr = "href")
    private List<String> imageLinksPng;
    // Alternate images are alone on the page, without links, zishy-style (else we would capture clickable thumbs)
    @Selector(value = ":not(a)>img[src*='.jp']")
    private List<Element> imageEltsJpg;
    @Selector(value = ":not(a)>img[src*='.png']")
    private List<Element> imageEltsPng;

    private List<String> imageLinks = new ArrayList<>();
    private List<String> imageElts = new ArrayList<>();


    // Remove duplicates in found images and stored them to an unified container
    private void processImages() {
        if (null != imageLinksJpg)
            imageLinks.addAll(Stream.of(imageLinksJpg).distinct().toList());
        if (null != imageLinksPng)
            imageLinks.addAll(Stream.of(imageLinksPng).distinct().toList());

        if (null != imageEltsJpg)
            imageElts.addAll(Stream.of(imageEltsJpg).map(e -> e.attr("src")).distinct().toList());
        if (null != imageEltsPng)
            imageElts.addAll(Stream.of(imageEltsPng).map(e -> e.attr("src")).distinct().toList());
    }

    private boolean isGallery() {
        return (imageLinks.size() > 4 || imageElts.size() > 4);
    }

    private void addLinksToImages(List<String> links, List<ImageFile> images, String url) {
        int order = 1;
        String urlHost = url.substring(0, url.indexOf("/", url.indexOf("://") + 3));
        String urlLocation = url.substring(0, url.lastIndexOf("/") + 1);

        for (String s : links) {
            if (!s.startsWith("http")) {
                if (s.startsWith("/"))
                    s = urlHost + s;
                else
                    s = urlLocation + s;
            }
            images.add(new ImageFile(order++, s, StatusContent.SAVED));
        }
    }

    public Content toContent(@NonNull String url) {
        Content result = new Content();

        processImages();

        result.setSite(Site.NONE); // Temp but needed for the rest of the operations; will be overwritten

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;

        Timber.i("galleryUrl : %s", theUrl);
        if (theUrl.startsWith("//")) theUrl = "http:" + theUrl;
        if (!theUrl.isEmpty()) {
            HttpUrl httpUrl = HttpUrl.get(theUrl);
            result.setUrl(httpUrl.scheme() + "://" + httpUrl.host() + httpUrl.encodedPath());
        } else result.setUrl("");

        if (!isGallery()) return result.setStatus(StatusContent.IGNORED);

        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        result.addAttributes(attributes);

        List<ImageFile> images = new ArrayList<>();

        if (imageLinks.size() > 4) addLinksToImages(imageLinks, images, url);
        else if (imageElts.size() > 4) addLinksToImages(imageElts, images, url);

        result.setQtyPages(images.size());
        result.addImageFiles(images);

        return result;
    }
}

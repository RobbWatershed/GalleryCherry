package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import okhttp3.HttpUrl;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class SmartContent extends BaseContentParser {

    @Selector(":root")
    private Element root;
    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector("head title")
    private String title;
    @Selector(value = "a[href*='.jpg']", attr = "href")
    private List<String> imageLinksJpg;
    @Selector(value = "a[href*='.jpeg']", attr = "href")
    private List<String> imageLinksJpeg;
    @Selector(value = "a[href*='.png']", attr = "href")
    private List<String> imageLinksPng;
    // Alternate images are alone on the page, without links, zishy-style (else we would capture clickable thumbs)
    @Selector(value = ":not(a)>img[src*='.jpg']")
    private List<Element> imageEltsJpg;
    @Selector(value = ":not(a)>img[src*='.jpeg']")
    private List<Element> imageEltsJpeg;
    @Selector(value = ":not(a)>img[src*='.png']")
    private List<Element> imageEltsPng;

    private List<String> imageLinks = new ArrayList<>();
    private List<String> imageElts = new ArrayList<>();


    // Remove duplicates in found images and stored them to an unified container
    private void processImages() {
        if (null != imageLinksJpg)
            imageLinks.addAll(Stream.of(imageLinksJpg).distinct().toList());
        if (null != imageLinksJpeg)
            imageLinks.addAll(Stream.of(imageLinksJpeg).distinct().toList());
        if (null != imageLinksPng)
            imageLinks.addAll(Stream.of(imageLinksPng).distinct().toList());

        if (null != imageEltsJpg)
            imageElts.addAll(Stream.of(imageEltsJpg).map(e -> e.attr("src")).distinct().toList());
        if (null != imageEltsJpeg)
            imageElts.addAll(Stream.of(imageEltsJpeg).map(e -> e.attr("src")).distinct().toList());
        if (null != imageEltsPng)
            imageElts.addAll(Stream.of(imageEltsPng).map(e -> e.attr("src")).distinct().toList());
    }

    private boolean isGallery() {
        return (imageLinks.size() > 4 || imageElts.size() > 4);
    }

    public static void addLinksToImages(List<String> links, List<ImageFile> images, String url) {
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
            images.add(ImageFile.fromImageUrl(order++, s, StatusContent.SAVED, links.size()));
        }
    }

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        processImages();

        content.setSite(Site.NONE); // Temp but needed for the rest of the operations; will be overwritten

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;

        Timber.i("galleryUrl : %s", theUrl);
        if (theUrl.startsWith("//")) theUrl = "http:" + theUrl;
        if (!theUrl.isEmpty()) {
            HttpUrl httpUrl = HttpUrl.get(theUrl);
            content.setUrl(httpUrl.scheme() + "://" + httpUrl.host() + httpUrl.encodedPath());
        } else content.setUrl("");

        if (!isGallery()) return new Content().setStatus(StatusContent.IGNORED);

        content.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        content.addAttributes(attributes);

        if (updateImages) {
            List<ImageFile> images = new ArrayList<>();

            if (imageLinks.size() > 4) addLinksToImages(imageLinks, images, url);
            else if (imageElts.size() > 4) addLinksToImages(imageElts, images, url);
            if (images.size() > 0) content.setCoverImageUrl(images.get(0).getUrl());

            content.setQtyPages(images.size());
            content.setImageFiles(images);
        }

        return content;
    }
}

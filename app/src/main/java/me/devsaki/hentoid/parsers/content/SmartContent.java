package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
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
    private List<String> imageLinks;
    @Selector(value = ":not(a)>img[src*='.jp']")
    // Alternate images are alone on the page, without links, zishy-style (else we would capture clickable thumbs)
    private List<Element> imageElts;


    private boolean isGallery() {
        if (null != imageLinks && imageLinks.size() > 4) return true;
        return null != imageElts && imageElts.size() > 4;
    }

    public Content toContent(@NonNull String url) {
        Content result = new Content();

        if (!isGallery()) return result;

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;

        Timber.i("galleryUrl : %s", theUrl);
        if (theUrl.startsWith("//")) theUrl = "http:" + theUrl;
        if (theUrl.length() > 0) {
            HttpUrl httpUrl = HttpUrl.get(theUrl);
            result.setUrl(httpUrl.encodedPath());
        } else result.setUrl("");

        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        result.addAttributes(attributes);

        List<ImageFile> images = new ArrayList<>();

        String urlHost = url.substring(0, url.indexOf("/", url.indexOf("://") + 3));
        String urlLocation = url.substring(0, url.lastIndexOf("/") + 1);

        int order = 1;
        if (imageLinks != null && imageLinks.size() > 4) {
            for (String s : imageLinks) {
                if (!s.startsWith("http")) {
                    if (s.startsWith("/"))
                        s = urlHost + s;
                    else
                        s = urlLocation + s;
                }
                images.add(new ImageFile(order++, s, StatusContent.SAVED));
            }
        } else if (imageElts != null && imageElts.size() > 4) {
            for (Element e : imageElts) {
                String s = e.attr("src");
                if (!s.startsWith("http")) {
                    if (s.startsWith("/"))
                        s = urlHost + s;
                    else
                        s = urlLocation + s;
                }
                images.add(new ImageFile(order++, s, StatusContent.SAVED));
            }
        }
        result.setQtyPages(images.size());
        result.addImageFiles(images);

        return result;
    }
}

package me.devsaki.hentoid.parsers.content;

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

public class SmartContent {

    @Selector(":root")
    private Element root;
    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector("head title")
    private String title;
    @Selector(value = "a[href*='.jp']", attr = "href")
    private List<String> imageLinks;
    @Selector(value = "img[src*='.jp']")
    private List<Element> imageElts;


    private boolean isGallery() {
        if (null != imageLinks && imageLinks.size() > 4) return true;
        return null != imageElts && imageElts.size() > 4;
    }

    public Content toContent() {
        Content result = new Content();

        if (!isGallery()) return result;


        result.setSite(Site.PORNPICGALLERIES); // TODO - get the right site

        Timber.i("galleryUrl : %s", galleryUrl);
        if (galleryUrl.startsWith("//")) galleryUrl = "http:" + galleryUrl;
        if (galleryUrl.length() > 0) {
            HttpUrl url = HttpUrl.get(galleryUrl);
            result.setUrl(url.encodedPath());
        } else result.setUrl("");

        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        List<ImageFile> images = new ArrayList<>();
        result.setImageFiles(images);

        int order = 1;
        if (imageLinks != null && imageLinks.size() > 4) {
            for (String s : imageLinks) {
                images.add(new ImageFile(order++, s, StatusContent.SAVED));
            }
        } else if (imageElts != null && imageElts.size() > 4) {
            for (Element e : imageElts) {
                // Images are alone on the page, without links, zishy-style (else they would simply be clickable thumbs)
                if (!e.parent().tagName().equalsIgnoreCase("a"))
                    images.add(new ImageFile(order++, e.attr("src"), StatusContent.SAVED));
            }
        }

        result.setQtyPages(images.size());

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}

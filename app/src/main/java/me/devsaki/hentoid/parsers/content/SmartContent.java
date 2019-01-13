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

public class SmartContent {

    @Selector(":root")
    private Element root;
    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector("head title")
    private String title;
    @Selector(value = "a[href*='.jp']", attr = "href")
    private List<String> imageLinks;
    @Selector(value = "a[href*='.jp']")
    private List<Element> imageLinksElts;


    public Content toContent() {
        Content result = new Content();

        if (null == imageLinks || imageLinks.size() < 5) return result;


        result.setSite(Site.PORNPICGALLERIES); // TODO - get the right site

        if (galleryUrl.startsWith("//")) galleryUrl = "http:" + galleryUrl;
        if (galleryUrl.length() > 0) {
            HttpUrl url = HttpUrl.get(galleryUrl);
            result.setUrl(url.encodedPath());
        }
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        List<ImageFile> images = new ArrayList<>();
        result.setImageFiles(images);

        int order = 1;
        /*
        for (String s : imageLinks) {
            images.add(new ImageFile(order++, s, StatusContent.SAVED));
        }*/
        for (Element e : imageLinksElts) {
            images.add(new ImageFile(order++, e.absUrl("href"), StatusContent.SAVED));
        }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());
        result.setQtyPages(images.size());

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}

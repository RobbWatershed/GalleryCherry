package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class PornPicsContent implements ContentParser {

    private String GALLERY_FOLDER = "/galleries/";

    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(".title-h1")
    private String title;
    @Selector(value = ".tags a")
    private List<Element> tags;
    @Selector(value = ".gallery-info__item a[href*='/?q']")
    private List<Element> models;
    @Selector(value = "#tiles .rel-link", attr = "href")
    private List<String> imageLinks;


    public Content toContent(@NonNull String url) {
        Content result = new Content();

        result.setSite(Site.PORNPICS);

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        int galleryLocation = theUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length();
        result.setUrl(theUrl.substring(galleryLocation));
        result.setTitle(title);

        if (null == imageLinks || imageLinks.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        AttributeMap attributes = new AttributeMap();

        if (models != null && models.size() > 1) {
            boolean first = true;
            for (Element e : models) {
                if (first) {
                    first = false;
                    continue;
                }
                attributes.add(new Attribute(AttributeType.MODEL, e.childNode(0).childNode(0).toString(), e.attr("href"), Site.PORNPICS));
            }
        }

        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.PORNPICS);
        result.addAttributes(attributes);


        List<ImageFile> images = new ArrayList<>();

        int order = 1;
        for (String s : imageLinks) {
            images.add(new ImageFile(order++, s, StatusContent.SAVED));
        }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());
        result.setImageFiles(images);
        result.setQtyPages(images.size());

        return result;
    }
}

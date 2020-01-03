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

public class HellpornoContent implements ContentParser {

    private String GALLERY_FOLDER = "/albums/";

    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "") // convert desktop to mobile ?
    private String galleryUrl;
    @Selector("#hp_underheader h3")
    private String title;
    @Selector(value = "a[href*='/pics/']") // except the first one (menu)
    private List<Element> tags;
    @Selector(value = "a[href*='/cs/']") // last one
    private List<Element> models;
    @Selector(value = ".hidden-img a", attr = "href")
    private List<String> imageLinks;


    public Content toContent(@NonNull String url) {
        Content result = new Content();

        result.setSite(Site.HELLPORNO);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        int galleryLocation = theUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length();
        result.setUrl(theUrl.substring(galleryLocation));
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();

        if (tags != null) {
            boolean first = true;
            for (Element a : tags) {
                if (!first)
                    ParseHelper.parseAttribute(attributes, AttributeType.TAG, a, true, Site.HELLPORNO);
                if (first) first = false;
            }
        }

        if (models != null) {
            Element e = models.get(models.size() - 1);
            attributes.add(new Attribute(AttributeType.MODEL, e.text(), e.attr("href"), Site.HELLPORNO));
        }
        result.addAttributes(attributes);


        List<ImageFile> images = new ArrayList<>();
        int order = 1;
        if (imageLinks != null)
            for (String s : imageLinks) {
                images.add(new ImageFile(order++, s, StatusContent.SAVED, imageLinks.size()));
            }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());
        result.setQtyPages(images.size());
        result.setImageFiles(images);

        return result;
    }
}

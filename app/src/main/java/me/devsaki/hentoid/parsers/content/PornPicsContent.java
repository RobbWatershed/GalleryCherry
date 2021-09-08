package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class PornPicsContent extends BaseContentParser {

    private String GALLERY_FOLDER = "/galleries/";

    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(".title-section h1")
    private String title;
    @Selector(value = ".tags a")
    private List<Element> tags;
    @Selector(value = ".gallery-info__item a[href*='/?q']")
    private List<Element> models;
    @Selector(value = "#tiles .rel-link", attr = "href")
    private List<String> imageLinks;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.PORNPICS);

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        int galleryLocation = theUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length();
        content.setUrl(theUrl.substring(galleryLocation));
        content.setTitle(title);

        if (null == imageLinks || imageLinks.isEmpty())
            return new Content().setStatus(StatusContent.IGNORED);

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
        content.addAttributes(attributes);

        if (updateImages) {
            List<ImageFile> images = new ArrayList<>();

            int order = 1;
            for (String s : imageLinks) {
                images.add(ImageFile.fromImageUrl(order++, s, StatusContent.SAVED, imageLinks.size()));
            }
            if (images.size() > 0) content.setCoverImageUrl(images.get(0).getUrl());
            content.setImageFiles(images);
            content.setQtyPages(images.size());
        }

        return content;
    }
}

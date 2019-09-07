package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class JpegworldContent implements ContentParser {

    private String GALLERY_FOLDER = "/galleries/";

    @Selector(value = "head link[rel='canonical']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector("#gallery-title")
    private String title;
    @Selector(value = ".tags-col a:not(.paysite)")
    private List<Element> tags;
    @Selector(value = ".gallery-item img", attr = "src")
    private List<String> imageLinks;


    public Content toContent(@NonNull String url) {
        Content result = new Content();

        result.setSite(Site.JPEGWORLD);

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        int galleryLocation = theUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length();
        result.setUrl(theUrl.substring(galleryLocation));
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();

        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.JPEGWORLD);

        result.addAttributes(attributes);

        List<ImageFile> images = new ArrayList<>();

        int order = 1;
        String[] parts;
        for (String s : imageLinks) {
            StringBuilder hiResLink = new StringBuilder();
            parts = s.split("/");
            for (int i = 0; i < parts.length; i++) {
                if (i != parts.length - 2)
                    hiResLink.append(parts[i]).append((i < parts.length - 1) ? "/" : "");
            }
            images.add(new ImageFile(order++, hiResLink.toString().replace("/thumbs/", "/galleries/"), StatusContent.SAVED));
        }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());
        result.setImageFiles(images);
        result.setQtyPages(images.size());

        return result;
    }
}

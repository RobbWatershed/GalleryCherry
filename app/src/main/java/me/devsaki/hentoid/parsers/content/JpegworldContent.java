package me.devsaki.hentoid.parsers.content;

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

public class JpegworldContent {

    private String GALLERY_FOLDER = "/galleries/";

    @Selector(value = "head link[rel='canonical']", attr = "href")
    private String galleryUrl;
    @Selector(".gallery-title")
    private String title;
    @Selector(value = ".tags-col a:not(.paysite)")
    private List<Element> tags;
    @Selector(value = ".gallery-item img", attr = "href")
    private List<String> imageLinks;


    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.JPEGWORLD);
        int galleryLocation = galleryUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length();
        result.setUrl(galleryUrl.substring(galleryLocation));
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);


        List<ImageFile> images = new ArrayList<>();
        result.setImageFiles(images);

        int order = 1;
        StringBuilder hiResLink = new StringBuilder();
        String[] parts;
        for (String s : imageLinks) {
            parts = s.split("/");
            for (int i = 0; i < parts.length; i++)
                if (i != parts.length - 2)
                    hiResLink.append(parts[i]).append((i < parts.length - 1) ? "/" : "");
            images.add(new ImageFile(order++, hiResLink.toString().replace("/thumbs/", "/galleries/"), StatusContent.SAVED));
        }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());
        result.setQtyPages(images.size());

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}

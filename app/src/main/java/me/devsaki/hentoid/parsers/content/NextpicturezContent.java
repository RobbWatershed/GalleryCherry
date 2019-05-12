package me.devsaki.hentoid.parsers.content;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class NextpicturezContent {

    private String GALLERY_FOLDER = "/gallery/";

    @Selector(value = "head link[rel='canonical']", attr = "href")
    private String galleryUrl;
    @Selector("#description")
    private String title;
    @Selector(value = "#thumbz a", attr = "href")
    private List<String> imageLinks;


    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.NEXTPICTUREZ);
        int galleryLocation = galleryUrl.indexOf(GALLERY_FOLDER) + GALLERY_FOLDER.length();
        result.setUrl(galleryUrl.substring(galleryLocation));
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        result.addAttributes(attributes);


        List<ImageFile> images = new ArrayList<>();

        int order = 1;
        for (String s : imageLinks) {
            images.add(new ImageFile(order++, galleryUrl + s, StatusContent.SAVED));
        }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());
        result.addImageFiles(images);
        result.setQtyPages(images.size());

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}

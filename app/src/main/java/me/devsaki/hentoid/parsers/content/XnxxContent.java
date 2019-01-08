package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class XnxxContent {

    @Selector(value = "head meta[name='twitter:url']", attr = "content")
    private String galleryUrl;
    @Selector(value = "head title")
    private String title;
    @Selector(value = ".descriptionGalleryPage")
    private List<Element> metadata;
    @Selector(value = ".descriptionGalleryPage a")
    private List<Element> tags;
    @Selector(value = ".downloadLink", attr="href")
    private List<String> imageLinks;


    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.XNXX);

        result.setTitle(title.substring(0, title.lastIndexOf("gallery") - 1));

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        // Models
        // TODO
        if (metadata != null) {
            //ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);
        }

        if (tags != null) {
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);
        }

        List<ImageFile> images = new ArrayList<>();
        result.setImageFiles(images);

        int order = 1;
        for(String s : imageLinks) {
            images.add(new ImageFile(order++, s, StatusContent.SAVED));
        }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());


        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}

package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

import static me.devsaki.hentoid.parsers.content.SmartContent.addLinksToImages;

public class Jjgirls2Content implements ContentParser {

    @Selector("head title")
    private String title;

    @Selector(value = ".info a.cats")
    private List<Element> models;
    @Selector(value = ".info a.tags.rgba")
    private List<Element> tags;

    @Selector(value = "a[href*='.jpg']", attr = "href")
    private List<String> imageLinksJpg;
    @Selector(value = "a[href*='.jpeg']", attr = "href")
    private List<String> imageLinksJpeg;
    @Selector(value = "a[href*='.png']", attr = "href")
    private List<String> imageLinksPng;

    private List<String> imageLinks = new ArrayList<>();

    // Remove duplicates in found images and stored them to an unified container
    private void processImages() {
        if (null != imageLinksJpg)
            imageLinks.addAll(Stream.of(imageLinksJpg).distinct().toList());
        if (null != imageLinksJpeg)
            imageLinks.addAll(Stream.of(imageLinksJpeg).distinct().toList());
        if (null != imageLinksPng)
            imageLinks.addAll(Stream.of(imageLinksPng).distinct().toList());
    }

    public Content toContent(@NonNull String url) {
        Content result = new Content();

        result.setSite(Site.JJGIRLS2);

        result.setUrl(url);
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        // Remove 1st model (sponsor site)
        if (!models.isEmpty()) models.remove(0);
        ParseHelper.parseAttributes(attributes, AttributeType.MODEL, models, true, Site.JJGIRLS2);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.JJGIRLS2);
        result.addAttributes(attributes);

        List<ImageFile> images = new ArrayList<>();
        processImages();
        addLinksToImages(imageLinks, images, url);
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());

        result.setQtyPages(images.size());
        result.setImageFiles(images);

        return result;
    }
}
package me.devsaki.hentoid.parsers.content;

import static me.devsaki.hentoid.parsers.content.SmartContent.addLinksToImages;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class BabeTodayContent extends BaseContentParser {

    @Selector("head title")
    private String title;

    @Selector(value = "a[href^='/mobile/']", attr = "href")
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
            imageLinks.addAll(Stream.of(imageLinksJpg).distinct().map(c -> c.replace("//", "https://")).toList());
        if (null != imageLinksJpeg)
            imageLinks.addAll(Stream.of(imageLinksJpeg).distinct().map(c -> c.replace("//", "https://")).toList());
        if (null != imageLinksPng)
            imageLinks.addAll(Stream.of(imageLinksPng).distinct().map(c -> c.replace("//", "https://")).toList());
    }

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.BABETODAY);

        content.setUrl(url);
        content.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        // Remove 1st tag (sponsor site)
        if (tags != null && !tags.isEmpty()) tags.remove(0);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.BABETODAY);
        content.addAttributes(attributes);

        if (updateImages) {
            List<ImageFile> images = new ArrayList<>();
            processImages();
            addLinksToImages(imageLinks, images, url);
            if (!images.isEmpty()) content.setCoverImageUrl(images.get(0).getUrl());

            content.setQtyPages(images.size());
            content.setImageFiles(images);
        }

        return content;
    }
}
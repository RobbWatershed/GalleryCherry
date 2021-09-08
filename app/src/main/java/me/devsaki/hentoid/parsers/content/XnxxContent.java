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

public class XnxxContent extends BaseContentParser {

    private static String PORNSTARS_MARKER = "Pornstars : ";
    private static String TAGS_MARKER = " - Tags : ";

    @Selector(value = "head meta[name='twitter:url']", attr = "content", defValue = "")
    private String galleryUrl;
    @Selector(value = "head title")
    private String title;
    @Selector(value = ".descriptionGalleryPage")
    private String rawMetadata;
    @Selector(value = ".descriptionGalleryPage a")
    private List<Element> tags;
    @Selector(value = ".downloadLink", attr = "href")
    private List<String> imageLinks;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.XNXX);

        int galleryIndex = title.lastIndexOf("gallery");
        if (galleryIndex > -1)
            content.setTitle(title.substring(0, title.lastIndexOf("gallery") - 1));
        else content.setTitle(title);

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        content.setUrl(theUrl.replace(Site.XNXX.getUrl(), "").replace("gallery/", ""));

        AttributeMap attributes = new AttributeMap();

        // Models
        if (rawMetadata != null && rawMetadata.contains(PORNSTARS_MARKER)) {
            int tagsPosition = rawMetadata.indexOf(TAGS_MARKER);

            String[] stars;
            if (tagsPosition > -1)
                stars = rawMetadata.substring(0, tagsPosition).replace(PORNSTARS_MARKER, "").split(",");
            else
                stars = rawMetadata.replace(PORNSTARS_MARKER, "").split(",");

            for (String s : stars) {
                attributes.add(new Attribute(AttributeType.MODEL, s.trim(), "/" + s.trim(), Site.XNXX));
            }
        }

        if (tags != null) {
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.XNXX);
        }
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

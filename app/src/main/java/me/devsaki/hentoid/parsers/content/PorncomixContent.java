package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.PorncomixParser;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class PorncomixContent implements ContentParser {
    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private String coverUrl;
    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title;

    @Selector(value = ".wp-manga-tags-list a[href*='tag']")
    private List<Element> mangaTags;
    @Selector(value = ".item-tags a[href*='tag']")
    private List<Element> galleryTags;
    @Selector(value = ".bb-tags a[href*='label']")
    private List<Element> zoneTags;
    @Selector(value = ".video-tags a[href*='tag']")
    private List<Element> bestTags;

    /*
    @Selector(value = "#single-pager")
    private Element mangaThumbsContainer;
    @Selector(value = "#dgwt-jg-2")
    private Element galleryThumbsContainer; // same for zone
    @Selector(value = "#gallery-2")
    private Element bestThumbsContainer;
     */

    @Selector(value = ".reading-content script")
    private Element mangaPagesContainer;
    @Selector(value = "#dgwt-jg-2 a")
    private List<Element> galleryPages; // same for zone
    @Selector(value = "#gallery-2 a")
    private List<Element> bestPages;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.PORNCOMIX);

        title = title.trim();
        if (title.isEmpty()) return result.setStatus(StatusContent.IGNORED);
        result.setTitle(Helper.removeNonPrintableChars(title.trim()));

        result.setUrl(url);
        result.setCoverImageUrl(coverUrl);

        String artist = "";
        if (result.getUrl().contains("/manga")) {
            String[] titleParts = title.split("-");
            artist = titleParts[0].trim();
        }

        /*
        if (mangaThumbsContainer != null) result.setQtyPages(mangaThumbsContainer.childNodeSize());
        else if (galleryThumbsContainer != null) result.setQtyPages(galleryThumbsContainer.childNodeSize());
        else if (bestThumbsContainer != null) result.setQtyPages(bestThumbsContainer.childNodeSize());
         */

        AttributeMap attributes = new AttributeMap();
        attributes.add(new Attribute(AttributeType.ARTIST, artist, artist, Site.PORNCOMIX));
        if (mangaTags != null && !mangaTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, mangaTags, true, Site.PORNCOMIX);
        else if (galleryTags != null && !galleryTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, galleryTags, true, Site.PORNCOMIX);
        else if (zoneTags != null && !zoneTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, zoneTags, true, Site.PORNCOMIX);
        else if (bestTags != null && !bestTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, bestTags, true, Site.PORNCOMIX);
        result.addAttributes(attributes);

        List<ImageFile> images = ParseHelper.urlsToImageFiles(PorncomixParser.parseImages(mangaPagesContainer, galleryPages, bestPages), StatusContent.SAVED);
        result.setImageFiles(images);
        result.setQtyPages(images.size());

        return result;
    }
}

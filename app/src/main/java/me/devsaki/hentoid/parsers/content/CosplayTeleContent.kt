package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class CosplayTeleContent extends BaseContentParser {

    private String GALLERY_FOLDER = "/galleries/";

    @Selector(value = "head meta[name='description']", attr = "content", defValue = "")
    private String title;
    @Selector(value = ".article-inner strong")
    private List<Element> tags;
    @Selector(value = ".gallery-icon a", attr = "href")
    private List<String> imageLinks;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.COSPLAYTELE);

        content.setUrl(url);
        content.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        for (Element tag : tags) {
            if (0 == tag.childrenSize()) continue;
            String tagStr = tag.toString().toLowerCase();
            Element elt = tag.child(0);
            if (tagStr.contains("cosplayer")) {
                ParseHelper.parseAttribute(attributes, AttributeType.MODEL, elt, true, Site.COSPLAYTELE);
            } else if (tagStr.contains("appears in")) {
                ParseHelper.parseAttribute(attributes, AttributeType.SERIE, elt, true, Site.COSPLAYTELE);
            } else if (tagStr.contains("character")) {
                ParseHelper.parseAttribute(attributes, AttributeType.CHARACTER, elt, true, Site.COSPLAYTELE);
            }
        }
        content.addAttributes(attributes);

        if (updateImages && !imageLinks.isEmpty()) {
            List<ImageFile> images = ParseHelper.urlsToImageFiles(imageLinks, imageLinks.get(0), StatusContent.SAVED);
            if (!images.isEmpty()) content.setCoverImageUrl(images.get(0).getUrl());
            content.setImageFiles(images);
            content.setQtyPages(images.size() - 1);
        }

        return content;
    }
}

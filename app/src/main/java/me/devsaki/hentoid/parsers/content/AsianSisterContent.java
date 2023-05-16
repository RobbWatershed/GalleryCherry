package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.image.ImageHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class AsianSisterContent extends BaseContentParser {

    @Selector(value = "h1", defValue = "")
    private String title;
    @Selector(value = ".headTitle a[href^=tag]")
    private List<Element> categoryTags;
    @Selector(value = "#detailBox a[href^=tag]")
    private List<Element> tags;
    @Selector(value = "img[dataurl]", attr = "dataurl")
    private List<String> thumbs;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.ASIANSISTER);
        content.setUrl(url.replace(Site.ASIANSISTER.getUrl(), ""));

        String[] titleParts = title.split("-");
        content.setTitle(titleParts[0].trim());

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, categoryTags, true, Site.ASIANSISTER);
        // Model is always the 1st tag
        if (!tags.isEmpty()) {
            List<Element> model = Stream.of(tags.get(0)).toList();
            ParseHelper.parseAttributes(attributes, AttributeType.MODEL, model, true, Site.ASIANSISTER);
            tags.remove(0);
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.ASIANSISTER);
        }
        content.addAttributes(attributes);

        // Remove duplicates
        if (updateImages) {
            if (!thumbs.isEmpty()) {
                thumbs = Stream.of(thumbs).distinct().map(s -> s.replace("imageimages", "images").trim()).filter(ImageHelper.INSTANCE::isSupportedImage).toList();
                content.setCoverImageUrl(thumbs.get(0));
                content.setImageFiles(ParseHelper.urlsToImageFiles(thumbs, thumbs.get(0), StatusContent.SAVED));
            }
            content.setQtyPages(thumbs.size() - 1); // Minus the cover
        }

        return content;
    }
}

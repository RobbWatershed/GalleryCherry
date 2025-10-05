package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class FapalityContent extends BaseContentParser {

    @Selector("h1")
    private List<Element> titles;
    @Selector(value = ".tags_list a")
    private List<Element> tags;
    @Selector(value = "img[itemprop]", attr = "src")
    private List<String> thumbs;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.FAPALITY);
        int photosIndex = url.indexOf("/photos/");
        content.setUrl(url.substring(photosIndex + 8));

        String title = "";
        if (titles != null && !titles.isEmpty()) {
            title = titles.get(0).text();
            int titleEnd = title.lastIndexOf(" - ");
            if (titleEnd > -1)
                title = title.substring(0, title.lastIndexOf(" - "));
        }
        content.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.FAPALITY);
        content.addAttributes(attributes);

        if (updateImages) {
            if (!thumbs.isEmpty()) content.setCoverImageUrl(thumbs.get(0));
            content.setQtyPages(thumbs.size());
        }

        return content;
    }
}

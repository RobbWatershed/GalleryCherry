package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class FapalityContent implements ContentParser {

    @Selector("h1")
    private List<Element> titles;
    @Selector(value = ".tags_list a")
    private List<Element> tags;
    @Selector(value = "img[itemprop]", attr = "src")
    private List<String> thumbs;


    public Content toContent(@NonNull String url) {
        Content result = new Content();

        result.setSite(Site.FAPALITY);
        int photosIndex = url.indexOf("/photos/");
        result.setUrl(url.substring(photosIndex + 8));

        String title = "";
        if (titles != null && !titles.isEmpty()) {
            title = titles.get(0).text();
            int titleEnd = title.lastIndexOf(" - ");
            if (titleEnd > -1)
                title = title.substring(0, title.lastIndexOf(" - "));
        }
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.FAPALITY);
        result.addAttributes(attributes);

        if (!thumbs.isEmpty()) result.setCoverImageUrl(thumbs.get(0));
        result.setQtyPages(thumbs.size());

        return result;
    }
}

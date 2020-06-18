package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class FapalityContent implements ContentParser {

    @Selector("h1")
    private List<Element> titles;
    @Selector(value = ".tags_list a")
    private List<Element> tags;
    @Selector(value = "img[itemprop]", attr = "src")
    private List<String> imageLinks;


    public Content toContent(@NonNull String url) {
        Content result = new Content();

        result.setSite(Site.FAPALITY);
        result.setUrl(url.substring(Site.FAPALITY.getUrl().length()));

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

        List<ImageFile> images = new ArrayList<>();
        int order = 1;
        String[] parts;
        for (String s : imageLinks) {
            StringBuilder sourceLink = new StringBuilder();
            parts = s.split("/");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0 && parts[i - 1].equalsIgnoreCase("main"))
                    continue; // Ignore the part after "main" to reconstitute the source URL
                sourceLink.append(parts[i]).append((i < parts.length - 1) ? "/" : "");
            }
            images.add(new ImageFile(order++, sourceLink.toString().replace("/main/", "/sources/"), StatusContent.SAVED, imageLinks.size()));
        }
        if (images.size() > 0) result.setCoverImageUrl(images.get(0).getUrl());
        result.setImageFiles(images);
        result.setQtyPages(images.size());

        return result;
    }
}

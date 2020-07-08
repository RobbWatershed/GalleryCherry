package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.HentaifoxParser;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class HentaifoxContent implements ContentParser {
    @Selector(value = ".cover img", attr = "src", defValue = "")
    private String coverUrl;
    @Selector(value = ".info h1", defValue = "")
    private String title;
    @Selector(".info")
    private Element information;
    @Selector(value = ".g_thumb img")
    private List<Element> thumbs;
    @Selector(value = "body script")
    private List<Element> scripts;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.HENTAIFOX);
        if (url.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(url.replace(Site.HENTAIFOX.getUrl(), "").replace("/gallery", ""));

        result.populateUniqueSiteId();
        result.setCoverImageUrl(coverUrl);
        result.setTitle(Helper.removeNonPrintableChars(title));

        if (null == information || information.children().isEmpty()) return result;

        AttributeMap attributes = new AttributeMap();

        for (Element e : information.children()) {
            // Flat info (pages, posted date)
            if (e.children().isEmpty() && e.hasText()) {
                if (e.text().toLowerCase().startsWith("pages")) {
                    result.setQtyPages(Integer.parseInt(e.text().toLowerCase().replace(" ", "").replace("pages:", "")));
                }
            } else if (e.children().size() > 1) { // Tags
                String metaType = e.child(0).text().replace(":", "").trim();
                List<Element> tagLinks = e.select("a");

                if (metaType.equalsIgnoreCase("artists"))
                    ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, tagLinks, true, Site.HENTAIFOX);
                if (metaType.equalsIgnoreCase("parodies"))
                    ParseHelper.parseAttributes(attributes, AttributeType.SERIE, tagLinks, true, Site.HENTAIFOX);
                if (metaType.equalsIgnoreCase("characters"))
                    ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, tagLinks, true, Site.HENTAIFOX);
                if (metaType.equalsIgnoreCase("tags"))
                    ParseHelper.parseAttributes(attributes, AttributeType.TAG, tagLinks, true, Site.HENTAIFOX);
                if (metaType.equalsIgnoreCase("groups"))
                    ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, tagLinks, true, Site.HENTAIFOX);
                if (metaType.equalsIgnoreCase("languages"))
                    ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, tagLinks, true, Site.HENTAIFOX);
                if (metaType.equalsIgnoreCase("category"))
                    ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, tagLinks, true, Site.HENTAIFOX);
            }
        }
        result.addAttributes(attributes);

        result.setImageFiles(ParseHelper.urlsToImageFiles(HentaifoxParser.parseImages(result, thumbs, scripts), result.getCoverImageUrl(), StatusContent.SAVED));

        return result;
    }
}

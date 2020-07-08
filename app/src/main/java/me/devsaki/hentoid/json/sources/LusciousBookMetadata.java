package me.devsaki.hentoid.json.sources;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;

public class LusciousBookMetadata {
    private BookData data;

    private static class BookData {
        private BookInfoContainer album;
    }

    private static class BookInfoContainer {
        private AlbumInfo get;
    }

    private static class AlbumInfo {
        private String id;
        private String title;
        private String url;
        private Integer number_of_pictures;
        private CoverInfo cover;
        private LanguageInfo language;
        private List<TagInfo> tags;
    }

    private static class CoverInfo {
        private String url;
    }

    private static class LanguageInfo {
        private String title;
        private String url;
    }

    private static class TagInfo {
        private String text;
        private String url;
    }

    private static final String RELATIVE_URL_PREFIX = "https://luscious.net";

    public Content toContent() {
        Content result = new Content();
        result.setSite(Site.LUSCIOUS);

        AlbumInfo info = data.album.get;
        if (null == info.url || null == info.title) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(info.url);

        result.setTitle(Helper.removeNonPrintableChars(info.title));

//        result.setQtyPages(info.number_of_pictures);  <-- does not reflect the actual number of pictures reachable via the Luscious API / website
        result.setCoverImageUrl(info.cover.url);

        AttributeMap attributes = new AttributeMap();
        if (info.language != null) {
            String name = Helper.removeNonPrintableChars(info.language.title.replace(" Language", ""));
            Attribute attribute = new Attribute(AttributeType.LANGUAGE, name, RELATIVE_URL_PREFIX + info.language.url, Site.LUSCIOUS);
            attributes.add(attribute);
        }

        if (info.tags != null) for (TagInfo tag : info.tags) {
            String name = Helper.removeNonPrintableChars(tag.text);
            if (name.contains(":"))
                name = name.substring(name.indexOf(':') + 1).trim(); // Clean all tags starting with "Type :" (e.g. "Artist : someguy")
            AttributeType type = AttributeType.TAG;
            if (tag.url.startsWith("/tags/artist:")) type = AttributeType.ARTIST;
//            else if (tag.url.startsWith("/tags/parody:")) type = AttributeType.SERIE;  <-- duplicate with series
            else if (tag.url.startsWith("/tags/character:")) type = AttributeType.CHARACTER;
            else if (tag.url.startsWith("/tags/series:")) type = AttributeType.SERIE;
            else if (tag.url.startsWith("/tags/group:")) type = AttributeType.ARTIST;
            Attribute attribute = new Attribute(type, name, RELATIVE_URL_PREFIX + tag.url, Site.LUSCIOUS);
            attributes.add(attribute);
        }
        result.addAttributes(attributes);

        return result;
    }
}

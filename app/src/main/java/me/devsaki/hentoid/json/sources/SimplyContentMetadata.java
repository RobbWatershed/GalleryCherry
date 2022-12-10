package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import com.squareup.moshi.Json;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;

@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class SimplyContentMetadata {
    Data data;

    private static class Data {
        String slug;
        String title;
        private @Json(name = "created_at")
        String createdAt;

        PageData preview;
        private @Json(name = "image_count")
        Integer imageCount;
        //List<PageData> images; <-- only the first few

        LanguageData language;
        List<MetadataEntry> artists;
        List<MetadataEntry> characters;
        List<MetadataEntry> parodies;
        MetadataEntry series;
        List<MetadataEntry> tags;
    }

    private static class MetadataEntry {
        String title;
        String slug;
    }

    private static class LanguageData {
        String name;
        String slug;
    }

    public static class PageData {
        @Json(name = "page_num")
        Integer pageNum;
        Map<String, String> sizes;

        String getFullUrl() {
            if (sizes.containsKey("full")) return sizes.get("full");
            else if (sizes.containsKey("giant_thumb")) return sizes.get("giant_thumb");
            else return "";
        }

        String getThumbUrl() {
            if (sizes.containsKey("small_thumb")) return sizes.get("small_thumb");
            else if (sizes.containsKey("thumb")) return sizes.get("thumb");
            else return "";
        }
    }

    public Content update(@NonNull Content content, boolean updateImages) {
        content.setSite(Site.SIMPLY);

        if (null == data || null == data.title || null == data.slug)
            return content.setStatus(StatusContent.IGNORED);

        String url = Site.SIMPLY.getUrl() + "manga/" + data.slug;

        content.setUrl(url);
        if (!data.createdAt.isEmpty())
            content.setUploadDate(Helper.parseDatetimeToEpoch(data.createdAt, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")); // e.g. 2022-10-23T19:47:08.717+02:00

        content.setTitle(StringHelper.removeNonPrintableChars(data.title));

        content.setQtyPages(data.imageCount);
        content.setCoverImageUrl(data.preview.getThumbUrl());

        AttributeMap attributes = new AttributeMap();
        if (data.language != null) {
            String name = StringHelper.removeNonPrintableChars(data.language.name);
            Attribute attribute = new Attribute(AttributeType.LANGUAGE, name, Site.SIMPLY.getUrl() + "language/" + data.language.slug, Site.SIMPLY);
            attributes.add(attribute);
        }
        if (data.series != null) {
            String name = StringHelper.removeNonPrintableChars(data.series.title);
            Attribute attribute = new Attribute(AttributeType.SERIE, name, Site.SIMPLY.getUrl() + "series/" + data.series.slug, Site.SIMPLY);
            attributes.add(attribute);
        }

        populateAttributes(attributes, data.artists, AttributeType.ARTIST, "artist");
        populateAttributes(attributes, data.characters, AttributeType.CHARACTER, "character");
        //populateAttributes(attributes, data.parodies, AttributeType.SERIE, "parody");
        populateAttributes(attributes, data.tags, AttributeType.TAG, "tag");

        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }

    private void populateAttributes(@NonNull AttributeMap attributes, List<MetadataEntry> entries, AttributeType type, @NonNull String typeUrl) {
        if (entries != null) for (MetadataEntry meta : entries) {
            String name = StringHelper.removeNonPrintableChars(meta.title);
            Attribute attribute = new Attribute(type, name, Site.SIMPLY.getUrl() + typeUrl + "/" + meta.slug, Site.SIMPLY);
            attributes.add(attribute);
        }
    }
}

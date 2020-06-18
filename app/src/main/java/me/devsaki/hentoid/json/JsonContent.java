package me.devsaki.hentoid.json;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Helper;

public class JsonContent {

    private String url;
    private String title;
    private String author;
    private String coverImageUrl;
    private Integer qtyPages;
    private long uploadDate;
    private long downloadDate;
    private StatusContent status;
    private Site site;
    private boolean favourite;
    private long reads;
    private long lastReadDate;
    private int lastReadPageIndex;
    private Map<String, String> bookPreferences = new HashMap<>();

    private Map<AttributeType, List<JsonAttribute>> attributes;
    private List<JsonImageFile> imageFiles = new ArrayList<>();
    private List<JsonErrorRecord> errorRecords = new ArrayList<>();

    private JsonContent() {
    }


    private void addAttribute(JsonAttribute attributeItem) {
        if (null == attributeItem) return;

        List<JsonAttribute> list;
        AttributeType type = attributeItem.getType();

        if (attributes.containsKey(type)) {
            list = attributes.get(type);
        } else {
            list = new ArrayList<>();
            attributes.put(type, list);
        }
        if (list != null) list.add(attributeItem);
    }

    public static JsonContent fromEntity(Content c) {
        return fromEntity(c, true);
    }

    public static JsonContent fromEntity(Content c, boolean keepImages) {
        JsonContent result = new JsonContent();
        result.url = c.getUrl();
        result.title = Helper.removeNonPrintableChars(c.getTitle());
        result.author = c.getAuthor();
        result.coverImageUrl = c.getCoverImageUrl();
        result.qtyPages = c.getQtyPages();
        result.uploadDate = c.getUploadDate();
        result.downloadDate = c.getDownloadDate();
        result.status = c.getStatus();
        result.site = c.getSite();
        result.favourite = c.isFavourite();
        result.reads = c.getReads();
        result.lastReadDate = c.getLastReadDate();
        result.lastReadPageIndex = c.getLastReadPageIndex();
        result.bookPreferences = c.getBookPreferences();

        result.attributes = new EnumMap<>(AttributeType.class);
        for (Attribute a : c.getAttributes()) {
            JsonAttribute attr = JsonAttribute.fromEntity(a, c.getSite());
            result.addAttribute(attr);
        }

        if (keepImages && c.getImageFiles() != null)
            for (ImageFile img : c.getImageFiles())
                result.imageFiles.add(JsonImageFile.fromEntity(img));

        if (c.getErrorLog() != null)
            for (ErrorRecord err : c.getErrorLog())
                result.errorRecords.add(JsonErrorRecord.fromEntity(err));

        return result;
    }

    public Content toEntity() {
        Content result = new Content();

        if (null == site) site = Site.NONE;
        result.setSite(site);
        result.setUrl(url);
        result.setTitle(Helper.removeNonPrintableChars(title));
        result.setAuthor(author);
        result.setCoverImageUrl(coverImageUrl);
        result.setQtyPages(qtyPages);
        result.setUploadDate(uploadDate);
        result.setDownloadDate(downloadDate);
        result.setStatus(status);
        result.setFavourite(favourite);
        result.setReads(reads);
        result.setLastReadDate(lastReadDate);
        result.setLastReadPageIndex(lastReadPageIndex);
        result.setBookPreferences(bookPreferences);

        if (attributes != null) {
            result.clearAttributes();
            for (List<JsonAttribute> jsonAttrList : attributes.values()) {
                // Remove duplicates that may exist in old JSONs (cause weird single tags to appear in the DB)
                jsonAttrList = Stream.of(jsonAttrList).distinct().toList();
                List<Attribute> attrList = new ArrayList<>();
                for (JsonAttribute attr : jsonAttrList) attrList.add(attr.toEntity(site));
                result.addAttributes(attrList);
            }
        }
        if (imageFiles != null) {
            List<ImageFile> imgs = new ArrayList<>();
            for (JsonImageFile img : imageFiles) imgs.add(img.toEntity(imageFiles.size()));
            result.setImageFiles(imgs);

            // Fix books with incorrect QtyPages that may exist in old JSONs
            if (qtyPages <= 0) result.setQtyPages(imageFiles.size());
        }
        if (errorRecords != null) {
            List<ErrorRecord> errs = new ArrayList<>();
            for (JsonErrorRecord err : errorRecords) errs.add(err.toEntity());
            result.setErrorLog(errs);
        }

        result.populateAuthor();
        result.populateUniqueSiteId();
        return result;
    }
}

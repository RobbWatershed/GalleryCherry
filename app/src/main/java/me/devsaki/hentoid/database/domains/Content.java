package me.devsaki.hentoid.database.domains;

import android.support.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.devsaki.hentoid.activities.websites.ASMHentaiActivity;
import me.devsaki.hentoid.activities.websites.BaseWebActivity;
import me.devsaki.hentoid.activities.websites.HellpornoActivity;
import me.devsaki.hentoid.activities.websites.FakkuActivity;
import me.devsaki.hentoid.activities.websites.JpegworldActivity;
import me.devsaki.hentoid.activities.websites.Link2GalleriesActivity;
import me.devsaki.hentoid.activities.websites.NextpicturezActivity;
import me.devsaki.hentoid.activities.websites.PornPicGalleriesActivity;
import me.devsaki.hentoid.activities.websites.PornPicsActivity;
import me.devsaki.hentoid.activities.websites.XhamsterActivity;
import me.devsaki.hentoid.activities.websites.XnxxActivity;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Preferences;

/**
 * Created by DevSaki on 09/05/2015.
 * Content builder
 */
@Entity
public class Content implements Serializable {

    @Id
    private long id;
    @Expose
    private String url;
    @Expose(serialize = false, deserialize = false)
    private String uniqueSiteId; // Has to be queryable in DB, hence has to be a field
    @Expose
    private String title;
    @Expose
    private String author;
    @Expose(serialize = false, deserialize = false)
    private ToMany<Attribute> attributes;
    @Expose
    private String coverImageUrl;
    @Expose
    private Integer qtyPages;
    @Expose
    private long uploadDate;
    @Expose
    private long downloadDate;
    @Expose
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status;
    @Expose(serialize = false, deserialize = false)
    @Backlink(to = "content")
    private ToMany<ImageFile> imageFiles;
    @Expose
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    private Site site;
    private String storageFolder; // Not exposed because it will vary according to book location -> valued at import
    @Expose
    private boolean favourite;
    @Expose
    private long reads = 0;
    @Expose
    private long lastReadDate;
    // Temporary during SAVED state only; no need to expose them for JSON persistence
    @Expose(serialize = false, deserialize = false)
    private String downloadParams;
    // Temporary during ERROR state only; no need to expose them for JSON persistence
    @Expose(serialize = false, deserialize = false)
    @Backlink(to = "content")
    private ToMany<ErrorRecord> errorLog;
    @Expose(serialize = false, deserialize = false)
    private int lastReadPageIndex = 0;
    @Expose(serialize = false, deserialize = false)
    private boolean isBeingDeleted = false;

    // Runtime attributes; no need to expose them nor to persist them
    @Transient
    private double percent;
    @Transient
    private int queryOrder;
    @Transient
    private boolean selected = false;

    // Kept for retro-compatibility with contentV2.json Hentoid files
    @Transient
    @Expose
    @SerializedName("attributes")
    private AttributeMap attributeMap;
    @Transient
    @Expose
    @SerializedName("imageFiles")
    private ArrayList<ImageFile> imageList;


    public ToMany<Attribute> getAttributes() {
        return this.attributes;
    }

    public void setAttributes(ToMany<Attribute> attributes) {
        this.attributes = attributes;
    }

    public AttributeMap getAttributeMap() {
        AttributeMap result = new AttributeMap();
        for (Attribute a : attributes) {
            a.computeUrl(this.getSite());
            result.add(a);
        }
        return result;
    }

    public Content addAttributes(AttributeMap attributes) {
        if (attributes != null) {
            for (AttributeType type : attributes.keySet()) {
                this.attributes.addAll(attributes.get(type));
            }
        }
        return this;
    }

    public long getId() {
        return this.id;
    }

    public Content setId(long id) {
        this.id = id;
        return this;
    }

    public String getUniqueSiteId() {
        return this.uniqueSiteId;
    }

    private String computeUniqueSiteId() {
        String[] parts = url.split("/");

        switch (site) {
            case XHAMSTER:
                return url.substring(url.lastIndexOf("-") + 1);
            case XNXX:
                if (parts.length > 0) return parts[0];
                else return "";
            case PORNPICS:
            case HELLPORNO:
            case PORNPICGALLERIES:
            case LINK2GALLERIES:
            case NEXTPICTUREZ:
                return parts[parts.length - 1];
            case JPEGWORLD:
                return url.substring(url.lastIndexOf("-") + 1, url.lastIndexOf("."));
            default:
                return "";
        }
    }

    public Class<?> getWebActivityClass() {
        return getWebActivityClass(this.site);
    }

    public static Class<?> getWebActivityClass(Site site) {
        switch (site) {
            case XHAMSTER:
                return XhamsterActivity.class;
            case XNXX:
                return XnxxActivity.class;
            case PORNPICS:
                return PornPicsActivity.class;
            case JPEGWORLD:
                return JpegworldActivity.class;
            case NEXTPICTUREZ:
                return NextpicturezActivity.class;
            case HELLPORNO:
                return HellpornoActivity.class;
            case PORNPICGALLERIES:
                return PornPicGalleriesActivity.class;
            case LINK2GALLERIES:
                return Link2GalleriesActivity.class;
            case FAKKU2:
                return FakkuActivity.class;
            default:
                return BaseWebActivity.class;
        }
    }

    public String getCategory() {
        if (attributes != null) {
            List<Attribute> attributesList = getAttributeMap().get(AttributeType.CATEGORY);
            if (attributesList != null && attributesList.size() > 0) {
                return attributesList.get(0).getName();
            }
        }

        return null;
    }

    public String getUrl() {
        return url;
    }

    public Content setUrl(String url) {
        this.url = url;
        this.uniqueSiteId = computeUniqueSiteId();
        return this;
    }

    public String getGalleryUrl() {
        String galleryConst;
        switch (site) {
            case PORNPICGALLERIES:
            case LINK2GALLERIES:
                return url; // Specific case - user can go on any site
            case HELLPORNO:
                galleryConst = ""; // Site landpage URL already contains the "/albums/" prefix
                break;
            case PORNPICS:
            case JPEGWORLD:
                galleryConst = "galleries/";
                break;
            default:
                galleryConst = "gallery/";
                break;
        }

        return site.getUrl() + galleryConst + url;
    }

    public String getReaderUrl() {
        switch (site) {
            default:
                return getGalleryUrl();
        }
    }

    public Content populateAuthor() {
        String author = "";
        AttributeMap attrMap = getAttributeMap();
        if (attrMap.containsKey(AttributeType.ARTIST) && attrMap.get(AttributeType.ARTIST).size() > 0)
            author = attrMap.get(AttributeType.ARTIST).get(0).getName();
        if (null == author || author.equals("")) // Try and get Circle
        {
            if (attrMap.containsKey(AttributeType.CIRCLE) && attrMap.get(AttributeType.CIRCLE).size() > 0)
                author = attrMap.get(AttributeType.CIRCLE).get(0).getName();
        }
        if (null == author) author = "";
        setAuthor(author);
        return this;
    }

    public Content preJSONExport() { // TODO - this is shabby
        this.attributeMap = getAttributeMap();
        this.imageList = new ArrayList<>(imageFiles);
        return this;
    }

    public Content postJSONImport() {   // TODO - this is shabby
        if (null == site) site = Site.NONE;

        if (this.attributeMap != null) {
            this.attributes.clear();
            for (AttributeType type : this.attributeMap.keySet()) {
                for (Attribute attr : this.attributeMap.get(type)) {
                    if (null == attr.getType())
                        attr.setType(AttributeType.SERIE); // Fix the issue with v1.6.5
                    this.attributes.add(attr.computeLocation(site));
                }
            }
        }
        if (this.imageList != null) {
            this.imageFiles.clear();
            this.imageFiles.addAll(this.imageList);
        }
        this.populateAuthor();
        this.uniqueSiteId = computeUniqueSiteId();
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Content setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getAuthor() {
        if (null == author) populateAuthor();
        return author;
    }

    public Content setAuthor(String author) {
        this.author = author;
        return this;
    }

    public String getCoverImageUrl() {
        if (coverImageUrl != null && !coverImageUrl.isEmpty()) return coverImageUrl;
        else if ((imageFiles != null) && (imageFiles.size() > 0)) return imageFiles.get(0).getUrl();
        else return null;
    }

    public Content setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
        return this;
    }

    public Integer getQtyPages() {
        return qtyPages;
    }

    public Content setQtyPages(Integer qtyPages) {
        this.qtyPages = qtyPages;
        return this;
    }

    long getUploadDate() {
        return uploadDate;
    }

    public Content setUploadDate(long uploadDate) {
        this.uploadDate = uploadDate;
        return this;
    }

    long getDownloadDate() {
        return downloadDate;
    }

    public Content setDownloadDate(long downloadDate) {
        this.downloadDate = downloadDate;
        return this;
    }

    public StatusContent getStatus() {
        return status;
    }

    public Content setStatus(StatusContent status) {
        this.status = status;
        return this;
    }

    @Nullable
    public ToMany<ImageFile> getImageFiles() {
        return imageFiles;
    }

    public Content addImageFiles(List<ImageFile> imageFiles) {
        if (imageFiles != null) {
            this.imageFiles.clear();
            this.imageFiles.addAll(imageFiles);
        }
        return this;
    }

    @Nullable
    public ToMany<ErrorRecord> getErrorLog() {
        return errorLog;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    public Site getSite() {
        return site;
    }

    public Content setSite(Site site) {
        this.site = site;
        return this;
    }

    public String getStorageFolder() {
        return storageFolder == null ? "" : storageFolder;
    }

    public Content setStorageFolder(String storageFolder) {
        this.storageFolder = storageFolder;
        return this;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public Content setFavourite(boolean favourite) {
        this.favourite = favourite;
        return this;
    }

    private int getQueryOrder() {
        return queryOrder;
    }

    public Content setQueryOrder(int order) {
        queryOrder = order;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }


    public long getReads() {
        return reads;
    }

    public Content increaseReads() {
        this.reads++;
        return this;
    }

    public Content setReads(long reads) {
        this.reads = reads;
        return this;
    }

    public long getLastReadDate() {
        return (0 == lastReadDate) ? downloadDate : lastReadDate;
    }

    public Content setLastReadDate(long lastReadDate) {
        this.lastReadDate = lastReadDate;
        return this;
    }

    public String getDownloadParams() {
        return (null == downloadParams) ? "" : downloadParams;
    }

    public Content setDownloadParams(String params) {
        downloadParams = params;
        return this;
    }

    public int getLastReadPageIndex() {
        return lastReadPageIndex;
    }

    public void setLastReadPageIndex(int index) {
        this.lastReadPageIndex = index;
    }

    public boolean isBeingDeleted() {
        return isBeingDeleted;
    }

    public void setIsBeingDeleted(boolean isBeingDeleted) {
        this.isBeingDeleted = isBeingDeleted;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Content content = (Content) o;

        return (url != null ? url.equals(content.url) : content.url == null) && site == content.site;
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + (site != null ? site.hashCode() : 0);
        return result;
    }

    public static Comparator<Content> getComparator(int compareMethod) {
        switch (compareMethod) {
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA:
                return TITLE_ALPHA_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST:
                return DLDATE_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED:
                return TITLE_ALPHA_INV_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_LAST:
                return DLDATE_INV_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_RANDOM:
                return QUERY_ORDER_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_LAST_UL_DATE_FIRST:
                return ULDATE_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_LEAST_READ:
                return READS_ORDER_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_MOST_READ:
                return READS_ORDER_INV_COMPARATOR;
            case Preferences.Constant.ORDER_CONTENT_LAST_READ:
                return READ_DATE_INV_COMPARATOR;
            default:
                return QUERY_ORDER_COMPARATOR;
        }
    }

    private static final Comparator<Content> TITLE_ALPHA_COMPARATOR = (a, b) -> a.getTitle().compareTo(b.getTitle());

    private static final Comparator<Content> DLDATE_COMPARATOR = (a, b) -> Long.compare(a.getDownloadDate(), b.getDownloadDate()) * -1; // Inverted - last download date first

    private static final Comparator<Content> ULDATE_COMPARATOR = (a, b) -> Long.compare(a.getUploadDate(), b.getUploadDate()) * -1; // Inverted - last upload date first

    private static final Comparator<Content> TITLE_ALPHA_INV_COMPARATOR = (a, b) -> a.getTitle().compareTo(b.getTitle()) * -1;

    private static final Comparator<Content> DLDATE_INV_COMPARATOR = (a, b) -> Long.compare(a.getDownloadDate(), b.getDownloadDate());

    public static final Comparator<Content> READS_ORDER_COMPARATOR = (a, b) -> {
        int comp = Long.compare(a.getReads(), b.getReads());
        return (0 == comp) ? Long.compare(a.getLastReadDate(), b.getLastReadDate()) : comp;
    };

    public static final Comparator<Content> READS_ORDER_INV_COMPARATOR = (a, b) -> {
        int comp = Long.compare(a.getReads(), b.getReads()) * -1;
        return (0 == comp) ? Long.compare(a.getLastReadDate(), b.getLastReadDate()) * -1 : comp;
    };

    public static final Comparator<Content> READ_DATE_INV_COMPARATOR = (a, b) -> Long.compare(a.getLastReadDate(), b.getLastReadDate()) * -1;

    private static final Comparator<Content> QUERY_ORDER_COMPARATOR = (a, b) -> Integer.compare(a.getQueryOrder(), b.getQueryOrder());
}

package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.devsaki.hentoid.activities.websites.BaseWebActivity;
import me.devsaki.hentoid.activities.websites.HellpornoActivity;
import me.devsaki.hentoid.activities.websites.JpegworldActivity;
import me.devsaki.hentoid.activities.websites.NextpicturezActivity;
import me.devsaki.hentoid.activities.websites.PornPicGalleriesActivity;
import me.devsaki.hentoid.activities.websites.PornPicsActivity;
import me.devsaki.hentoid.activities.websites.XhamsterActivity;
import me.devsaki.hentoid.activities.websites.XnxxActivity;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by DevSaki on 09/05/2015.
 * Content builder
 */
public class Content implements Serializable {

    @Expose
    private String url;
    @Expose
    private String title;
    @Expose
    private String author;
    @Expose
    private AttributeMap attributes;
    @Expose
    private String coverImageUrl;
    @Expose
    private Integer qtyPages;
    @Expose
    private long uploadDate;
    @Expose
    private long downloadDate;
    @Expose
    private StatusContent status;
    @Expose
    private List<ImageFile> imageFiles;
    @Expose
    private Site site;
    private String storageFolder; // Not exposed because it will vary according to book location -> valued at import
    @Expose
    private boolean favourite;
    @Expose
    private long reads = 0;
    @Expose
    private long lastReadDate;
    // Runtime attributes; no need to expose them
    private double percent;
    private int queryOrder;
    private boolean selected = false;


    public AttributeMap getAttributes() {
        if (null == attributes) attributes = new AttributeMap();
        return attributes;
    }

    public Content setAttributes(AttributeMap attributes) {
        this.attributes = attributes;
        return this;
    }

    public int getId() {
        return url.hashCode();
    }

    public String getUniqueSiteId() {
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
            case NEXTPICTUREZ:
                return parts[parts.length - 1];
            case JPEGWORLD:
                return url.substring(url.lastIndexOf("-") + 1, url.lastIndexOf("."));
            default:
                return "";
        }
    }

    public Class<?> getWebActivityClass() {
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
            default:
                return BaseWebActivity.class;
        }
    }

    public String getCategory() {
        if (attributes != null) {
            List<Attribute> attributesList = attributes.get(AttributeType.CATEGORY);
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
        return this;
    }

    public String getGalleryUrl() {
        String galleryConst;
        switch (site) {
            case PORNPICGALLERIES:
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
        if (getAttributes().containsKey(AttributeType.ARTIST) && attributes.get(AttributeType.ARTIST).size() > 0)
            author = attributes.get(AttributeType.ARTIST).get(0).getName();
        if (null == author || author.equals("")) // Try and get Circle
        {
            if (attributes.containsKey(AttributeType.CIRCLE) && attributes.get(AttributeType.CIRCLE).size() > 0)
                author = attributes.get(AttributeType.CIRCLE).get(0).getName();
        }
        if (null == author) author = "";
        setAuthor(author);
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

    public long getUploadDate() {
        return uploadDate;
    }

    public Content setUploadDate(long uploadDate) {
        this.uploadDate = uploadDate;
        return this;
    }

    public long getDownloadDate() {
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

    public List<ImageFile> getImageFiles() {
        if (null == imageFiles) imageFiles = Collections.emptyList();
        return imageFiles;
    }

    public Content setImageFiles(List<ImageFile> imageFiles) {
        this.imageFiles = imageFiles;
        return this;
    }

    public double getPercent() {
        return percent;
    }

    public Content setPercent(double percent) {
        this.percent = percent;
        return this;
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

    public static final Comparator<Content> TITLE_ALPHA_COMPARATOR = (a, b) -> a.getTitle().compareTo(b.getTitle());

    public static final Comparator<Content> DLDATE_COMPARATOR = (a, b) -> Long.compare(a.getDownloadDate(), b.getDownloadDate()) * -1; // Inverted - last download date first

    public static final Comparator<Content> ULDATE_COMPARATOR = (a, b) -> Long.compare(a.getUploadDate(), b.getUploadDate()) * -1; // Inverted - last upload date first

    public static final Comparator<Content> TITLE_ALPHA_INV_COMPARATOR = (a, b) -> a.getTitle().compareTo(b.getTitle()) * -1;

    public static final Comparator<Content> DLDATE_INV_COMPARATOR = (a, b) -> Long.compare(a.getDownloadDate(), b.getDownloadDate());

    public static final Comparator<Content> READS_ORDER_COMPARATOR = (a, b) -> {
        int comp = Long.compare(a.getReads(), b.getReads());
        return (0 == comp) ? Long.compare(a.getLastReadDate(), b.getLastReadDate()) : comp;
    };

    public static final Comparator<Content> READS_ORDER_INV_COMPARATOR = (a, b) -> {
        int comp = Long.compare(a.getReads(), b.getReads()) * -1;
        return (0 == comp) ? Long.compare(a.getLastReadDate(), b.getLastReadDate()) * -1 : comp;
    };

    public static final Comparator<Content> READ_DATE_INV_COMPARATOR = (a, b) -> Long.compare(a.getLastReadDate(), b.getLastReadDate()) * -1;

    public static final Comparator<Content> QUERY_ORDER_COMPARATOR = (a, b) -> Integer.compare(a.getQueryOrder(), b.getQueryOrder());
}

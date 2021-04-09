package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Transient;
import io.objectbox.converter.PropertyConverter;
import io.objectbox.relation.ToMany;
import me.devsaki.hentoid.activities.sources.AsianSisterActivity;
import me.devsaki.hentoid.activities.sources.BaseWebActivity;
import me.devsaki.hentoid.activities.sources.FapalityActivity;
import me.devsaki.hentoid.activities.sources.HellpornoActivity;
import me.devsaki.hentoid.activities.sources.Jjgirls2Activity;
import me.devsaki.hentoid.activities.sources.JjgirlsActivity;
import me.devsaki.hentoid.activities.sources.JpegworldActivity;
import me.devsaki.hentoid.activities.sources.Link2GalleriesActivity;
import me.devsaki.hentoid.activities.sources.LusciousActivity;
import me.devsaki.hentoid.activities.sources.NextpicturezActivity;
import me.devsaki.hentoid.activities.sources.PornPicGalleriesActivity;
import me.devsaki.hentoid.activities.sources.PornPicsActivity;
import me.devsaki.hentoid.activities.sources.RedditActivity;
import me.devsaki.hentoid.activities.sources.XhamsterActivity;
import me.devsaki.hentoid.activities.sources.XnxxActivity;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.JsonHelper.MAP_STRINGS;

/**
 * Created by DevSaki on 09/05/2015.
 * Content builder
 */
@SuppressWarnings("UnusedReturnValue")
@Entity
public class Content implements Serializable {

    @Id
    private long id;
    @Index
    private String url;
    private String uniqueSiteId; // Has to be queryable in DB, hence has to be a field
    private String title;
    private String author;
    private ToMany<Attribute> attributes;
    private String coverImageUrl;
    private Integer qtyPages; // Integer is actually unnecessary, but changing this to plain int requires a small DB model migration...
    private long uploadDate;
    private long downloadDate = 0;
    @Index
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status;
    @Backlink(to = "content")
    private ToMany<ImageFile> imageFiles;
    @Backlink(to = "content")
    public ToMany<GroupItem> groupItems;
    @Index
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    private Site site;
    /**
     * @deprecated Replaced by {@link me.devsaki.hentoid.workers.ImportWorker} methods; class is kept for retrocompatibilty
     */
    @Deprecated
    private String storageFolder; // Used as pivot for API29 migration; no use after that (replaced by storageUri)
    private String storageUri; // Not exposed because it will vary according to book location -> valued at import
    private boolean favourite;
    private long reads = 0;
    private long lastReadDate;
    private int lastReadPageIndex = 0;
    @Convert(converter = Content.StringMapConverter.class, dbType = String.class)
    private Map<String, String> bookPreferences = new HashMap<>();

    // Aggregated data redundant with the sum of individual data contained in ImageFile
    // ObjectBox can't do the sum in a single Query, so here it is !
    private long size = 0;
    private float readProgress = 0f;

    // Temporary during SAVED state only
    private String downloadParams;
    // Temporary during ERROR state only
    @Backlink(to = "content")
    private ToMany<ErrorRecord> errorLog;
    // Needs to be in the DB to keep the information when deletion takes a long time
    // and user navigates away; no need to save that into JSON
    private boolean isBeingDeleted = false;
    // Needs to be in the DB to optimize I/O
    // No need to save that into the JSON file itself, obviously
    private String jsonUri;
    // Useful only during cleanup operations; no need to get it into the JSON
    private boolean isFlaggedForDeletion = false;

    // Runtime attributes; no need to expose them for JSON persistence nor to persist them to DB
    @Transient
    private long progress;          // number of downloaded pages; used to display the progress bar on the queue screen
    @Transient
    private long downloadedBytes = 0;// Number of downloaded bytes; used to display the size estimate on the queue screen
    @Transient
    private boolean isFirst;        // True if current content is the first of its set in the DB query
    @Transient
    private boolean isLast;         // True if current content is the last of its set in the DB query
    @Transient
    private int numberDownloadRetries = 0;  // Current number of download retries current content has gone through
    @Transient
    private int readPagesCount = -1;  // Read pages count fed by payload; only useful to update list display
    @Transient
    private String archiveLocationUri;  // Only used when importing external archives

    public Content() {
    }


    public ToMany<Attribute> getAttributes() {
        return this.attributes;
    }

    public void setAttributes(ToMany<Attribute> attributes) {
        this.attributes = attributes;
    }

    public void clearAttributes() {
        this.attributes.clear();
    }

    public void putAttributes(List<Attribute> attributes) {
        // We do want to compare array references, not content
        if (attributes != null && attributes != this.attributes) {
            this.attributes.clear();
            this.attributes.addAll(attributes);
        }
    }

    public AttributeMap getAttributeMap() {
        AttributeMap result = new AttributeMap();
        if (attributes != null)
            for (Attribute a : attributes) result.add(a);
        return result;
    }

    public void putAttributes(@NonNull AttributeMap attrs) {
        if (attributes != null) {
            attributes.clear();
            addAttributes(attrs);
        }
    }

    public Content addAttributes(@NonNull AttributeMap attrs) {
        if (attributes != null) {
            for (Map.Entry<AttributeType, List<Attribute>> entry : attrs.entrySet()) {
                List<Attribute> attrList = entry.getValue();
                if (attrList != null)
                    addAttributes(attrList);
            }
        }
        return this;
    }

    public Content addAttributes(@NonNull List<Attribute> attrs) {
        if (attributes != null) attributes.addAll(attrs);
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
        if (null == uniqueSiteId) uniqueSiteId = computeUniqueSiteId();
        return uniqueSiteId;
    }

    public void populateUniqueSiteId() {
        this.uniqueSiteId = computeUniqueSiteId();
    }

    public void setUniqueSiteId(@NonNull String uniqueSiteId) {
        this.uniqueSiteId = uniqueSiteId;
    }

    private String computeUniqueSiteId() {
        if (null == url) return "";

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
            case JJGIRLS2:
                return parts[parts.length - 1];
            case FAPALITY:
                return parts[parts.length - 2];
            case JPEGWORLD:
                return url.substring(url.lastIndexOf("-") + 1, url.lastIndexOf("."));
            case REDDIT:
                return "reddit"; // One single book
            case JJGIRLS:
                return parts[parts.length - 2] + "/" + parts[parts.length - 1];
            case LUSCIOUS:
                // ID is the last numeric part of the URL
                // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
                int lastIndex = url.lastIndexOf('_');
                return url.substring(lastIndex + 1, url.length() - 1);
            case ASIANSISTER:
                // ID is the first numeric part of the URL
                // e.g. /view_51651_stuff_561_58Pn -> 51651 is the ID
                parts = url.split("_");
                if (parts.length > 1) return parts[1];
                else return "";
            default:
                return "";
        }
    }

    public Class<?> getWebActivityClass() {
        return getWebActivityClass(this.site);
    }

    public static Class<? extends AppCompatActivity> getWebActivityClass(Site site) {
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
            case REDDIT:
                return RedditActivity.class;
            case JJGIRLS:
                return JjgirlsActivity.class;
            case JJGIRLS2:
                return Jjgirls2Activity.class;
            case LUSCIOUS:
                return LusciousActivity.class;
            case FAPALITY:
                return FapalityActivity.class;
            case ASIANSISTER:
                return AsianSisterActivity.class;
            default:
                return BaseWebActivity.class;
        }
    }

    public String getCategory() {
        if (attributes != null) {
            List<Attribute> attributesList = getAttributeMap().get(AttributeType.CATEGORY);
            if (attributesList != null && !attributesList.isEmpty()) {
                return attributesList.get(0).getName();
            }
        }

        return null;
    }

    public String getUrl() {
        return (null == url) ? "" : url;
    }

    public Content setUrl(String url) {
        this.url = url;
        computeUniqueSiteId();
        return this;
    }

    public boolean isUrlBrowsable() {
        if (url != null) {
            return !site.equals(Site.NONE) && !site.equals(Site.HINA);
        }
        return false;
    }

    public String getGalleryUrl() {
        String galleryConst;
        switch (site) {
            case PORNPICGALLERIES:
            case LINK2GALLERIES:
            case REDDIT: // N/A
            case JJGIRLS:
            case JJGIRLS2:
                return url; // Specific case - user can go on any site (smart parser)
            case HELLPORNO:
            case FAPALITY:
            case ASIANSISTER:
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
        return getGalleryUrl();
    }

    /**
     * Neutralizes the given cover URL to detect duplicate books
     *
     * @param url  Cover URL to neutralize
     * @param site Site the URL is taken from
     * @return Neutralized cover URL
     */
    public static String getNeutralCoverUrlRoot(@NonNull final String url, @NonNull final Site site) {
        return url;
    }

    public Content populateAuthor() {
        String authorStr = "";
        AttributeMap attrMap = getAttributeMap();
        if (attrMap.containsKey(AttributeType.ARTIST) && !attrMap.get(AttributeType.ARTIST).isEmpty())
            authorStr = attrMap.get(AttributeType.ARTIST).get(0).getName();
        if ((null == authorStr || authorStr.equals(""))
                && attrMap.containsKey(AttributeType.CIRCLE)
                && !attrMap.get(AttributeType.CIRCLE).isEmpty()) // Try and get Circle
            authorStr = attrMap.get(AttributeType.CIRCLE).get(0).getName();

        if (null == authorStr) authorStr = "";
        setAuthor(authorStr);
        return this;
    }

    public String getTitle() {
        return Helper.protect(title);
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

    public int getQtyPages() {
        return (null == qtyPages) ? 0 : qtyPages;
    }

    public Content setQtyPages(int qtyPages) {
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

    @Nullable
    public ToMany<ImageFile> getImageFiles() {
        return imageFiles;
    }

    public Content setImageFiles(List<ImageFile> imageFiles) {
        // We do want to compare array references, not content
        if (imageFiles != null && imageFiles != this.imageFiles) {
            this.imageFiles.clear();
            this.imageFiles.addAll(imageFiles);
        }
        return this;
    }

    public ImageFile getCover() {
        List<ImageFile> images = getImageFiles();
        if (images != null && !images.isEmpty()) {
            for (ImageFile img : images)
                if (img.isCover()) return img;
        }
        return new ImageFile(0, getCoverImageUrl(), StatusContent.ONLINE, 1);
    }

    public String getCoverImageUrl() {
        return (null == coverImageUrl) ? "" : coverImageUrl;
    }

    public Content setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
        return this;
    }

    @Nullable
    public ToMany<ErrorRecord> getErrorLog() {
        return errorLog;
    }

    public void setErrorLog(List<ErrorRecord> errorLog) {
        if (errorLog != null && !errorLog.equals(this.errorLog)) {
            this.errorLog.clear();
            this.errorLog.addAll(errorLog);
        }
    }

    public double getPercent() {
        if (getQtyPages() > 0)
            return progress * 1.0 / getQtyPages();
        else
            return 0;
    }

    public void setProgress(long progress) {
        this.progress = progress;
    }

    public void computeProgress() {
        if (0 == progress && imageFiles != null)
            progress = Stream.of(imageFiles).filter(i -> i.getStatus() == StatusContent.DOWNLOADED || i.getStatus() == StatusContent.ERROR).count();
    }

    public double getBookSizeEstimate() {
        if (downloadedBytes > 0) {
            computeProgress();
            if (progress > 3) return (long) (downloadedBytes / getPercent());
        }
        return 0;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public void computeDownloadedBytes() {
        if (0 == downloadedBytes)
            downloadedBytes = Stream.of(imageFiles).mapToLong(ImageFile::getSize).sum();
    }

    public long getNbDownloadedPages() {
        if (imageFiles != null)
            return Stream.of(imageFiles).filter(i -> (i.getStatus() == StatusContent.DOWNLOADED || i.getStatus() == StatusContent.EXTERNAL) && i.isReadable()).count();
        else return 0;
    }

    private long getDownloadedPagesSize() {
        if (imageFiles != null)
            return Stream.of(imageFiles).filter(i -> (i.getStatus() == StatusContent.DOWNLOADED || i.getStatus() == StatusContent.EXTERNAL)).collect(Collectors.summingLong(ImageFile::getSize));
        else return 0;
    }

    public long getSize() {
        return size;
    }

    public void forceSize(long size) {
        this.size = size;
    }

    public void computeSize() {
        size = getDownloadedPagesSize();
    }

    public void computeReadProgress() {
        readProgress = getReadPagesCount() * 1f / Stream.of(getImageFiles()).withoutNulls().filter(ImageFile::isReadable).count();
    }

    public float getReadProgress() {
        return readProgress;
    }

    public Site getSite() {
        return site;
    }

    public Content setSite(Site site) {
        this.site = site;
        return this;
    }


    /**
     * @deprecated Replaced by getStorageUri; accessor is kept for API29 migration
     */
    @Deprecated
    public String getStorageFolder() {
        return storageFolder == null ? "" : storageFolder;
    }

    /**
     * @deprecated Replaced by getStorageUri; accessor is kept for API29 migration
     */
    @Deprecated
    public void resetStorageFolder() {
        storageFolder = "";
    }

    public String getStorageUri() {
        return storageUri == null ? "" : storageUri;
    }

    public Content setStorageUri(String storageUri) {
        this.storageUri = storageUri;
        return this;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public Content setFavourite(boolean favourite) {
        this.favourite = favourite;
        return this;
    }

    public boolean isLast() {
        return isLast;
    }

    public void setLast(boolean last) {
        this.isLast = last;
    }

    public boolean isFirst() {
        return isFirst;
    }

    public void setFirst(boolean first) {
        this.isFirst = first;
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
        return lastReadDate;
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

    public Map<String, String> getBookPreferences() {
        return bookPreferences;
    }

    public void setBookPreferences(Map<String, String> bookPreferences) {
        this.bookPreferences = bookPreferences;
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

    public String getJsonUri() {
        return (null == jsonUri) ? "" : jsonUri;
    }

    public void setJsonUri(String jsonUri) {
        this.jsonUri = jsonUri;
    }

    public boolean isFlaggedForDeletion() {
        return isFlaggedForDeletion;
    }

    public void setFlaggedForDeletion(boolean flaggedForDeletion) {
        isFlaggedForDeletion = flaggedForDeletion;
    }

    public int getNumberDownloadRetries() {
        return numberDownloadRetries;
    }

    public void increaseNumberDownloadRetries() {
        this.numberDownloadRetries++;
    }

    public boolean isArchive() {
        return ArchiveHelper.isSupportedArchive(getStorageUri()); // Warning : this shortcut assumes the URI contains the file name, which is not guaranteed !
    }

    public String getArchiveLocationUri() {
        return archiveLocationUri;
    }

    public void setArchiveLocationUri(String archiveLocationUri) {
        this.archiveLocationUri = archiveLocationUri;
    }

    public List<GroupItem> getGroupItems(Grouping grouping) {
        List<GroupItem> result = new ArrayList<>();
        for (GroupItem gi : groupItems)
            if (gi.group.getTarget().grouping.equals(grouping)) result.add(gi);

        return result;
    }

    public int getReadPagesCount() {
        if (readPagesCount > -1) return readPagesCount;

        if (null == imageFiles) return 0;
        int countReadPages = (int) Stream.of(imageFiles).filter(ImageFile::isRead).filter(ImageFile::isReadable).count();
        if (0 == countReadPages && lastReadPageIndex > 0)
            return lastReadPageIndex; // pre-v1.13 content
        else return countReadPages; // post v1.13 content
    }

    public void setReadPagesCount(int count) {
        readPagesCount = count;
    }


    public static class StringMapConverter implements PropertyConverter<Map<String, String>, String> {
        @Override
        public Map<String, String> convertToEntityProperty(String databaseValue) {
            if (null == databaseValue) return new HashMap<>();

            try {
                return JsonHelper.jsonToObject(databaseValue, MAP_STRINGS);
            } catch (IOException e) {
                Timber.w(e);
                return new HashMap<>();
            }
        }

        @Override
        public String convertToDatabaseValue(Map<String, String> entityProperty) {
            return JsonHelper.serializeToJson(entityProperty, MAP_STRINGS);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content content = (Content) o;
        return getId() == content.getId() &&
                Objects.equals(getUniqueSiteId(), content.getUniqueSiteId());
    }

    @Override
    public int hashCode() {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(id, uniqueSiteId);
    }

    public long hash64() {
        return Helper.hash64((id + "." + uniqueSiteId).getBytes());
    }
}

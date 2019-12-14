package me.devsaki.hentoid.database.domains;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.StatusContent;

/**
 * Created by DevSaki on 10/05/2015.
 * Image File builder
 */
@Entity
public class ImageFile {

    @Id
    private long id;
    private Integer order;
    private String url;
    private String name;
    private boolean favourite = false;
    @Convert(converter = StatusContent.StatusContentConverter.class, dbType = Integer.class)
    private StatusContent status;
    public ToOne<Content> content;


    // Temporary attributes during SAVED state only; no need to expose them for JSON persistence
    private String downloadParams;


    // Runtime attributes; no need to expose them nor to persist them

    // Display order of the image in the image viewer
    @Transient
    private int displayOrder;
    // Absolute storage path of the image
    @Transient
    private String absolutePath;
    // Has the image been read from a backup URL ?
    @Transient
    private boolean isBackup = false;
    // Inferred MIME-type of the image
    @Transient
    private String mimeType; // TODO : make it persistent ?


    public ImageFile() {
    }

    public ImageFile(int order, String url, StatusContent status, int maxPages) {
        this.order = order;

        int nbMaxDigits = (int) (Math.floor(Math.log10(maxPages)) + 1);
        this.name = String.format(Locale.US, "%0" + nbMaxDigits + "d", order);

        this.url = url;
        this.status = status;
        this.favourite = false;
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Integer getOrder() {
        return order;
    }

    public ImageFile setOrder(Integer order) {
        this.order = order;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public ImageFile setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getName() {
        return name;
    }

    public ImageFile setName(String name) {
        this.name = name;
        return this;
    }

    public void computeNameFromOrder() {
        name = String.format(Locale.US, "%03d", order);
    }

    public StatusContent getStatus() {
        return status;
    }

    public ImageFile setStatus(StatusContent status) {
        this.status = status;
        return this;
    }

    public String getDownloadParams() {
        return (null == downloadParams) ? "" : downloadParams;
    }

    public ImageFile setDownloadParams(String params) {
        downloadParams = params;
        return this;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isBackup() {
        return isBackup;
    }

    public void setBackup(boolean backup) {
        isBackup = backup;
    }

    public String getMimeType() {
        return (null == mimeType) ? "" : mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }


    public static final Comparator<ImageFile> ORDER_COMPARATOR = (a, b) -> a.getOrder().compareTo(b.getOrder());

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImageFile imageFile = (ImageFile) o;

        return Objects.equals(url, imageFile.url);
    }

    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }
}

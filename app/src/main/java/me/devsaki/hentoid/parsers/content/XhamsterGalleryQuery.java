package me.devsaki.hentoid.parsers.content;

import com.google.gson.annotations.Expose;

public class XhamsterGalleryQuery {

    @Expose
    public final String name = "photosGalleryPagedCollectionFetch";
    @Expose
    public final XHamsterGalleryRequestData requestData;

    public XhamsterGalleryQuery(String galleryId, int page) {
        requestData = new XHamsterGalleryRequestData(galleryId, page);
    }
}

class XHamsterGalleryRequestData {
    @Expose
    public final String entityID;
    @Expose
    public final Integer page;

    public XHamsterGalleryRequestData(String galleryId, int page) {
        entityID = galleryId;
        this.page = page;
    }
}

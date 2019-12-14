package me.devsaki.hentoid.json.sources;

public class XhamsterGalleryQuery {

    public final String name = "photosGalleryPagedCollectionFetch";
    private final XHamsterGalleryRequestData requestData;

    public XhamsterGalleryQuery(String galleryId, int page) {
        requestData = new XHamsterGalleryRequestData(galleryId, page);
    }

    static class XHamsterGalleryRequestData {
        final String entityID;
        public final Integer page;

        XHamsterGalleryRequestData(String galleryId, int page) {
            entityID = galleryId;
            this.page = page;
        }
    }
}
package me.devsaki.hentoid.json.sources;

import java.util.ArrayList;
import java.util.List;

public class XhamsterGalleryContent {

    public String name;
    public Pagination pagination;
    private List<Page> responseData;

    public List<String> toImageUrlList() {
        List<String> result = new ArrayList<>();

        if (responseData != null) {
            int order = 1;
            for (Page p : responseData) {
                result.add(p.imageURL);
            }
        }

        return result;
    }

    static class Pagination {
        private Integer maxPage;
        private Integer maxPages;
        public String pageLinkFirst;

        public int getMaxPage() {
            if (maxPage != null && maxPage > 0) return maxPage;
            if (maxPages != null && maxPages > 0) return maxPages;
            return 0;
        }
    }

    static class Page {
        private String imageURL;

        // TODO - get more content ?
    }
}

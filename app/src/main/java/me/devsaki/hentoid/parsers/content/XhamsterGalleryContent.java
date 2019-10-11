package me.devsaki.hentoid.parsers.content;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

public class XhamsterGalleryContent {

    @Expose
    public String name;
    @Expose
    public Pagination pagination;
    @Expose
    public List<Page> responseData;

    public List<String> toImageUrlList()
    {
        List<String> result = new ArrayList<>();

        if (responseData != null)
        {
            int order = 1;
            for (Page p : responseData)
            {
                result.add(p.imageURL);
            }
        }

        return result;
    }

    class Pagination
    {
        @Expose
        public Integer maxPage;
        @Expose
        public Integer maxPages;
        @Expose
        public String pageLinkFirst;

        public int getMaxPage()
        {
            if (maxPage != null && maxPage > 0) return maxPage;
            if (maxPages != null && maxPages > 0) return maxPages;
            return 0;
        }
    }

    class Page
    {
        @Expose
        public String imageURL;

        // TODO - get more content ?
    }
}

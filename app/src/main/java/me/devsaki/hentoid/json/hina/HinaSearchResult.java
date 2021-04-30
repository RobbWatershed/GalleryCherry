package me.devsaki.hentoid.json.hina;

import com.annimon.stream.Stream;
import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public class HinaSearchResult {

    @Json(name = "nbHits")
    private int maxctr;
    @Json(name = "limit")
    private int resultsPerPage;
    @Json(name = "hits")
    private List<HinaGallery> data;


    public int getMaxPages() {
        return (int) Math.ceil(maxctr * 1f / resultsPerPage);
    }

    public int getMaxAlbums() {
        return maxctr;
    }

    public List<Content> getGalleries() {
        List<Content> result = new ArrayList<>();
        if (data != null)
            for (HinaGallery g : data) result.add(g.toContent());
        return result;
    }

    public static class HinaGallery {

        @Json(name = "iid")
        private String id;
        @Json(name = "Name")
        private String name;
        @Json(name = "Edata")
        private String edata;
        @Json(name = "poster")
        private String thumb;

        public Content toContent() {
            Content result = new Content();

            result.setSite(Site.HINA);
            result.setTitle(name);
            result.setStatus(StatusContent.ONLINE);

            List<Attribute> attrList = new ArrayList<>();
            if (edata != null && !edata.isEmpty()) {
                String[] edataParts = edata.split(" ");
                List<String> edataPartsUnique = Stream.of(edataParts).distinct().toList();

                for (String attr : edataPartsUnique)
                    attrList.add(new Attribute(AttributeType.TAG, attr, "hina/" + attr, Site.HINA));
            }
            result.addAttributes(attrList);
            result.setCoverImageUrl(thumb);

            result.populateAuthor();
            result.setUniqueSiteId(id);
            return result;
        }
    }
}

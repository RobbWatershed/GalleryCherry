package me.devsaki.hentoid.json.hina;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.ContentHelper;

public class HinaResult {

    private int maxres;
    private int maxctr;
    private List<HinaGallery> data;


    public int getMaxPages() {
        return maxres;
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
        private String id;
        private Date time;
        private String origin;
        private String name;
        private String idol;
        private String edata;
        private String thumb;
        private List<String> gliphs;


        public Content toContent() {
            Content result = new Content();

            result.setSite(Site.HINA);
            result.setUrl(origin);
            result.setTitle(name);
            result.setStatus(StatusContent.ONLINE);

            List<Attribute> attrList = new ArrayList<>();
            if (edata != null && !edata.isEmpty()) {
                String[] edataParts = edata.split(" ");
                List<String> edataPartsUnique = Stream.of(edataParts).distinct().toList();

                for (String attr : edataPartsUnique)
                    attrList.add(new Attribute(AttributeType.TAG, attr, "hina/" + attr, Site.HINA));
            }
            if (idol != null && !idol.isEmpty() && !idol.equalsIgnoreCase("unknown")) {
                attrList.add(new Attribute(AttributeType.MODEL, idol, "hina/" + idol, Site.HINA));
            }
            result.addAttributes(attrList);
            if (gliphs != null && !gliphs.isEmpty()) {
                List<ImageFile> imgs = ParseHelper.urlsToImageFiles(gliphs, thumb, StatusContent.SAVED);
                for (ImageFile i : imgs)
                    i.setDownloadParams(ContentHelper.makeDownloadParams("hina-id", id));
                result.setImageFiles(imgs);
                result.setQtyPages(gliphs.size());
            } else {
                result.setCoverImageUrl(thumb);
            }

            result.populateAuthor();
            result.setUniqueSiteId(id);
            return result;
        }
    }
}

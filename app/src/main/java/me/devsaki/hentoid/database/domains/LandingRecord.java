package me.devsaki.hentoid.database.domains;

import java.util.Comparator;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import me.devsaki.hentoid.enums.Site;

@Entity
public class LandingRecord { // TODO serialize to JSON to keep between reinstalls

    @Id
    public long id;
    @Convert(converter = Site.SiteConverter.class, dbType = Long.class)
    public Site site;
    @Index
    public String url;
    public long lastAccessDate;

    public LandingRecord() {
    }

    public LandingRecord(Site site, String url) {
        this.site = site;
        this.url = url;
    }

    public static final Comparator<LandingRecord> DATE_COMPARATOR_DESC = (a, b) -> -1 * Long.compare(a.lastAccessDate, b.lastAccessDate);
}

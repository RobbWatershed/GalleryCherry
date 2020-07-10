package me.devsaki.hentoid.enums;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 */
public enum Site {

    XHAMSTER(0, "XHamster", "https://m.xhamster.com/photos/", "xhamster", R.drawable.ic_menu_xhamster, true, false, false, false),
    XNXX(1, "XNXX", "https://multi.xnxx.com/", "XNXX", R.drawable.ic_menu_xnxx, true, false, false, false),
    PORNPICS(2, "Pornpics", "https://www.pornpics.com/", "pornpics", R.drawable.ic_menu_pornpics, true, false, false, false),
    JPEGWORLD(3, "Jpegworld", "https://www.jpegworld.com/", "jpegworld", R.drawable.ic_menu_jpegworld, true, false, false, false),
    NEXTPICTUREZ(4, "Nextpicturez", "http://www.nextpicturez.com/", "nextpicturez", R.drawable.ic_menu_nextpicturez, true, false, false, false),
    HELLPORNO(5, "Hellporno", "https://hellporno.com/albums/", "hellporno", R.drawable.ic_menu_hellporno, true, false, false, false),
    PORNPICGALLERIES(6, "Pornpicgalleries", "http://pornpicgalleries.com/", "pornpicgalleries", R.drawable.ic_menu_ppg, true, false, false, false),
    LINK2GALLERIES(7, "Link2galleries", "https://www.link2galleries.com/", "link2galleries", R.drawable.ic_menu_l2g, true, false, false, false),
    REDDIT(8, "Reddit", "https://www.reddit.com/", "reddit", R.drawable.ic_social_reddit, true, false, false, true),
    JJGIRLS(9, "JJGirls", "https://jjgirls.com/mobile/", "jjgirls", R.drawable.ic_menu_jjgirls, true, false, true, false),
    LUSCIOUS(10, "luscious.net", "https://members.luscious.net/porn/", "luscious", R.drawable.ic_menu_luscious, false, false, false, false),
    FAPALITY(11, "Fapality", "https://fapality.com/photos/", "fapality", R.drawable.ic_menu_fapality, true, false, false, false),
    HINA(12, "Hina", "https://github.com/ixilia/hina", "hina", R.drawable.ic_info, true, false, false, false),
    NONE(98, "none", "", "none", R.drawable.ic_external_library, true, false, false, false); // External library; fallback site


    private final int code;
    private final String description;
    private final String uniqueKeyword;
    private final String url;
    private final int ico;
    private final boolean canKnowHentoidAgent;
    private final boolean hasImageProcessing;
    private final boolean hasBackupURLs;
    private final boolean isDanbooru;

    Site(int code,
         String description,
         String url,
         String uniqueKeyword,
         int ico,
         boolean canKnowHentoidAgent,
         boolean hasImageProcessing,
         boolean hasBackupURLs,
         boolean isDanbooru) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.uniqueKeyword = uniqueKeyword;
        this.ico = ico;
        this.canKnowHentoidAgent = canKnowHentoidAgent;
        this.hasImageProcessing = hasImageProcessing;
        this.hasBackupURLs = hasBackupURLs;
        this.isDanbooru = isDanbooru;
    }

    public static Site searchByCode(long code) {
        for (Site s : values())
            if (s.getCode() == code) return s;

        return NONE;
    }

    // Same as ValueOf with a fallback to NONE
    // (vital for forward compatibility)
    public static Site searchByName(String name) {
        for (Site s : values())
            if (s.name().equalsIgnoreCase(name)) return s;

        return NONE;
    }

    public static Site searchByUrl(String url) {
        if (null == url || url.isEmpty()) {
            Timber.w("Invalid url");
            return null;
        }
        for (Site s : Site.values()) {
            if (url.contains(s.getUniqueKeyword()))
                return s;
        }
        return Site.NONE;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    private String getUniqueKeyword() {
        return uniqueKeyword;
    }

    public String getUrl() {
        return url;
    }

    public int getIco() {
        return ico;
    }


    public boolean canKnowHentoidAgent() {
        return canKnowHentoidAgent;
    }

    public boolean hasImageProcessing() {
        return hasImageProcessing;
    }

    public boolean hasBackupURLs() {
        return hasBackupURLs;
    }

    public boolean isDanbooru() {
        return isDanbooru;
    }

    public String getFolder() {
        return description;
    }

    public static class SiteConverter implements PropertyConverter<Site, Long> {
        @Override
        public Site convertToEntityProperty(Long databaseValue) {
            if (databaseValue == null) {
                return Site.NONE;
            }
            for (Site site : Site.values()) {
                if (site.getCode() == databaseValue) {
                    return site;
                }
            }
            return Site.NONE;
        }

        @Override
        public Long convertToDatabaseValue(Site entityProperty) {
            return entityProperty == null ? null : (long) entityProperty.getCode();
        }
    }
}

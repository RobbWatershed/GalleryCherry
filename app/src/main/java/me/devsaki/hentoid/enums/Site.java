package me.devsaki.hentoid.enums;

import java.io.File;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 * TODO: deprecate {@link #allowParallelDownloads} on 1/10/2020 if not needed by that time
 */
public enum Site {

    XHAMSTER(0, "XHamster", "https://m.xhamster.com/photos/", "xhamster", R.drawable.ic_menu_xhamster, true, true, false, false),
    XNXX(1, "XNXX", "https://multi.xnxx.com/", "XNXX", R.drawable.ic_menu_xnxx, true, true, false, false),
    PORNPICS(2, "Pornpics", "https://www.pornpics.com/", "pornpics", R.drawable.ic_menu_pornpics, true, true, false, false),
    JPEGWORLD(3, "Jpegworld", "https://www.jpegworld.com/", "jpegworld", R.drawable.ic_menu_jpegworld, true, true, false, false),
    NEXTPICTUREZ(4, "Nextpicturez", "http://www.nextpicturez.com/", "nextpicturez", R.drawable.ic_menu_nextpicturez, true, true, false, false),
    HELLPORNO(5, "Hellporno", "https://hellporno.com/albums/", "hellporno", R.drawable.ic_menu_hellporno, true, true, false, false),
    PORNPICGALLERIES(6, "Pornpicgalleries", "http://pornpicgalleries.com/", "pornpicgalleries", R.drawable.ic_menu_ppg, true, true, false, false),
    LINK2GALLERIES(7, "Link2galleries", "https://www.link2galleries.com/", "link2galleries", R.drawable.ic_menu_l2g, true, true, false, false),
    NONE(98, "none", "", "none", R.drawable.ic_menu_about, true, true, false, false); // Fallback site


    private final int code;
    private final String description;
    private final String uniqueKeyword;
    private final String url;
    private final int ico;
    private final boolean allowParallelDownloads;
    private final boolean canKnowHentoidAgent;
    private final boolean hasImageProcessing;
    private final boolean hasBackupURLs;

    Site(int code,
         String description,
         String url,
         String uniqueKeyword,
         int ico,
         boolean allowParallelDownloads,
         boolean canKnowHentoidAgent,
         boolean hasImageProcessing,
         boolean hasBackupURLs) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.uniqueKeyword = uniqueKeyword;
        this.ico = ico;
        this.allowParallelDownloads = allowParallelDownloads;
        this.canKnowHentoidAgent = canKnowHentoidAgent;
        this.hasImageProcessing = hasImageProcessing;
        this.hasBackupURLs = hasBackupURLs;
    }

    public static Site searchByCode(long code) {
        if (code == -1) {
            Timber.w("Invalid site code!");
        }
        for (Site s : Site.values()) {
            if (s.getCode() == code)
                return s;
        }
        return Site.NONE;
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

    public boolean isAllowParallelDownloads() {
        return allowParallelDownloads;
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

    public String getFolder() {
        return File.separator + description + File.separator;
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

package me.devsaki.hentoid.enums;

import androidx.annotation.Nullable;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 */
public enum Site {

    XHAMSTER(0, "XHamster", "https://m.xhamster.com/photos/", R.drawable.ic_menu_xhamster, true, false, false, false),
    XNXX(1, "XNXX", "https://multi.xnxx.com/", R.drawable.ic_menu_xnxx, true, false, false, false),
    PORNPICS(2, "Pornpics", "https://www.pornpics.com/", R.drawable.ic_menu_pornpics, true, false, false, false),
    JPEGWORLD(3, "Jpegworld", "https://www.jpegworld.com/", R.drawable.ic_menu_jpegworld, true, false, false, false),
    NEXTPICTUREZ(4, "Nextpicturez", "http://www.nextpicturez.com/", R.drawable.ic_menu_nextpicturez, true, false, false, false),
    HELLPORNO(5, "Hellporno", "https://hellporno.com/albums/", R.drawable.ic_menu_hellporno, true, false, false, false),
    PORNPICGALLERIES(6, "Pornpicgalleries", "http://pornpicgalleries.com/", R.drawable.ic_menu_ppg, true, false, false, false),
    LINK2GALLERIES(7, "Link2galleries", "https://www.link2galleries.com/", R.drawable.ic_menu_l2g, true, false, false, false),
    REDDIT(8, "Reddit", "https://www.reddit.com/", R.drawable.ic_social_reddit, true, false, false, true),
    JJGIRLS(9, "JJGirls", "https://jjgirls.com/mobile/", R.drawable.ic_menu_jjgirls, true, false, true, false),
    LUSCIOUS(10, "luscious.net", "https://members.luscious.net/porn/", R.drawable.ic_menu_luscious, false, false, false, false),
    FAPALITY(11, "Fapality", "https://fapality.com/photos/", R.drawable.ic_menu_fapality, true, false, false, false),
    HINA(12, "Hina", "https://github.com/ixilia/hina", R.drawable.ic_menu_hina, true, false, false, false),
    NONE(98, "none", "", R.drawable.ic_external_library, true, false, false, false); // External library; fallback site


    private final int code;
    private final String description;
    private final String url;
    private final int ico;
    private final boolean useMobileAgent;
    private final boolean useHentoidAgent;
    private final boolean hasImageProcessing;
    private final boolean hasBackupURLs;
    private final boolean isDanbooru;

    Site(int code,
         String description,
         String url,
         int ico,
         boolean useMobileAgent,
         boolean useHentoidAgent,
         boolean hasImageProcessing,
         boolean hasBackupURLs,
         boolean isDanbooru) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
        this.useMobileAgent = useMobileAgent;
        this.useHentoidAgent = useHentoidAgent;
        this.hasImageProcessing = hasImageProcessing;
        this.hasBackupURLs = hasBackupURLs;
        this.isDanbooru = isDanbooru;
    }

    Site(int code,
         String description,
         String url,
         int ico) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
        this.useMobileAgent = true;
        this.useHentoidAgent = false;
        this.hasImageProcessing = false;
        this.hasBackupURLs = false;
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

    @Nullable
    public static Site searchByUrl(String url) {
        if (null == url || url.isEmpty()) {
            Timber.w("Invalid url");
            return null;
        }

        for (Site s : Site.values())
            if (s.code > 0 && HttpHelper.getDomainFromUri(url).equalsIgnoreCase(HttpHelper.getDomainFromUri(s.url)))
                return s;

        return Site.NONE;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public int getIco() {
        return ico;
    }

    public boolean useMobileAgent() {
        return useMobileAgent;
    }

    public boolean useHentoidAgent() {
        return useHentoidAgent;
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

    public String getUserAgent() {
        if (useMobileAgent())
            return HttpHelper.getMobileUserAgent(useHentoidAgent());
        else
            return HttpHelper.getDesktopUserAgent(useHentoidAgent());
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

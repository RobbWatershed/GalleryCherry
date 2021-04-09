package me.devsaki.hentoid.enums;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.json.JsonSiteSettings;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 */
public enum Site {

    XHAMSTER(0, "XHamster", "https://m.xhamster.com/photos/", R.drawable.ic_menu_xhamster),
    XNXX(1, "XNXX", "https://multi.xnxx.com/", R.drawable.ic_menu_xnxx),
    PORNPICS(2, "Pornpics", "https://www.pornpics.com/", R.drawable.ic_menu_pornpics),
    JPEGWORLD(3, "Jpegworld", "https://www.jpegworld.com/", R.drawable.ic_menu_jpegworld),
    NEXTPICTUREZ(4, "Nextpicturez", "http://www.nextpicturez.com/", R.drawable.ic_menu_nextpicturez),
    HELLPORNO(5, "Hellporno", "https://hellporno.com/albums/", R.drawable.ic_menu_hellporno, false, false, false, false, false, false), // Use desktop agent for Hellporno
    PORNPICGALLERIES(6, "Pornpicgalleries", "http://pornpicgalleries.com/", R.drawable.ic_menu_ppg),
    LINK2GALLERIES(7, "Link2galleries", "https://www.link2galleries.com/", R.drawable.ic_menu_l2g),
    REDDIT(8, "Reddit", "https://www.reddit.com/", R.drawable.ic_social_reddit, true, true, false, false, false, true), // Reddit is treated as a booru source
    JJGIRLS(9, "JJGirls (Jap)", "https://jjgirls.com/mobile/", R.drawable.ic_menu_jjgirls, true, true, false, true, false, false), // JJgirls uses the backup URL mechanism to fill in the gaps of fake image links leading to ads
    LUSCIOUS(10, "luscious.net", "https://members.luscious.net/porn/", R.drawable.ic_menu_luscious),
    FAPALITY(11, "Fapality", "https://fapality.com/photos/", R.drawable.ic_menu_fapality),
    HINA(12, "Hina", "https://github.com/ixilia/hina", R.drawable.ic_menu_hina),
    ASIANSISTER(13, "Asiansister", "https://asiansister.com/", R.drawable.ic_menu_asiansister),
    JJGIRLS2(14, "JJGirls (Western)", "https://jjgirls.com/pornpics/", R.drawable.ic_menu_jjgirls, true, true, false, true, false, false), // JJgirls uses the backup URL mechanism to fill in the gaps of fake image links leading to ads
    NONE(98, "none", "", R.drawable.ic_external_library); // External library; fallback site


    private static final Site[] INVISIBLE_SITES = {
            HINA, // Hardcoded link; should not be on display on dynamic sources
            NONE // Technical fallback
    };


    private final int code;
    private final String description;
    private final String url;
    private final int ico;
    // Default values are overridden in sites.json
    private final boolean useMobileAgent = true;
    private final boolean useHentoidAgent = false;
    private final boolean useWebviewAgent = true;
    private final boolean hasImageProcessing = false;
    private final boolean hasBackupURLs = false;
    private final boolean hasCoverBasedPageUpdates = false;
    private final boolean isDanbooru = false;

    Site(int code,
         String description,
         String url,
         int ico) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
    }

    Site(int code,
         String description,
         String url,
         int ico) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
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

    public boolean useWebviewAgent() {
        return useWebviewAgent;
    }

    public boolean hasImageProcessing() {
        return hasImageProcessing;
    }

    public boolean hasBackupURLs() {
        return hasBackupURLs;
    }

    public boolean hasCoverBasedPageUpdates() {
        return hasCoverBasedPageUpdates;
    }

    public boolean isDanbooru() {
        return isDanbooru;
    }

    public boolean isVisible() {
        for (Site s : INVISIBLE_SITES) if (s.equals(this)) return false;
        return true;
    }


    public String getFolder() {
        return description;
    }

    public String getUserAgent() {
        if (useMobileAgent())
            return HttpHelper.getMobileUserAgent(useHentoidAgent(), useWebviewAgent());
        else
            return HttpHelper.getDesktopUserAgent(useHentoidAgent(), useWebviewAgent());
    }

    public void updateFrom(@NonNull final JsonSiteSettings.JsonSite jsonSite) {
        if (jsonSite.useMobileAgent != null) useMobileAgent = jsonSite.useMobileAgent;
        if (jsonSite.useHentoidAgent != null) useHentoidAgent = jsonSite.useHentoidAgent;
        if (jsonSite.useWebviewAgent != null) useWebviewAgent = jsonSite.useWebviewAgent;
        if (jsonSite.hasImageProcessing != null) hasImageProcessing = jsonSite.hasImageProcessing;
        if (jsonSite.hasBackupURLs != null) hasBackupURLs = jsonSite.hasBackupURLs;
        if (jsonSite.hasCoverBasedPageUpdates != null)
            hasCoverBasedPageUpdates = jsonSite.hasCoverBasedPageUpdates;
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

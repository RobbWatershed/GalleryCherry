package me.devsaki.hentoid.enums;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.json.core.JsonSiteSettings;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Site enumerator
 */
public enum Site {

    XHAMSTER(0, "XHamster", "https://m.xhamster.com/photos/", R.drawable.ic_menu_xhamster),
    XNXX(1, "XNXX", "https://multi.xnxx.com/", R.drawable.ic_menu_xnxx),
    PORNPICS(2, "Pornpics", "https://www.pornpics.com/", R.drawable.ic_menu_pornpics),
    JPEGWORLD(3, "Jpegworld", "https://www.jpegworld.com/", R.drawable.ic_menu_jpegworld),
    NEXTPICTUREZ(4, "Nextpicturez", "http://www.nextpicturez.com/", R.drawable.ic_menu_nextpicturez),
    HELLPORNO(5, "Hellporno", "https://hellporno.com/albums/", R.drawable.ic_menu_hellporno),
    PORNPICGALLERIES(6, "Pornpicgalleries", "http://pornpicgalleries.com/", R.drawable.ic_menu_ppg),
    LINK2GALLERIES(7, "Link2galleries", "https://www.link2galleries.com/", R.drawable.ic_menu_l2g),
    REDDIT(8, "Reddit", "https://www.reddit.com/", R.drawable.ic_social_reddit),
    JJGIRLS(9, "JJGirls (Jap)", "https://jjgirls.com/mobile/", R.drawable.ic_menu_jjgirls),
    LUSCIOUS(10, "luscious.net", "https://members.luscious.net/porn/", R.drawable.ic_site_luscious),
    FAPALITY(11, "Fapality", "https://fapality.com/photos/", R.drawable.ic_menu_fapality),
    HINA(12, "Hina", "https://github.com/ixilia/hina", R.drawable.ic_menu_hina),
    ASIANSISTER(13, "Asiansister", "https://asiansister.com/", R.drawable.ic_menu_asiansister),
    JJGIRLS2(14, "JJGirls (Western)", "https://jjgirls.com/pornpics/", R.drawable.ic_menu_jjgirls),
    BABETODAY(15, "Babe.today", "https://babe.today/", R.drawable.ic_menu_jjgirls),
    NONE(98, "none", "", R.drawable.ic_attribute_source); // External library; fallback site

    private static final Site[] INVISIBLE_SITES = {
            HELLPORNO, // Removed their pictures section
            HINA, // Hardcoded link; should not be on display on dynamic sources
            JJGIRLS2, // Abandoned in favour of babe.today
            HINA, // Dead service
            ASIANSISTER, // Redirected to sisterasian.com; only hosts videos now
            NONE // Technical fallback
    };


    private final int code;
    private final String description;
    private final String url;
    private final int ico;
    // Default values are overridden in sites.json
    private boolean useMobileAgent = true;
    private boolean useHentoidAgent = false;
    private boolean useWebviewAgent = true;
    // Download behaviour control
    private boolean hasBackupURLs = false;
    private boolean hasCoverBasedPageUpdates = false;
    private boolean isDanbooru = false;
    private boolean useCloudflare = false;
    private int requestsCapPerSecond = -1;
    private int parallelDownloadCap = 0;
    // Controls for "Mark downloaded/merged" in browser
    private int bookCardDepth = 2;
    private Set<String> bookCardExcludedParentClasses = new HashSet<>();
    //Controls for "Mark books with blocked tags" in browser
    private int galleryHeight = -1;

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

    public boolean hasBackupURLs() {
        return hasBackupURLs;
    }

    public boolean hasCoverBasedPageUpdates() {
        return hasCoverBasedPageUpdates;
    }

    public boolean isDanbooru() {
        return isDanbooru;
    }

    public boolean isUseCloudflare() {
        return useCloudflare;
    }

    public int getRequestsCapPerSecond() {
        return requestsCapPerSecond;
    }

    public int getParallelDownloadCap() {
        return parallelDownloadCap;
    }

    public int getBookCardDepth() {
        return bookCardDepth;
    }

    public Set<String> getBookCardExcludedParentClasses() {
        return bookCardExcludedParentClasses;
    }

    public int getGalleryHeight() {
        return galleryHeight;
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
        if (jsonSite.hasBackupURLs != null) hasBackupURLs = jsonSite.hasBackupURLs;
        if (jsonSite.hasCoverBasedPageUpdates != null)
            hasCoverBasedPageUpdates = jsonSite.hasCoverBasedPageUpdates;
        if (jsonSite.isDanbooru != null) isDanbooru = jsonSite.isDanbooru;
        if (jsonSite.useCloudflare != null)
            useCloudflare = jsonSite.useCloudflare;
        if (jsonSite.parallelDownloadCap != null)
            parallelDownloadCap = jsonSite.parallelDownloadCap;
        if (jsonSite.requestsCapPerSecond != null)
            requestsCapPerSecond = jsonSite.requestsCapPerSecond;
        if (jsonSite.bookCardDepth != null)
            bookCardDepth = jsonSite.bookCardDepth;
        if (jsonSite.bookCardExcludedParentClasses != null)
            bookCardExcludedParentClasses = new HashSet<>(jsonSite.bookCardExcludedParentClasses);
        if (jsonSite.galleryHeight != null)
            galleryHeight = jsonSite.galleryHeight;
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

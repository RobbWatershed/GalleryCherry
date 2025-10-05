package me.devsaki.hentoid.enums

import io.objectbox.converter.PropertyConverter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.json.core.JsonSiteSettings.JsonSite
import me.devsaki.hentoid.util.network.getDesktopUserAgent
import me.devsaki.hentoid.util.network.getDomainFromUri
import me.devsaki.hentoid.util.network.getMobileUserAgent
import timber.log.Timber


// Safe-for-work/wife/gf option; not used anymore and kept here for retrocompatibility
private val INVISIBLE_SITES = setOf(
    Site.HELLPORNO, // Removed their pictures section
    Site.JJGIRLS2, // Abandoned in favour of babe.today
    Site.HINA, // Dead service
    Site.ASIANSISTER, // Redirected to sisterasian.com; only hosts videos now
    Site.JPEGWORLD, // Dead site
    Site.NEXTPICTUREZ, // Dead site
    Site.PORNPICGALLERIES, // Dead site
    Site.ANY, // Technical fallback
    Site.NONE // Technical fallback
)

enum class Site(val code: Int, val description: String, val url: String, val ico: Int) {
    XHAMSTER(0, "XHamster", "https://m.xhamster.com/photos/", R.drawable.ic_site_xhamster),
    XNXX(1, "XNXX", "https://multi.xnxx.com/", R.drawable.ic_site_xnxx),
    PORNPICS(2, "Pornpics", "https://www.pornpics.com/", R.drawable.ic_site_pornpics),
    JPEGWORLD(3, "Jpegworld", "https://www.jpegworld.com/", R.drawable.ic_site_jpegworld),
    NEXTPICTUREZ(
        4,
        "Nextpicturez",
        "http://www.nextpicturez.com/",
        R.drawable.ic_site_nextpicturez
    ),
    HELLPORNO(5, "Hellporno", "https://hellporno.com/albums/", R.drawable.ic_site_hellporno),
    PORNPICGALLERIES(6, "Pornpicgalleries", "http://pornpicgalleries.com/", R.drawable.ic_site_ppg),
    LINK2GALLERIES(7, "Link2galleries", "https://www.link2galleries.com/", R.drawable.ic_site_l2g),
    REDDIT(8, "Reddit", "https://www.reddit.com/", R.drawable.ic_social_reddit),
    JJGIRLS(9, "JJGirls (Jap)", "https://jjgirls.com/mobile/", R.drawable.ic_site_jjgirls),
    LUSCIOUS(10, "luscious.net", "https://members.luscious.net/porn/", R.drawable.ic_site_luscious),
    FAPALITY(11, "Fapality", "https://fapality.com/photos/", R.drawable.ic_site_fapality),
    HINA(12, "Hina", "https://github.com/ixilia/hina", R.drawable.ic_site_hina),
    ASIANSISTER(13, "Asiansister", "https://asiansister.com/", R.drawable.ic_site_asiansister),
    JJGIRLS2(14, "JJGirls (Western)", "https://jjgirls.com/pornpics/", R.drawable.ic_site_jjgirls),
    BABETODAY(15, "Babe.today", "https://babe.today/", R.drawable.ic_site_jjgirls),
    JAPBEAUTIES(
        16,
        "Japanese beauties",
        "https://japanesebeauties.one/",
        R.drawable.ic_cherry_icon
    ),
    SXYPIX(17, "SXYPIX", "https://sxypix.com/", R.drawable.ic_site_sxypix),
    PICS_X(18, "PICS-X", "https://pics-x.com/", R.drawable.ic_site_pics_x),
    COSPLAYTELE(19, "CosplayTele", "https://cosplaytele.com/", R.drawable.ic_fav_full),

    // Used for associating attributes to sites in Preferences
    ANY(97, "any", "", R.drawable.ic_cherry_icon),
    NONE(98, "none", "", R.drawable.ic_attribute_source); // External library; fallback site

    // Default values overridden in sites.json
    var useMobileAgent = true
        private set
    var useHentoidAgent = false
        private set
    var useWebviewAgent = true
        private set

    // Download behaviour control
    var hasBackupURLs = false
        private set
    var hasCoverBasedPageUpdates = false
        private set
    var useCloudflare = false
        private set
    var hasUniqueBookId = false
        private set
    var requestsCapPerSecond = -1
        private set
    var parallelDownloadCap = 0
        private set

    // Controls for "Mark downloaded/merged" in browser
    var bookCardDepth = 2
        private set
    var bookCardExcludedParentClasses: Set<String> = HashSet()
        private set

    // Controls for "Mark books with blocked tags" in browser
    var galleryHeight = -1
        private set

    // Determine which Jsoup output to use when rewriting the HTML
    // 0 : html; 1 : xml
    var jsoupOutputSyntax = 0
        private set


    val isVisible: Boolean
        get() {
            return !INVISIBLE_SITES.contains(this)
        }

    val folder: String
        get() {
            return description
        }

    val userAgent: String
        get() {
            return if (useMobileAgent) getMobileUserAgent(useHentoidAgent, useWebviewAgent)
            else getDesktopUserAgent(useHentoidAgent, useWebviewAgent)
        }


    fun updateFrom(jsonSite: JsonSite) {
        if (jsonSite.useMobileAgent != null) useMobileAgent = jsonSite.useMobileAgent
        if (jsonSite.useHentoidAgent != null) useHentoidAgent = jsonSite.useHentoidAgent
        if (jsonSite.useWebviewAgent != null) useWebviewAgent = jsonSite.useWebviewAgent
        if (jsonSite.hasBackupURLs != null) hasBackupURLs = jsonSite.hasBackupURLs
        if (jsonSite.hasCoverBasedPageUpdates != null)
            hasCoverBasedPageUpdates = jsonSite.hasCoverBasedPageUpdates
        if (jsonSite.useCloudflare != null) useCloudflare = jsonSite.useCloudflare
        if (jsonSite.hasUniqueBookId != null) hasUniqueBookId = jsonSite.hasUniqueBookId
        if (jsonSite.parallelDownloadCap != null) parallelDownloadCap = jsonSite.parallelDownloadCap
        if (jsonSite.requestsCapPerSecond != null)
            requestsCapPerSecond = jsonSite.requestsCapPerSecond
        if (jsonSite.bookCardDepth != null) bookCardDepth = jsonSite.bookCardDepth
        if (jsonSite.bookCardExcludedParentClasses != null)
            bookCardExcludedParentClasses =
                java.util.HashSet(jsonSite.bookCardExcludedParentClasses)
        if (jsonSite.galleryHeight != null) galleryHeight = jsonSite.galleryHeight
        if (jsonSite.jsoupOutputSyntax != null) jsoupOutputSyntax = jsonSite.jsoupOutputSyntax
    }

    class SiteConverter : PropertyConverter<Site, Long?> {
        override fun convertToEntityProperty(databaseValue: Long?): Site {
            if (databaseValue == null) return NONE
            for (site in Site.entries) if (site.code.toLong() == databaseValue) return site
            return NONE
        }

        override fun convertToDatabaseValue(entityProperty: Site): Long {
            return entityProperty.code.toLong()
        }
    }

    companion object {
        fun searchByCode(code: Int): Site {
            for (s in Site.entries) if (s.code == code) return s
            return NONE
        }

        // Same as ValueOf with a fallback to NONE
        // (vital for forward compatibility)
        fun searchByName(name: String?): Site {
            for (s in Site.entries) if (s.name.equals(name, ignoreCase = true)) return s
            return NONE
        }

        fun searchByUrl(url: String?): Site? {
            if (url.isNullOrEmpty()) {
                Timber.w("Invalid url")
                return null
            }

            for (s in Site.entries) if (s.code > 0 && getDomainFromUri(url).equals(
                    getDomainFromUri(s.url), ignoreCase = true
                )
            ) return s

            return NONE
        }
    }
}
package me.devsaki.hentoid.util

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.text.TextUtils
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.core.ResetLazy
import me.devsaki.hentoid.core.lazyWithReset
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.enums.Theme
import me.devsaki.hentoid.util.network.Source
import java.util.regex.Pattern
import kotlin.reflect.KProperty

/**
 * Decorator class that wraps a SharedPreference to implement properties
 * Some properties do not have a setter because it is changed by PreferenceActivity
 * Some properties are parsed as ints because of limitations with the Preference subclass used
 */
object Settings {
    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    fun performHousekeeping() {
        // PIN activation -> Lock type (v1.18.4)
        if (sharedPreferences.contains(Key.APP_LOCK)) {
            if (!appLockPin.isEmpty()) lockType = 1
        }
        // Auto rotate switch -> Auto rotate direction (v1.20.6)
        if (sharedPreferences.contains(Key.VIEWER_AUTO_ROTATE_OLD)) {
            val autoRotate = sharedPreferences.getBoolean(Key.VIEWER_AUTO_ROTATE_OLD, false)
            sharedPreferences.edit { remove(Key.VIEWER_AUTO_ROTATE_OLD) }
            readerAutoRotate =
                if (autoRotate) Value.READER_AUTO_ROTATE_LEFT else Value.READER_AUTO_ROTATE_NONE
        }
    }

    fun extractPortableInformation(): Map<String, Any> {
        val result: MutableMap<String, Any?> = HashMap(sharedPreferences.all)

        // Remove non-exportable settings that make no sense on another instance
        result.remove(Key.FIRST_RUN)
        result.remove(Key.WELCOME_DONE)
        result.remove(Key.PRIMARY_STORAGE_URI)
        result.remove(Key.EXTERNAL_LIBRARY_URI)
        result.remove(Key.LAST_KNOWN_APP_VERSION_CODE)
        result.remove(Key.REFRESH_JSON_1_DONE)
        result.remove(Key.LOCK_TYPE)
        result.remove(Key.ACHIEVEMENTS)
        result.remove(Key.ACHIEVEMENTS_NB_AI_RESCALE)

        return result.filterValues { it != null }.mapValues { it -> it.value as Any }
    }

    fun importInformation(settings: Map<String, Any?>) {
        settings.entries.forEach {
            it.value?.let { value ->
                when (value) {
                    is Int -> {
                        sharedPreferences.edit { putInt(it.key, value) }
                    }

                    is String -> {
                        sharedPreferences.edit { putString(it.key, value) }
                    }

                    is Boolean -> {
                        sharedPreferences.edit { putBoolean(it.key, value) }
                    }

                    is Float -> {
                        sharedPreferences.edit { putFloat(it.key, value) }
                    }

                    is Long -> {
                        sharedPreferences.edit { putLong(it.key, value) }
                    }
                }
            }
        }
    }

    fun makeSiteKey(key: String, site: Site): String {
        return if (site == Site.ANY || site == Site.NONE) key
        else "$key.${site.name}"
    }

    /**
     * FIELDS
     */
    // IMPORT
    val isImportQueueEmptyBooks: Boolean by BoolSetting(Key.IMPORT_QUEUE_EMPTY, false)

    private val importExtRgxLazyHandler: ResetLazy<Triple<Pattern, Boolean, Boolean>> =
        lazyWithReset { patternToRegex(m_importExtNamePattern) }
    val importExtRgx: Triple<Pattern, Boolean, Boolean> by importExtRgxLazyHandler

    fun setImportExtNamePattern(value: String) {
        m_importExtNamePattern = value
        importExtRgxLazyHandler.reset()
    }

    fun getImportExtNamePattern(): String {
        return m_importExtNamePattern
    }

    private var m_importExtNamePattern: String by StringSetting(
        "import_external_name_pattern",
        Default.IMPORT_NAME_PATTERN
    )

    // LIBRARY
    var libraryDisplay: Int by IntSettingStr(Key.LIBRARY_DISPLAY, Default.LIBRARY_DISPLAY)
    var libraryDisplayGridFav: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_FAV, true)
    var libraryDisplayGridRating: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_RATING, true)
    var libraryDisplayGridSource: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_SOURCE, true)
    var libraryDisplayGridStorageInfo: Boolean by BoolSetting(
        Key.LIBRARY_DISPLAY_GRID_STORAGE,
        true
    )
    var libraryDisplayGridTitle: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_TITLE, true)
    var libraryDisplayGridLanguage: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_LANG, true)
    var libraryGridCardWidthDP: Int by IntSettingStr(Key.LIBRARY_GRID_CARD_WIDTH, 150)
    var libraryDisplayGroupFigure: Int by IntSettingStr(
        Key.LIBRARY_DISPLAY_GROUP_FIGURE,
        Value.LIBRARY_DISPLAY_GROUP_NB_BOOKS
    )
    var activeSites: List<Site> by ListSiteSetting(Key.ACTIVE_SITES, Value.ACTIVE_SITES)
    var contentSortField: Int by IntSetting(
        "pref_order_content_field",
        Default.ORDER_CONTENT_FIELD
    )
    var isContentSortDesc: Boolean by BoolSetting("pref_order_content_desc", false)
    var groupSortField: Int by IntSetting("pref_order_group_field", Default.ORDER_GROUP_FIELD)
    var isGroupSortDesc: Boolean by BoolSetting("pref_order_group_desc", false)
    var contentPageQuantity: Int by IntSettingStr("pref_quantity_per_page_lists", 20)
    val endlessScroll: Boolean by BoolSetting(Key.ENDLESS_SCROLL, true)
    var topFabEnabled: Boolean by BoolSetting(Key.TOP_FAB, true)
    var groupingDisplay: Int by IntSettingStr(Key.GROUPING_DISPLAY, Grouping.FLAT.id)
    fun getGroupingDisplayG(): Grouping {
        return Grouping.Companion.searchById(groupingDisplay)
    }
    val navigationNostalgiaMode: Boolean by BoolSetting(Key.NOSTALGIA_MODE, false)

    var artistGroupVisibility: Int by IntSettingStr(
        "artist_group_visibility",
        Value.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS
    )
    var folderSortField: Int by IntSetting("pref_order_folder_field", Default.ORDER_FOLDER_FIELD)
    var isFolderSortDesc: Boolean by BoolSetting("pref_order_folder_desc", false)
    var libraryFoldersRoots: List<String> by ListStringSetting("library_folders_roots")
    var libraryFoldersRoot: String by StringSetting("library_folders_current_root", "")

    // ADV SEARCH
    val searchAttributesSortOrder: Int by IntSettingStr(
        "pref_order_attribute_lists",
        Value.SEARCH_ORDER_ATTRIBUTES_COUNT
    )
    val searchAttributesCount: Boolean by BoolSetting("pref_order_attribute_count", true)

    // LOCK
    var lockType: Int by IntSettingStr(Key.LOCK_TYPE, 0)
    var appLockPin: String by StringSetting(Key.APP_LOCK, "")
    var lockOnAppRestore: Boolean by BoolSetting("pref_lock_on_app_restore", false)
    var lockTimer: Int by IntSettingStr("pref_lock_timer", Value.LOCK_TIMER_30S)

    // MASS OPERATIONS
    var massOperation: Int by IntSettingStr("MASS_OPERATION", 0)
    var massOperationScope: Int by IntSettingStr("MASS_SCOPE", 0)

    // TRANSFORM
    var isResizeEnabled: Boolean by BoolSetting("TRANSFORM_RESIZE_ENABLED", false)
    var resizeMethod: Int by IntSettingStr("TRANSFORM_RESIZE_METHOD", 0)
    var resizeMethod1Ratio: Int by IntSettingStr("TRANSFORM_RESIZE_1_RATIO", 120)
    var resizeMethod2Height: Int by IntSettingStr("TRANSFORM_RESIZE_2_HEIGHT", 0)
    var resizeMethod2Width: Int by IntSettingStr("TRANSFORM_RESIZE_2_WIDTH", 0)
    var resizeMethod3Ratio: Int by IntSettingStr("TRANSFORM_RESIZE_3_RATIO", 80)
    var resizeMethod5Images: Int by IntSettingStr("TRANSFORM_RESIZE_5_IMAGES", 15)
    var transcodeMethod: Int by IntSettingStr("TRANSFORM_TRANSCODE_METHOD", 0)
    var transcodeEncoderAll: Int by IntSettingStr(
        "TRANSFORM_TRANSCODE_ENC_ALL",
        PictureEncoder.PNG.value
    )
    var transcodeEncoderLossless: Int by IntSettingStr(
        "TRANSFORM_TRANSCODE_ENC_LOSSLESS",
        PictureEncoder.PNG.value
    )
    var transcodeEncoderLossy: Int by IntSettingStr(
        "TRANSFORM_TRANSCODE_ENC_LOSSY",
        PictureEncoder.JPEG.value
    )
    var transcodeQuality: Int by IntSettingStr("TRANSFORM_TRANSCODE_QUALITY", 90)

    // ARCHIVES
    var archiveTargetFolder: String by StringSetting(
        "ARCHIVE_TARGET_FOLDER",
        Value.TARGET_FOLDER_DOWNLOADS
    )
    var latestArchiveTargetFolderUri: String by StringSetting("ARCHIVE_TARGET_FOLDER_LATEST", "")
    var archiveTargetFormat: Int by IntSettingStr("ARCHIVE_TARGET_FORMAT", 0)
    var pdfBackgroundColor: Int by IntSettingStr("ARCHIVE_PDF_BGCOLOR", 0)
    var isArchiveOverwrite: Boolean by BoolSetting("ARCHIVE_OVERWRITE", true)
    var isArchiveDeleteOnSuccess: Boolean by BoolSetting("ARCHIVE_DELETE_ON_SUCCESS", false)

    // BROWSER
    fun isBrowserAugmented(site: Site): Boolean {
        return sharedPreferences.getBoolean(
            makeSiteKey(Key.WEB_AUGMENTED_BROWSER, site),
            isAppBrowserAugmented
        )
    }

    fun setBrowserAugmented(site: Site, value: Boolean) {
        sharedPreferences.edit { putBoolean(makeSiteKey(Key.WEB_AUGMENTED_BROWSER, site), value) }
    }

    var isAppBrowserAugmented: Boolean by BoolSetting(Key.WEB_AUGMENTED_BROWSER, true)
    fun isAdBlockerOn(site: Site): Boolean {
        return sharedPreferences.getBoolean(makeSiteKey(Key.WEB_ADBLOCKER, site), isAppAdBlockerOn)
    }

    fun setAdBlockerOn(site: Site, value: Boolean) {
        sharedPreferences.edit { putBoolean(makeSiteKey(Key.WEB_ADBLOCKER, site), value) }
    }

    var isAppAdBlockerOn: Boolean by BoolSetting(Key.WEB_ADBLOCKER, true)
    var isBrowserForceLightMode: Boolean by BoolSetting(Key.WEB_FORCE_LIGHTMODE, false)
    var isBrowserLanguageFilter: Boolean by BoolSetting("pref_browser_language_filter", false)
    var browserLanguageFilterValue: String by StringSetting("pref_language_filter_value", "english")
    val isBrowserLockFavPanel: Boolean by BoolSetting(Key.WEB_LOCK_FAVS_PANEL, false)
    var blockedTags: List<String> by ListStringSetting(Key.DL_BLOCKED_TAGS)
    val isBrowserResumeLast: Boolean by BoolSetting("pref_browser_resume_last", false)
    val isBrowserMarkDownloaded: Boolean by BoolSetting(Key.BROWSER_MARK_DOWNLOADED, false)
    val isBrowserMarkMerged: Boolean by BoolSetting(Key.BROWSER_MARK_MERGED, false)
    val isBrowserMarkQueued: Boolean by BoolSetting(Key.BROWSER_MARK_QUEUED, false)
    val isBrowserMarkBlockedTags: Boolean by BoolSetting(Key.BROWSER_MARK_BLOCKED, false)

    private val browserDlActionInt by IntSettingStr(Key.BROWSER_DL_ACTION, Value.DL_ACTION_DL_PAGES)
    fun getBrowserDlAction(): DownloadMode {
        return DownloadMode.Companion.fromValue(browserDlActionInt)
    }

    val isBrowserQuickDl: Boolean by BoolSetting(Key.BROWSER_QUICK_DL, true)
    val browserQuickDlThreshold: Int by IntSettingStr(
        Key.BROWSER_QUICK_DL_THRESHOLD,
        1500 // 1.5s
    )
    val isBrowserNhentaiInvisibleBlacklist: Boolean by BoolSetting(
        Key.BROWSER_NHENTAI_INVISIBLE_BLACKLIST,
        false
    )
    val http429DefaultDelaySecs: Int by IntSettingStr(Key.DL_HTTP_429_DEFAULT_DELAY, 120)
    val dnsOverHttps: Int by IntSettingStr(
        Key.BROWSER_DNS_OVER_HTTPS,
        Source.NONE.value // No DNS
    )

    // QUEUE / DOWNLOADER
    val isDownloadEhHires: Boolean by BoolSetting("pref_dl_eh_hires", false)
    val isDownloadHitomiAvif: Boolean by BoolSetting("pref_dl_hitomi_avif", false)
    fun getDownloadThreadCount(site: Site): Int {
        return (sharedPreferences.getString(
            makeSiteKey(Key.DL_THREADS_QUANTITY_LISTS, site),
            appDownloadThreadCount.toString()
        ) + "").toInt()
    }

    private val appDownloadThreadCount: Int by IntSettingStr(
        Key.DL_THREADS_QUANTITY_LISTS,
        Value.DOWNLOAD_THREAD_COUNT_AUTO
    )
    var downloadDuplicateAsk: Boolean by BoolSetting("download_duplicate_ask", true)
    var downloadPlusDuplicateTry: Boolean by BoolSetting("download_plus_duplicate_try", true)
    val isQueueAutostart: Boolean by BoolSetting("pref_queue_autostart", true)
    val isQueueWifiOnly: Boolean by BoolSetting("pref_queue_wifi_only", false)
    val isDownloadLargeOnlyWifi: Boolean by BoolSetting("pref_dl_size_wifi", false)
    val downloadLargeOnlyWifiThresholdMB: Int by IntSettingStr("pref_dl_size_wifi_threshold", 40)
    val downloadLargeOnlyWifiThresholdPages: Int by IntSettingStr(
        "pref_dl_pages_wifi_threshold",
        999999
    )
    val isDlRetriesActive: Boolean by BoolSetting("pref_dl_retries_active", false)
    val dlRetriesNumber: Int by IntSettingStr("pref_dl_retries_number", 5)
    val dlRetriesMemLimit: Int by IntSettingStr("pref_dl_retries_mem_limit", 100)
    val dlSpeedCap: Int by IntSettingStr(Key.DL_SPEED_CAP, Value.DL_SPEED_CAP_NONE)
    val queueNewDownloadPosition: Int by IntSettingStr(
        "pref_queue_new_position",
        Default.QUEUE_NEW_DOWNLOADS_POSITION
    )
    val tagBlockingBehaviour: Int by IntSettingStr(
        "pref_dl_blocked_tags_behaviour",
        Value.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE
    )

    // READER
    var isReaderResumeLastLeft: Boolean by BoolSetting("pref_viewer_resume_last_left", true)
    var isReaderKeepScreenOn: Boolean by BoolSetting(Key.VIEWER_KEEP_SCREEN_ON, true)
    var isReaderDisplayAroundNotch: Boolean by BoolSetting(Key.VIEWER_DISPLAY_AROUND_NOTCH, true)

    var readerColorDepth: Int by IntSettingStr(Key.READER_COLOR_DEPTH, 0)
    private var reader2PagesMode: Boolean by BoolSetting(Key.READER_TWOPAGES, false)

    fun getContent2PagesMode(bookPrefs: Map<String, String>): Boolean {
        return bookPrefs.getOrDefault(Key.READER_TWOPAGES, reader2PagesMode.toString()).toBoolean()
    }

    fun getContentDisplayMode(site: Site, bookPrefs: Map<String, String>): Int {
        if (Value.VIEWER_ORIENTATION_HORIZONTAL == getContentOrientation(site, bookPrefs)) {
            if (bookPrefs.containsKey(Key.VIEWER_IMAGE_DISPLAY)) {
                val value = bookPrefs[Key.VIEWER_IMAGE_DISPLAY]
                if (value != null) return value.toInt()
            }
            return readerDisplayMode
        } else return Value.VIEWER_DISPLAY_FIT // The only relevant mode for vertical (aka. webtoon) display
    }

    val readerDisplayMode: Int by IntSettingStr(Key.VIEWER_IMAGE_DISPLAY, Value.VIEWER_DISPLAY_FIT)

    fun getContentBrowseMode(site: Site, bookPrefs: Map<String, String>?): Int {
        bookPrefs?.let {
            if (it.containsKey(Key.VIEWER_BROWSE_MODE)) {
                val value = it[Key.VIEWER_BROWSE_MODE]
                if (value != null) return value.toInt()
            }
        }
        return getReaderBrowseMode(site)
    }

    fun getReaderBrowseMode(site: Site): Int {
        return (sharedPreferences.getString(
            makeSiteKey(Key.VIEWER_BROWSE_MODE, site),
            appReaderBrowseMode.toString()
        ) + "").toInt()
    }

    var appReaderBrowseMode: Int by IntSettingStr(Key.VIEWER_BROWSE_MODE, Value.VIEWER_BROWSE_NONE)

    fun getContentDirection(site: Site, bookPrefs: Map<String, String>?): Int {
        return if ((getContentBrowseMode(
                site,
                bookPrefs
            ) == Value.VIEWER_BROWSE_RTL)
        ) Value.VIEWER_DIRECTION_RTL else Value.VIEWER_DIRECTION_LTR
    }

    private fun getContentOrientation(site: Site, bookPrefs: Map<String, String>): Int {
        return if ((getContentBrowseMode(
                site,
                bookPrefs
            ) == Value.VIEWER_BROWSE_TTB)
        ) Value.VIEWER_ORIENTATION_VERTICAL else Value.VIEWER_ORIENTATION_HORIZONTAL
    }

    fun isContentSmoothRendering(bookPrefs: Map<String, String>): Boolean {
        if (bookPrefs.containsKey(Key.VIEWER_RENDERING)) {
            val value = bookPrefs[Key.VIEWER_RENDERING]
            if (value != null) return isSmoothRendering(value.toInt())
        }
        return isReaderSmoothRendering()
    }

    fun isReaderSmoothRendering(): Boolean {
        return isSmoothRendering(readerRenderingMode)
    }

    private fun isSmoothRendering(mode: Int): Boolean {
        return (mode == Value.VIEWER_RENDERING_SMOOTH)
    }

    private val readerRenderingMode: Int by IntSettingStr(
        Key.VIEWER_RENDERING,
        Value.VIEWER_RENDERING_SHARP
    )
    val isReaderDisplayPageNum: Boolean by BoolSetting(Key.VIEWER_DISPLAY_PAGENUM, false)
    val isReaderTapTransitions: Boolean by BoolSetting("pref_viewer_tap_transitions", true)
    val isReaderZoomTransitions: Boolean by BoolSetting(Key.VIEWER_ZOOM_TRANSITIONS, true)
    val isReaderSwipeToFling: Boolean by BoolSetting(Key.VIEWER_SWIPE_TO_FLING, false)
    val isReaderInvertVolumeRocker: Boolean by BoolSetting(
        "pref_viewer_invert_volume_rocker",
        false
    )
    val isReaderTapToTurn: Boolean by BoolSetting("pref_viewer_page_turn_tap", true)
    val isReaderTapToTurn2x: Boolean by BoolSetting("pref_viewer_page_turn_tap_2x", false)
    val isReaderVolumeToTurn: Boolean by BoolSetting("pref_viewer_page_turn_volume", true)
    val isReaderSwipeToTurn: Boolean by BoolSetting(Key.VIEWER_PAGE_TURN_SWIPE, true)
    val isReaderKeyboardToTurn: Boolean by BoolSetting("pref_viewer_page_turn_keyboard", true)
    val isReaderVolumeToSwitchBooks: Boolean by BoolSetting("pref_viewer_book_switch_volume", false)
    val isReaderOpenBookInGalleryMode: Boolean by BoolSetting("pref_viewer_open_gallery", false)
    val isReaderChapteredNavigation: Boolean by BoolSetting("viewer_chaptered_navigation", false)
    val isReaderContinuous: Boolean by BoolSetting(Key.VIEWER_CONTINUOUS, false)
    val readerPageReadThreshold: Int by IntSettingStr(
        "pref_viewer_read_threshold",
        Value.VIEWER_READ_THRESHOLD_1
    )
    val readerRatioCompletedThreshold: Int by IntSettingStr(
        "pref_viewer_ratio_completed_threshold",
        Value.VIEWER_COMPLETED_RATIO_THRESHOLD_NONE
    )
    var readerSlideshowDelay: Int by IntSettingStr(
        "pref_viewer_slideshow_delay",
        Value.VIEWER_SLIDESHOW_DELAY_2
    )
    var readerSlideshowDelayVertical: Int by IntSettingStr(
        "pref_viewer_slideshow_delay_vertical",
        Value.VIEWER_SLIDESHOW_DELAY_2
    )
    val readerSeparatingBars: Int by IntSettingStr(
        Key.VIEWER_SEPARATING_BARS,
        Value.VIEWER_SEPARATING_BARS_OFF
    )
    val isReaderDoubleTapToZoom: Boolean by BoolSetting(Key.VIEWER_DOUBLE_TAP_TO_ZOOM, true)
    val isReaderHoldToZoom: Boolean by BoolSetting(Key.VIEWER_HOLD_TO_ZOOM, false)
    val readerCapTapZoom: Int by IntSettingStr(
        "pref_viewer_cap_tap_zoom",
        Value.VIEWER_CAP_TAP_ZOOM_NONE
    )
    val isReaderMaintainHorizontalZoom: Boolean by BoolSetting(
        "pref_viewer_maintain_horizontal_zoom",
        false
    )
    var readerAutoRotate: Int by IntSettingStr(
        Key.VIEWER_AUTO_ROTATE,
        Value.READER_AUTO_ROTATE_NONE
    )
    var readerCurrentContent: Long by LongSetting("viewer_current_content", -1)
    var readerGalleryColumns: Int by IntSettingStr("viewer_gallery_columns", 4)
    var readerDeleteAskMode: Int by IntSettingStr(
        Key.VIEWER_DELETE_ASK_MODE,
        Value.VIEWER_DELETE_ASK_AGAIN
    )
    var readerDeleteTarget: Int by IntSettingStr(
        Key.VIEWER_DELETE_TARGET,
        Value.VIEWER_DELETE_TARGET_PAGE
    )
    var readerSlideshowLoop: Int by IntSettingStr(
        "viewer_slideshow_loop",
        Value.VIEWER_SLIDESHOW_LOOP_NONE
    )
    var readerTargetFolder: String by StringSetting(
        "READER_TARGET_FOLDER",
        Value.TARGET_FOLDER_DOWNLOADS
    )
    var latestReaderTargetFolderUri: String by StringSetting("READER_TARGET_FOLDER_LATEST", "")
    val isReaderSmartCrop: Boolean by BoolSetting(Key.READER_SMART_CROP, false)

    // METADATA & RULES EDITOR
    var ruleSortField: Int by IntSetting("pref_order_rule_field", Value.ORDER_FIELD_SOURCE_NAME)
    var isRuleSortDesc: Boolean by BoolSetting("pref_order_rule_desc", false)

    // ACHIEVEMENTS
    var achievements: ULong by ULongSetting(Key.ACHIEVEMENTS, 0UL)
    var nbAIRescale: Int by IntSettingStr(Key.ACHIEVEMENTS_NB_AI_RESCALE, 0)

    // STORAGE / IMPORT
    private var storageUri: String by StringSetting(Key.PRIMARY_STORAGE_URI, "")
    private var storageUri2: String by StringSetting(Key.PRIMARY_STORAGE_URI_2, "")
    var externalLibraryUri: String by StringSetting(Key.EXTERNAL_LIBRARY_URI, "")
    fun getStorageUri(location: StorageLocation): String {
        return when (location) {
            StorageLocation.PRIMARY_1 -> storageUri
            StorageLocation.PRIMARY_2 -> storageUri2
            StorageLocation.EXTERNAL -> externalLibraryUri
            else -> ""
        }
    }

    fun setStorageUri(location: StorageLocation, uri: String) {
        when (location) {
            StorageLocation.PRIMARY_1 -> storageUri = uri
            StorageLocation.PRIMARY_2 -> storageUri2 = uri
            StorageLocation.EXTERNAL -> externalLibraryUri = uri
            else -> {}
        }
    }

    val folderNameFormat: Int by IntSettingStr(
        "pref_folder_naming_content_lists",
        Value.FOLDER_NAMING_CONTENT_AUTH_TITLE_ID
    )
    var storageDownloadStrategy: Int by IntSettingStr(
        Key.PRIMARY_STORAGE_FILL_METHOD,
        Value.STORAGE_FILL_BALANCE_FREE
    )
    var storageSwitchThresholdPc: Int by IntSettingStr(Key.PRIMARY_STORAGE_SWITCH_THRESHOLD_PC, 90)
    var memoryAlertThreshold: Int by IntSettingStr(Key.MEMORY_ALERT_THRESHOLD, 110)
    val isDeleteExternalLibrary: Boolean by BoolSetting(Key.EXTERNAL_LIBRARY_DELETE, false)
    val folderTruncationNbChars: Int by IntSettingStr("pref_folder_trunc_lists", 100)
    var latestBeholderTimestamp: Long by LongSetting("pref_latest_beholder_timestamp", 0)

    // DUPLICATE DETECTOR
    var duplicateSensitivity: Int by IntSettingStr("duplicate_sensitivity", 1)
    var duplicateUseTitle: Boolean by BoolSetting("duplicate_use_title", true)
    var duplicateUseArtist: Boolean by BoolSetting("duplicate_use_artist", true)
    var duplicateUseCover: Boolean by BoolSetting("duplicate_use_cover", false)
    var duplicateUseSameLanguage: Boolean by BoolSetting("duplicate_use_same_language", false)
    var duplicateIgnoreChapters: Boolean by BoolSetting("duplicate_ignore_chapters", true)
    var duplicateLastIndex: Int by IntSettingStr("last_index", -1)

    val duplicateBrowserSensitivity: Int by IntSettingStr("duplicate_browser_sensitivity", 2)
    val duplicateBrowserUseTitle: Boolean by BoolSetting("duplicate_browser_use_title", true)
    val duplicateBrowserUseArtist: Boolean by BoolSetting("duplicate_browser_use_artist", true)
    val duplicateBrowserUseCover: Boolean by BoolSetting("duplicate_browser_use_cover", true)
    val duplicateBrowserUseSameLanguage: Boolean by BoolSetting(
        "duplicate_browser_use_same_language",
        false
    )


    // APP-WIDE
    var isFirstRun: Boolean by BoolSetting(Key.FIRST_RUN, true)
    // Used to detect when LibraryActivity opens for the first time to force-open navigation drawer
    var isFirstRunProcessComplete: Boolean by BoolSetting(Key.WELCOME_DONE, false)
    var lastKnownAppVersionCode: Int by IntSettingStr(Key.LAST_KNOWN_APP_VERSION_CODE, 0)
    var isRefreshJson1Complete: Boolean by BoolSetting(Key.REFRESH_JSON_1_DONE, false)
    val isAnalyticsEnabled: Boolean by BoolSetting(Key.ANALYTICS_PREFERENCE, true)
    val isAutomaticUpdateEnabled: Boolean by BoolSetting("pref_check_updates", true)
    var isBrowserMode: Boolean by BoolSetting(Key.BROWSER_MODE, false)
    val isForceEnglishLocale: Boolean by BoolSetting(Key.FORCE_ENGLISH, false)
    var isTextMenuOn: Boolean by BoolSetting(Key.TEXT_SELECT_MENU, false)
    var arePlugReactionsOn: Boolean by BoolSetting("plug_reactions_on", true)
    val recentVisibility: Boolean by BoolSetting(Key.APP_PREVIEW, BuildConfig.DEBUG)
    val maxDbSizeKb: Long by LongSetting("db_max_size", 3L * 1024 * 1024) // 3GB
    var colorTheme: Int by IntSettingStr(Key.COLOR_THEME, Value.COLOR_THEME_LIGHT)


    // Public Helpers

    fun registerPrefsChangedListener(listener: OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPrefsChangedListener(listener: OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }


    // Delegates

    private class ULongSetting(val key: String, val default: ULong) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): ULong {
            return (sharedPreferences.getString(key, default.toString()) + "").toULong()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: ULong) {
            sharedPreferences.edit { putString(key, value.toString()) }
        }
    }

    private class LongSetting(val key: String, val default: Long) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Long {
            return (sharedPreferences.getString(key, default.toString()) + "").toLong()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            sharedPreferences.edit { putString(key, value.toString()) }
        }
    }

    private class IntSettingStr(val key: String, val default: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            return (sharedPreferences.getString(key, default.toString()) + "").toInt()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            sharedPreferences.edit { putString(key, value.toString()) }
        }
    }

    private class IntSetting(val key: String, val default: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            return (sharedPreferences.getInt(key, default))
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            sharedPreferences.edit { putInt(key, value) }
        }
    }

    private class BoolSetting(val key: String, val default: Boolean) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            return sharedPreferences.getBoolean(key, default)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sharedPreferences.edit { putBoolean(key, value) }
        }
    }

    private class StringSetting(val key: String, val default: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
            return sharedPreferences.getString(key, default) ?: ""
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            sharedPreferences.edit { putString(key, value) }
        }
    }

    private class ListStringSetting(val key: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): List<String> {
            return sharedPreferences.getString(key, "")
                ?.split(",")
                ?.map { it.trim() }
                ?.filterNot { it.isEmpty() }
                ?: emptyList()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: List<String>) {
            sharedPreferences.edit { putString(key, TextUtils.join(",", value)) }
        }
    }

    private class ListSiteSetting(val key: String, val default: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): List<Site> {
            return sharedPreferences.getString(key, default)
                ?.split(",")
                ?.distinct()
                ?.map { it.trim() }
                ?.filterNot { it.isEmpty() }
                ?.map { Site.searchByCode(it.toInt()) }
                ?: emptyList()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: List<Site>) {
            val codes = value.map { it.code }.distinct()
            sharedPreferences.edit { putString(key, TextUtils.join(",", codes)) }
        }
    }


    // Consts
    object Key {
        const val FIRST_RUN = "pref_first_run"
        const val WELCOME_DONE = "pref_welcome_done"
        const val LAST_KNOWN_APP_VERSION_CODE = "last_known_app_version_code"
        const val REFRESH_JSON_1_DONE = "refresh_json_1_done"
        const val ANALYTICS_PREFERENCE = "pref_analytics_preference"
        const val BROWSER_MODE = "browser_mode"
        const val FORCE_ENGLISH = "force_english"
        const val COLOR_THEME = "pref_color_theme"
        const val ACTIVE_SITES = "active_sites"

        const val IMPORT_QUEUE_EMPTY = "pref_import_queue_empty"

        const val LIBRARY_DISPLAY = "pref_library_display"
        const val LOCK_TYPE = "LOCK_TYPE"
        const val LIBRARY_DISPLAY_GRID_FAV = "LIBRARY_DISPLAY_GRID_FAV"
        const val LIBRARY_DISPLAY_GRID_RATING = "LIBRARY_DISPLAY_GRID_RATING"
        const val LIBRARY_DISPLAY_GRID_SOURCE = "LIBRARY_DISPLAY_GRID_SOURCE"
        const val LIBRARY_DISPLAY_GRID_STORAGE = "LIBRARY_DISPLAY_GRID_STORAGE"
        const val LIBRARY_DISPLAY_GRID_TITLE = "LIBRARY_DISPLAY_GRID_TITLE"
        const val LIBRARY_DISPLAY_GRID_LANG = "LIBRARY_DISPLAY_GRID_LANG"
        const val LIBRARY_GRID_CARD_WIDTH = "grid_card_width"
        const val LIBRARY_DISPLAY_GROUP_FIGURE = "library_display_group_figure"
        const val ACHIEVEMENTS = "achievements"
        const val ACHIEVEMENTS_NB_AI_RESCALE = "ach_nb_ai_rescale"

        const val WEB_AUGMENTED_BROWSER = "pref_browser_augmented"
        const val WEB_ADBLOCKER = "WEB_ADBLOCKER"
        const val WEB_FORCE_LIGHTMODE = "WEB_FORCE_LIGHTMODE"
        const val WEB_LOCK_FAVS_PANEL = "web_lock_favs_panel"
        const val DL_BLOCKED_TAGS = "pref_dl_blocked_tags"
        const val BROWSER_MARK_DOWNLOADED = "browser_mark_downloaded"
        const val BROWSER_MARK_MERGED = "browser_mark_merged"
        const val BROWSER_MARK_QUEUED = "browser_mark_queued"
        const val BROWSER_MARK_BLOCKED = "browser_mark_blocked"
        const val BROWSER_DL_ACTION = "pref_browser_dl_action"
        const val BROWSER_QUICK_DL = "pref_browser_quick_dl"
        const val BROWSER_QUICK_DL_THRESHOLD = "pref_browser_quick_dl_threshold"
        const val BROWSER_DNS_OVER_HTTPS = "pref_browser_dns_over_https"
        const val BROWSER_CLEAR_COOKIES = "pref_browser_clear_cookies"
        const val BROWSER_NHENTAI_INVISIBLE_BLACKLIST = "pref_nhentai_invisible_blacklist"
        const val DL_HTTP_429_DEFAULT_DELAY = "pref_dl_http_429_default_delay"

        const val TEXT_SELECT_MENU = "TEXT_SELECT_MENU"
        const val APP_LOCK = "pref_app_lock"
        const val ENDLESS_SCROLL = "pref_endless_scroll"
        const val TOP_FAB = "pref_top_fab"
        const val GROUPING_DISPLAY = "grouping_display"
        const val NOSTALGIA_MODE = "navigation_nostalgia_mode"
        const val APP_PREVIEW = "pref_app_preview"
        const val PRIMARY_STORAGE_URI = "pref_sd_storage_uri"
        const val PRIMARY_STORAGE_URI_2 = "pref_sd_storage_uri_2"
        const val EXTERNAL_LIBRARY_URI = "pref_external_library_uri"
        const val PRIMARY_STORAGE_FILL_METHOD = "pref_storage_fill_method"
        const val PRIMARY_STORAGE_SWITCH_THRESHOLD_PC = "pref_storage_switch_threshold_pc"
        const val EXTERNAL_LIBRARY_DELETE = "pref_external_library_delete"
        const val MEMORY_ALERT_THRESHOLD = "pref_memory_alert"

        const val DL_THREADS_QUANTITY_LISTS = "pref_dl_threads_quantity_lists"
        const val DL_SPEED_CAP = "dl_speed_cap"

        const val READER_COLOR_DEPTH = "viewer_color_depth"
        const val READER_TWOPAGES = "reader_two_pages"
        const val VIEWER_KEEP_SCREEN_ON = "pref_viewer_keep_screen_on"
        const val VIEWER_DISPLAY_AROUND_NOTCH = "pref_viewer_display_notch"
        const val VIEWER_IMAGE_DISPLAY = "pref_viewer_image_display"
        const val VIEWER_BROWSE_MODE = "pref_viewer_browse_mode"
        const val VIEWER_RENDERING = "pref_viewer_rendering"
        const val VIEWER_DISPLAY_PAGENUM = "pref_viewer_display_pagenum"
        const val VIEWER_ZOOM_TRANSITIONS = "pref_viewer_zoom_transitions"
        const val VIEWER_SWIPE_TO_FLING = "pref_viewer_swipe_to_fling"
        const val VIEWER_PAGE_TURN_SWIPE = "pref_viewer_page_turn_swipe"
        const val VIEWER_CONTINUOUS = "pref_viewer_continuous"
        const val VIEWER_SEPARATING_BARS = "pref_viewer_separating_bars"
        const val VIEWER_DOUBLE_TAP_TO_ZOOM = "pref_viewer_double_tap_zoom"
        const val VIEWER_HOLD_TO_ZOOM = "pref_viewer_zoom_holding"
        const val VIEWER_AUTO_ROTATE = "pref_viewer_auto_rotate_mode"
        const val VIEWER_DELETE_ASK_MODE = "viewer_delete_ask"
        const val VIEWER_DELETE_TARGET = "viewer_delete_target"
        const val READER_SMART_CROP = "reader_smart_crop"

        // Deprecated values kept for housekeeping/migration
        const val VIEWER_AUTO_ROTATE_OLD = "pref_viewer_auto_rotate"
    }

    // IMPORTANT : Any default value change must be mirrored in res/values/strings_settings.xml
    object Default {
        const val ORDER_CONTENT_FIELD = Value.ORDER_FIELD_TITLE
        const val ORDER_GROUP_FIELD = Value.ORDER_FIELD_TITLE
        const val ORDER_FOLDER_FIELD = Value.ORDER_FIELD_TITLE
        const val LIBRARY_DISPLAY = Value.LIBRARY_DISPLAY_LIST
        const val QUEUE_NEW_DOWNLOADS_POSITION = Value.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM
        const val IMPORT_NAME_PATTERN = "%t"
    }

    // IMPORTANT : Any value change must be mirrored in res/values/array_preferences.xml
    object Value {
        private val DEFAULT_SITES = arrayOf(
            Site.NHENTAI,
            Site.HITOMI,
            Site.ASMHENTAI,
            Site.TSUMINO,
            Site.PURURIN,
            Site.EHENTAI,
            Site.FAKKU2,
            Site.NEXUS,
            Site.MUSES,
            Site.DOUJINS
        )
        val ACTIVE_SITES: String = TextUtils.join(",", DEFAULT_SITES.map { it.code })

        const val DOWNLOAD_THREAD_COUNT_AUTO = 0

        const val TARGET_FOLDER_DOWNLOADS = "downloads"

        const val LIBRARY_DISPLAY_LIST = 0
        const val LIBRARY_DISPLAY_GRID = 1

        const val LIBRARY_DISPLAY_GROUP_NB_BOOKS = 0
        const val LIBRARY_DISPLAY_GROUP_SIZE = 1

        const val SEARCH_ORDER_ATTRIBUTES_ALPHABETIC = 0
        const val SEARCH_ORDER_ATTRIBUTES_COUNT = 1

        // Sorting field codes for content and group
        const val ORDER_FIELD_NONE = -1
        const val ORDER_FIELD_TITLE = 0
        const val ORDER_FIELD_ARTIST = 1
        const val ORDER_FIELD_NB_PAGES = 2
        const val ORDER_FIELD_DOWNLOAD_PROCESSING_DATE = 3
        const val ORDER_FIELD_UPLOAD_DATE = 4
        const val ORDER_FIELD_READ_DATE = 5
        const val ORDER_FIELD_READS = 6
        const val ORDER_FIELD_SIZE = 7
        const val ORDER_FIELD_CHILDREN = 8 // Groups only
        const val ORDER_FIELD_READ_PROGRESS = 9
        const val ORDER_FIELD_DOWNLOAD_COMPLETION_DATE = 10
        const val ORDER_FIELD_SOURCE_NAME = 11 // Rules only
        const val ORDER_FIELD_TARGET_NAME = 12 // Rules only
        const val ORDER_FIELD_CUSTOM = 98
        const val ORDER_FIELD_RANDOM = 99

        const val STORAGE_FILL_BALANCE_FREE = 0
        const val STORAGE_FILL_FALLOVER = 1

        const val FOLDER_NAMING_CONTENT_ID = 0
        const val FOLDER_NAMING_CONTENT_TITLE_ID = 1
        const val FOLDER_NAMING_CONTENT_AUTH_TITLE_ID = 2
        const val FOLDER_NAMING_CONTENT_TITLE_AUTH_ID = 3

        const val DL_ACTION_DL_PAGES = 0
        const val DL_ACTION_STREAM = 1
        const val DL_ACTION_ASK = 2

        const val QUEUE_NEW_DOWNLOADS_POSITION_TOP = 0
        const val QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM = 1
        const val QUEUE_NEW_DOWNLOADS_POSITION_ASK = 2

        const val DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE = 0
        const val DL_TAG_BLOCKING_BEHAVIOUR_QUEUE_ERROR = 1

        const val DL_SPEED_CAP_NONE = -1
        const val DL_SPEED_CAP_100 = 0
        const val DL_SPEED_CAP_200 = 1
        const val DL_SPEED_CAP_400 = 2
        const val DL_SPEED_CAP_800 = 3


        const val VIEWER_DISPLAY_FIT = 0
        const val VIEWER_DISPLAY_FILL = 1
        const val VIEWER_DISPLAY_STRETCH = 2

        const val VIEWER_ORIENTATION_HORIZONTAL = 0
        const val VIEWER_ORIENTATION_VERTICAL = 1

        const val VIEWER_DIRECTION_LTR = 0
        const val VIEWER_DIRECTION_RTL = 1

        const val VIEWER_BROWSE_NONE = -1
        const val VIEWER_BROWSE_LTR = 0
        const val VIEWER_BROWSE_RTL = 1
        const val VIEWER_BROWSE_TTB = 2

        const val VIEWER_RENDERING_SHARP = 0
        const val VIEWER_RENDERING_SMOOTH = 1

        const val VIEWER_READ_THRESHOLD_NONE = -1
        const val VIEWER_READ_THRESHOLD_1 = 0
        const val VIEWER_READ_THRESHOLD_2 = 1
        const val VIEWER_READ_THRESHOLD_5 = 2
        const val VIEWER_READ_THRESHOLD_ALL = 3

        const val VIEWER_COMPLETED_RATIO_THRESHOLD_NONE = -1
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_10 = 0
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_25 = 1
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_33 = 2
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_50 = 3
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_75 = 4
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_ALL = 99

        const val VIEWER_SLIDESHOW_DELAY_2 = 0
        const val VIEWER_SLIDESHOW_DELAY_4 = 1
        const val VIEWER_SLIDESHOW_DELAY_8 = 2
        const val VIEWER_SLIDESHOW_DELAY_16 = 3
        const val VIEWER_SLIDESHOW_DELAY_1 = 4
        const val VIEWER_SLIDESHOW_DELAY_05 = 5

        const val VIEWER_SLIDESHOW_LOOP_NONE = 0
        const val VIEWER_SLIDESHOW_LOOP_CHAPTER = 1
        const val VIEWER_SLIDESHOW_LOOP_BOOK = 2
        const val VIEWER_SLIDESHOW_FOLLOW_CONTINUOUS = 3

        const val VIEWER_SEPARATING_BARS_OFF = 0
        const val VIEWER_SEPARATING_BARS_SMALL = 1
        const val VIEWER_SEPARATING_BARS_MEDIUM = 2
        const val VIEWER_SEPARATING_BARS_LARGE = 3

        const val VIEWER_DELETE_ASK_AGAIN = 0
        const val VIEWER_DELETE_ASK_BOOK = 1
        const val VIEWER_DELETE_ASK_SESSION = 2

        const val VIEWER_DELETE_TARGET_BOOK = 0
        const val VIEWER_DELETE_TARGET_PAGE = 1

        const val VIEWER_CAP_TAP_ZOOM_NONE = 0
        const val VIEWER_CAP_TAP_ZOOM_2X = 2
        const val VIEWER_CAP_TAP_ZOOM_4X = 4
        const val VIEWER_CAP_TAP_ZOOM_6X = 6

        const val READER_AUTO_ROTATE_NONE = 0
        const val READER_AUTO_ROTATE_LEFT = 1
        const val READER_AUTO_ROTATE_RIGHT = 2

        val ORDER_CONTENT_FAVOURITE = -2 // Artificial order created for clarity purposes

        const val LOCK_TIMER_OFF = 0
        const val LOCK_TIMER_10S = 1
        const val LOCK_TIMER_30S = 2
        const val LOCK_TIMER_1M = 3
        const val LOCK_TIMER_2M = 4

        const val ARTIST_GROUP_VISIBILITY_ARTISTS = 0
        const val ARTIST_GROUP_VISIBILITY_GROUPS = 1
        const val ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS = 2

        val COLOR_THEME_LIGHT = Theme.LIGHT.id
        val COLOR_THEME_DARK = Theme.DARK.id
        val COLOR_THEME_BLACK = Theme.BLACK.id
        val COLOR_THEME_YOU = Theme.YOU.id
    }
}
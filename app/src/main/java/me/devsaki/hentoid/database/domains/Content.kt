package me.devsaki.hentoid.database.domains

import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import io.objectbox.annotation.Uid
import io.objectbox.converter.PropertyConverter
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.activities.sources.BabeTodayActivity
import me.devsaki.hentoid.activities.sources.BaseBrowserActivity
import me.devsaki.hentoid.activities.sources.CosplayTeleActivity
import me.devsaki.hentoid.activities.sources.FapalityActivity
import me.devsaki.hentoid.activities.sources.JapBeautiesActivity
import me.devsaki.hentoid.activities.sources.Link2GalleriesActivity
import me.devsaki.hentoid.activities.sources.LusciousActivity
import me.devsaki.hentoid.activities.sources.PicsXActivity
import me.devsaki.hentoid.activities.sources.PornPicGalleriesActivity
import me.devsaki.hentoid.activities.sources.PornPicsActivity
import me.devsaki.hentoid.activities.sources.RedditActivity
import me.devsaki.hentoid.activities.sources.SxyPixActivity
import me.devsaki.hentoid.activities.sources.XhamsterActivity
import me.devsaki.hentoid.activities.sources.XnxxActivity
import me.devsaki.hentoid.database.domains.ImageFile.Companion.fromImageUrl
import me.devsaki.hentoid.database.safeReach
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.Site.SiteConverter
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.isSupportedArchive
import me.devsaki.hentoid.util.formatAuthor
import me.devsaki.hentoid.util.hash64
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.serializeToJson
import timber.log.Timber
import java.io.IOException
import java.util.Objects

enum class DownloadMode(val value: Int) {
    DOWNLOAD(Settings.Value.DL_ACTION_DL_PAGES), // Download images
    STREAM(Settings.Value.DL_ACTION_STREAM), // Saves the book for on-demande viewing
    ASK(Settings.Value.DL_ACTION_ASK); // Saves the book for on-demande viewing)

    companion object {
        fun fromValue(v: Int): DownloadMode {
            return entries.firstOrNull { it.value == v } ?: DOWNLOAD
        }
    }
}

@Entity
data class Content(
    @Id
    var id: Long = 0,
    @Index
    @Uid(5800889076602216395L)
    var dbUrl: String = "",
    var uniqueSiteId: String = "", // Has to be queryable in DB, hence has to be a field
    var title: String = "",
    @Uid(2417271458982667075L)
    var dbAuthor: String = "",
    var coverImageUrl: String = "",
    var qtyPages: Int = 0,// Integer is actually unnecessary, but changing this to plain int requires a small DB model migration...
    var uploadDate: Long = 0,
    var downloadDate: Long = 0, // aka "Download date (processed)"
    var downloadCompletionDate: Long = 0, // aka "Download date (completed)"
    @Index
    @Convert(converter = StatusContent.Converter::class, dbType = Int::class)
    var status: StatusContent = StatusContent.UNHANDLED_ERROR,
    @Index
    @Convert(converter = SiteConverter::class, dbType = Long::class)
    var site: Site = Site.NONE,
    var storageUri: String = "",
    var favourite: Boolean = false,
    var rating: Int = 0,
    var completed: Boolean = false,
    var reads: Long = 0,
    var lastReadDate: Long = 0,
    var lastReadPageIndex: Int = 0,
    var manuallyMerged: Boolean = false,
    @Convert(converter = StringMapConverter::class, dbType = String::class)
    var bookPreferences: Map<String, String> = HashMap(),
    @Convert(converter = DownloadModeConverter::class, dbType = Int::class)
    var downloadMode: DownloadMode = DownloadMode.DOWNLOAD,
    var replacementTitle: String = "",
    // Aggregated data redundant with the sum of individual data contained in ImageFile
    // ObjectBox can't do the sum in a single Query, so here it is !
    var size: Long = 0,
    var readProgress: Float = 0f,
    // Temporary during SAVED state only
    var downloadParams: String = "",
    // Needs to be in the DB to keep the information when deletion takes a long time
    // and user navigates away; no need to save that into JSON
    var isBeingProcessed: Boolean = false,
    // Needs to be in the DB to optimize I/O
    // No need to save that into the JSON file itself, obviously
    var jsonUri: String = "",
    // Useful only during cleanup operations; no need to get it into the JSON
    @Index
    var isFlaggedForDeletion: Boolean = false,
    var lastEditDate: Long = 0
) {
    lateinit var attributes: ToMany<Attribute>

    @Backlink(to = "content")
    lateinit var imageFiles: ToMany<ImageFile>

    @Backlink(to = "content")
    lateinit var groupItems: ToMany<GroupItem>

    @Backlink(to = "content")
    lateinit var chapters: ToMany<Chapter>

    @Backlink(to = "content")
    lateinit var queueRecords: ToMany<QueueRecord>

    lateinit var contentToReplace: ToOne<Content>

    // Temporary during ERROR state only
    @Backlink(to = "content")
    lateinit var errorLog: ToMany<ErrorRecord>

    // Runtime attributes; no need to expose them for JSON persistence nor to persist them to DB
    @Transient
    var uniqueHash: Long = 0 // cached value of uniqueHash

    // number of downloaded pages; used to display the progress bar on the queue screen
    @Transient
    var progress: Long = 0

    // Number of downloaded bytes; used to display the size estimate on the queue screen
    @Transient
    var downloadedBytes: Long = 0

    @Transient
    var isFirst = false // True if current content is the first of its set in the DB query

    @Transient
    var isLast = false // True if current content is the last of its set in the DB query

    // Current number of download retries current content has gone through
    @Transient
    var numberDownloadRetries = 0

    // Read pages count fed by payload; only useful to update list display
    @Transient
    var dbReadPagesCount = -1

    @Transient
    var parentStorageUri: String? = null // Only used when importing

    @Transient
    private var storageDoc: DocumentFile? = null // Only used when importing

    // Only used when importing queued items (temp location to simplify JSON structure; definite storage in QueueRecord)
    @Transient
    var isFrozen = false

    @Transient
    var folderExists = true // Only used when loading the Content into the reader

    @Transient
    var isDynamic = false // Only used when loading the Content into the reader

    companion object {
        fun getWebActivityClass(site: Site): Class<out AppCompatActivity> {
            return when (site) {
                Site.XHAMSTER -> XhamsterActivity::class.java
                Site.XNXX -> XnxxActivity::class.java
                Site.PORNPICS -> PornPicsActivity::class.java
                Site.PORNPICGALLERIES -> PornPicGalleriesActivity::class.java
                Site.LINK2GALLERIES -> Link2GalleriesActivity::class.java
                Site.REDDIT -> RedditActivity::class.java
                Site.JJGIRLS -> SxyPixActivity::class.java
                Site.BABETODAY -> BabeTodayActivity::class.java
                Site.LUSCIOUS -> LusciousActivity::class.java
                Site.FAPALITY -> FapalityActivity::class.java
                Site.JAPBEAUTIES -> JapBeautiesActivity::class.java
                Site.SXYPIX -> SxyPixActivity::class.java
                Site.PICS_X -> PicsXActivity::class.java
                Site.COSPLAYTELE -> CosplayTeleActivity::class.java
                else -> BaseBrowserActivity::class.java
            }
        }

        fun getGalleryUrlFromId(site: Site, id: String, altCode: Int = 0): String {
            return site.url
        }

        /**
         * Neutralizes the given cover URL to detect duplicate books
         *
         * @param url  Cover URL to neutralize
         * @param site Site the URL is taken from
         * @return Neutralized cover URL
         */
        fun getNeutralCoverUrlRoot(url: String, site: Site): String {
            return url
        }

        fun transformRawUrl(site: Site, url: String): String {
            return url
        }
    }


    fun clearAttributes() {
        attributes.clear()
    }

    fun putAttributes(attributes: Collection<Attribute?>?) {
        // We do want to compare array references, not content
        if (attributes != null && attributes !== this.attributes) {
            this.attributes.clear()
            this.attributes.addAll(attributes)
        }
    }

    val attributeMap: AttributeMap
        get() {
            val result = AttributeMap()
            val list = attributes.safeReach(this)
            for (a in list) result.add(a)
            return result
        }

    fun putAttributes(attrs: AttributeMap) {
        attributes.clear()
        addAttributes(attrs)
    }

    fun addAttributes(attrs: AttributeMap): Content {
        for ((_, attrList) in attrs) {
            addAttributes(attrList)
        }
        return this
    }

    fun addAttributes(attrs: Collection<Attribute>): Content {
        attributes.addAll(attrs)
        return this
    }

    fun populateUniqueSiteId() {
        if (uniqueSiteId.isEmpty()) uniqueSiteId = computeUniqueSiteId()
    }


    private fun computeUniqueSiteId(): String {
        var parts: Array<String> =
            url.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        when (site) {
            Site.XHAMSTER -> return url.substring(url.lastIndexOf("-") + 1)
            Site.XNXX -> return if (parts.isNotEmpty()) parts[0]
            else ""

            Site.PORNPICS, Site.HELLPORNO, Site.PORNPICGALLERIES, Site.LINK2GALLERIES, Site.NEXTPICTUREZ, Site.JJGIRLS2, Site.SXYPIX, Site.PICS_X, Site.BABETODAY -> return parts[parts.size - 1]
            Site.FAPALITY, Site.COSPLAYTELE -> return parts[parts.size - 2]
            Site.JPEGWORLD -> return url.substring(url.lastIndexOf("-") + 1, url.lastIndexOf("."))
            Site.REDDIT -> return "reddit" // One single book
            Site.JAPBEAUTIES, Site.JJGIRLS -> return parts[parts.size - 2] + "/" + parts[parts.size - 1]
            Site.LUSCIOUS -> {
                // ID is the last numeric part of the URL
                // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
                val lastIndex = url.lastIndexOf('_')
                return url.substring(lastIndex + 1, url.length - 1)
            }

            Site.ASIANSISTER -> {
                // ID is the first numeric part of the URL
                // e.g. /view_51651_stuff_561_58Pn -> 51651 is the ID
                parts = url.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                return if (parts.size > 1) parts[1]
                else ""
            }

            else -> return ""
        }
    }

    val galleryUrl: String
        get() {
            val galleryConst = when (site) {
                Site.PORNPICGALLERIES, Site.LINK2GALLERIES, Site.REDDIT, Site.JJGIRLS, Site.JJGIRLS2, Site.BABETODAY, Site.JAPBEAUTIES, Site.SXYPIX, Site.COSPLAYTELE -> return url // Specific case - user can go on any site (smart parser)
                Site.HELLPORNO, Site.FAPALITY, Site.ASIANSISTER -> "" // Site landpage URL already contains the "/albums/" prefix
                Site.PORNPICS, Site.JPEGWORLD -> "galleries/"
                Site.LUSCIOUS -> return site.url.replace("/porn/", "") + url
                else -> "gallery/"
            }

            return site.url + (galleryConst + url).replace("//", "/")
        }

    val readerUrl: String
        get() {
            return galleryUrl
        }

    fun setRawUrl(value: String) {
        url = transformRawUrl(site, value)
    }

    var url: String
        get() = dbUrl
        set(value) {
            dbUrl = if (value.startsWith("http")) transformRawUrl(site, value)
            else value
            populateUniqueSiteId()
        }

    fun computeAuthor() {
        dbAuthor = formatAuthor(this)
    }

    var author: String
        get() {
            if (dbAuthor.isEmpty()) computeAuthor()
            return dbAuthor
        }
        set(value) {
            dbAuthor = value
        }

    val imageList: List<ImageFile>
        get() = imageFiles.safeReach(this)

    fun setImageFiles(imageFiles: List<ImageFile>?): Content {
        // We do want to compare array references, not content
        if (imageFiles != null && imageFiles !== this.imageFiles) {
            this.imageFiles.clear()
            this.imageFiles.addAll(imageFiles)
        }
        return this
    }

    val cover: ImageFile
        get() {
            val images = imageList
            if (images.isEmpty()) {
                val makeupCover = fromImageUrl(0, coverImageUrl, StatusContent.ONLINE, 1)
                makeupCover.imageHash = Long.MIN_VALUE // Makeup cover is unhashable
                return makeupCover
            }
            for (img in images) if (img.isCover) return img
            // If nothing found, get 1st page as cover
            return imageList.first()
        }

    val errorList: List<ErrorRecord>
        get() {
            return errorLog.safeReach(this)
        }

    fun setErrorLog(errorLog: List<ErrorRecord>?) {
        if (errorLog != null && errorLog != this.errorLog) {
            this.errorLog.clear()
            this.errorLog.addAll(errorLog)
        }
    }

    fun getPercent(): Double {
        return if (qtyPages > 0) progress * 1.0 / qtyPages
        else 0.0
    }

    fun computeProgress() {
        if (0L == progress) progress =
            imageList.count { it.status == StatusContent.DOWNLOADED || it.status == StatusContent.ERROR } * 1L
    }

    fun getBookSizeEstimate(): Double {
        if (downloadedBytes > 0) {
            computeProgress()
            if (progress > 3) return (downloadedBytes / getPercent()).toLong().toDouble()
        }
        return 0.0
    }

    fun computeDownloadedBytes() {
        if (0L == downloadedBytes) downloadedBytes = imageFiles.sumOf { it.size }
    }

    fun getNbDownloadedPages(): Int {
        return imageList.count { (it.status == StatusContent.DOWNLOADED || it.status == StatusContent.EXTERNAL || it.status == StatusContent.ONLINE) && it.isReadable }
    }

    private fun getDownloadedPagesSize(): Long {
        return imageList
            .filter { it.status == StatusContent.DOWNLOADED || it.status == StatusContent.EXTERNAL }
            .sumOf { it.size }
    }

    fun computeSize() {
        size = getDownloadedPagesSize()
    }

    fun setStorageDoc(storageDoc: DocumentFile): Content {
        this.storageUri = storageDoc.uri.toString()
        this.storageDoc = storageDoc
        return this
    }

    fun clearStorageDoc() {
        storageUri = ""
        storageDoc = null
    }

    fun getStorageDoc(): DocumentFile? {
        return storageDoc
    }

    fun increaseReads(): Content {
        reads++
        return this
    }

    // Warning : this assumes the URI contains the file name, which is not guaranteed (not in any spec)!
    val isArchive: Boolean
        get() = isSupportedArchive(storageUri)

    // Warning : this assumes the URI contains the file name, which is not guaranteed (not in any spec)!
    val isPdf: Boolean
        get() = getExtension(storageUri).equals("pdf", true)

    val groupItemList: List<GroupItem>
        get() = groupItems.safeReach(this)

    fun getGroupItems(grouping: Grouping): List<GroupItem> {
        return groupItemList
            .filterNot { null == it.linkedGroup }
            .filter { it.linkedGroup?.grouping == grouping }
    }

    private fun computeReadPagesCount(): Int {
        val countReadPages =
            imageFiles.filter(ImageFile::read).count(ImageFile::isReadable)
        return if (0 == countReadPages && lastReadPageIndex > 0) lastReadPageIndex // pre-v1.13 content
        else countReadPages // post v1.13 content
    }

    var readPagesCount: Int
        get() = if (dbReadPagesCount > -1) dbReadPagesCount else computeReadPagesCount()
        set(value) {
            dbReadPagesCount = value
        }

    fun computeReadProgress() {
        val denominator = imageList.count { it.isReadable }
        if (0 == denominator) {
            readProgress = 0f
            return
        }
        readProgress = computeReadPagesCount() * 1f / denominator
    }

    val chaptersList: List<Chapter>
        get() = chapters.safeReach(this)

    val attributeList: List<Attribute>
        get() = attributes.safeReach(this)

    fun setChapters(chapters: List<Chapter?>?) {
        // We do want to compare array references, not content
        if (chapters != null && chapters !== this.chapters) {
            this.chapters.clear()
            this.chapters.addAll(chapters)
        }
    }

    fun clearChapters() {
        chapters.clear()
    }

    fun setContentIdToReplace(contentIdToReplace: Long) {
        contentToReplace.targetId = contentIdToReplace
    }

    fun increaseNumberDownloadRetries() {
        numberDownloadRetries++
    }

    class StringMapConverter : PropertyConverter<Map<String, String>, String> {
        override fun convertToEntityProperty(databaseValue: String?): Map<String, String> {
            if (null == databaseValue) return java.util.HashMap()

            try {
                return jsonToObject<Map<String, String>>(databaseValue, MAP_STRINGS)!!
            } catch (e: IOException) {
                Timber.w(e)
                return java.util.HashMap()
            }
        }

        override fun convertToDatabaseValue(entityProperty: Map<String, String>): String {
            return serializeToJson(entityProperty, MAP_STRINGS)
        }
    }

    class DownloadModeConverter : PropertyConverter<DownloadMode, Int> {
        override fun convertToEntityProperty(databaseValue: Int?): DownloadMode {
            if (databaseValue == null) return DownloadMode.DOWNLOAD
            return DownloadMode.fromValue(databaseValue)
        }

        override fun convertToDatabaseValue(entityProperty: DownloadMode): Int {
            return entityProperty.value
        }
    }

    // Hashcode (and by consequence equals) has to take into account fields that get visually updated on the app UI
    // If not done, FastAdapter's PagedItemListImpl cache won't detect changes to the object
    // and items won't be visually updated on screen
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val content = other as Content
        return favourite == content.favourite
                && rating == content.rating
                && completed == content.completed
                && downloadDate == content.downloadDate  // To differentiate external books that have no URL
                && size == content.size // To differentiate external books that have no URL
                && lastReadDate == content.lastReadDate
                && isBeingProcessed == content.isBeingProcessed
                && url == content.url
                && coverImageUrl == content.coverImageUrl
                && site == content.site
                && downloadMode == content.downloadMode
                && lastEditDate == content.lastEditDate
                && qtyPages == content.qtyPages
    }

    override fun hashCode(): Int {
        return Objects.hash(
            favourite,
            rating,
            completed,
            downloadDate,
            size,
            lastReadDate,
            isBeingProcessed,
            url,
            coverImageUrl,
            site,
            downloadMode,
            lastEditDate,
            qtyPages
        )
    }

    fun uniqueHash(): Long {
        if (0L == uniqueHash) uniqueHash = hash64("$id.$uniqueSiteId".toByteArray())
        return uniqueHash
    }
}
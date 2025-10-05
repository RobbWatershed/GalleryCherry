package me.devsaki.hentoid.database

import android.util.SparseIntArray
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import io.objectbox.android.ObjectBoxDataSource
import io.objectbox.android.ObjectBoxLiveData
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.buildSearchUri
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.parseSearchUri
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.ObjectBoxPredeterminedDataSource.PredeterminedDataSourceFactory
import me.devsaki.hentoid.database.ObjectBoxRandomDataSource.RandomDataSourceFactory
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.database.domains.SearchRecord
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.database.domains.SiteHistory
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.AttributeQueryResult
import me.devsaki.hentoid.util.Location
import me.devsaki.hentoid.util.MergerLiveData
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Value.LIBRARY_DISPLAY_GROUP_SIZE
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.util.isInLibrary
import me.devsaki.hentoid.widget.ContentSearchManager.Companion.searchContentIds
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle.Companion.fromSearchCriteria
import timber.log.Timber
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.min


class ObjectBoxDAO : CollectionDAO {
    override fun cleanup() {
        ObjectBoxDB.cleanup()
    }

    override fun cleanupOrphanAttributes() {
        ObjectBoxDB.cleanupOrphanAttributes()
    }

    override fun getDbSizeBytes(): Long {
        return ObjectBoxDB.getDbSizeBytes()
    }

    override fun selectStoredFavContentIds(bookFavs: Boolean, groupFavs: Boolean): Set<Long> {
        return ObjectBoxDB.selectStoredContentFavIds(bookFavs, groupFavs)
    }

    override fun countContentWithUnhashedCovers(): Long {
        return ObjectBoxDB.selectNonHashedContentQ().safeCount()
    }

    override fun selectContentWithUnhashedCovers(): List<Content> {
        return ObjectBoxDB.selectNonHashedContentQ().safeFind()
    }

    override fun streamStoredContent(
        includeQueued: Boolean,
        orderField: Int,
        orderDesc: Boolean,
        consumer: Consumer<Content>
    ) {
        ObjectBoxDB.selectStoredContentQ(includeQueued, orderField, orderDesc).build()
            .use { query -> query.forEach { consumer(it) } }
    }

    override fun selectRecentBookIds(searchBundle: ContentSearchBundle): List<Long> {
        return contentIdSearch(false, searchBundle, emptySet())
    }

    override fun searchBookIds(
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): List<Long> {
        return contentIdSearch(false, searchBundle, metadata)
    }

    override fun searchBookIdsUniversal(searchBundle: ContentSearchBundle): List<Long> {
        return contentIdSearch(true, searchBundle, emptySet())
    }

    override fun insertAttribute(attr: Attribute): Long {
        return ObjectBoxDB.insertAttribute(attr)
    }

    override fun selectAttribute(id: Long): Attribute? {
        return ObjectBoxDB.selectAttribute(id)
    }

    override fun selectAttributeMasterDataPaged(
        types: List<AttributeType>,
        filter: String?,
        groupId: Long,
        attrs: Set<Attribute>?,
        location: Location,
        contentType: Type,
        includeFreeAttrs: Boolean,
        page: Int,
        booksPerPage: Int,
        orderStyle: Int
    ): AttributeQueryResult {
        return pagedAttributeSearch(
            types,
            filter,
            groupId,
            getDynamicGroupContent(groupId),
            attrs,
            location,
            contentType,
            includeFreeAttrs,
            orderStyle,
            page,
            booksPerPage
        )
    }

    override fun countAttributesPerType(
        groupId: Long,
        filter: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): SparseIntArray {
        return countAttributes(
            groupId,
            getDynamicGroupContent(groupId),
            filter,
            location,
            contentType
        )
    }

    override fun selectChapters(contentId: Long): List<Chapter> {
        return ObjectBoxDB.selectChapters(contentId)
    }

    override fun selectChapters(chapterIds: List<Long>): List<Chapter> {
        return ObjectBoxDB.selectChapters(chapterIds)
    }

    override fun selectChapter(chapterId: Long): Chapter? {
        return ObjectBoxDB.selectChapter(chapterId)
    }

    override fun selectErrorContentLive(): LiveData<List<Content>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectErrorContentQ())
    }

    override fun selectErrorContentLive(query: String?, source: Site?): LiveData<List<Content>> {
        val bundle = ContentSearchBundle()
        bundle.query = query ?: ""
        val sourceAttr: MutableSet<Attribute> = HashSet()
        if (source != null) sourceAttr.add(Attribute(source))
        bundle.attributes = buildSearchUri(sourceAttr, null, "", 0, 0).toString()
        bundle.sortField = Settings.Value.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE
        return ObjectBoxLiveData(
            ObjectBoxDB.selectContentUniversalQ(
                bundle,
                LongArray(0),
                intArrayOf(StatusContent.ERROR.code)
            )
        )
    }

    override fun selectErrorContent(): List<Content> {
        return ObjectBoxDB.selectErrorContentQ().safeFind()
    }

    override fun countAllBooksLive(): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val livedata = ObjectBoxLiveData(ObjectBoxDB.selectVisibleContentQ())
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { result.value = it.size }
        return result
    }

    override fun countBooks(
        groupId: Long,
        metadata: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val bundle = ContentSearchBundle()
        bundle.groupId = groupId
        bundle.location = location.value
        bundle.contentType = contentType.value
        bundle.sortField = Settings.Value.ORDER_FIELD_NONE
        val livedata = ObjectBoxLiveData(
            ObjectBoxDB.selectContentSearchContentQ(
                bundle,
                getDynamicGroupContent(groupId),
                metadata,
                ObjectBoxDB.libraryStatus
            )
        )
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { result.value = it.size }
        return result
    }

    override fun selectRecentBooks(searchBundle: ContentSearchBundle): LiveData<PagedList<Content>> {
        return getPagedContent(false, searchBundle, emptySet())
    }

    override fun searchBooks(
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): LiveData<PagedList<Content>> {
        return getPagedContent(false, searchBundle, metadata)
    }

    override fun searchBooksUniversal(searchBundle: ContentSearchBundle): LiveData<PagedList<Content>> {
        return getPagedContent(true, searchBundle, emptySet())
    }

    override fun selectNoContent(): LiveData<PagedList<Content>> {
        return LivePagedListBuilder(
            ObjectBoxDataSource.Factory(ObjectBoxDB.selectNoContentQ()),
            1
        ).build()
    }


    private fun getPagedContent(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>,
    ): LiveData<PagedList<Content>> {
        val isCustomOrder = searchBundle.sortField == Settings.Value.ORDER_FIELD_CUSTOM
        val contentRetrieval: Pair<Long, DataSource.Factory<Int, Content>> =
            if (isCustomOrder) getPagedContentByList(isUniversal, searchBundle, metadata)
            else getPagedContentByQuery(isUniversal, searchBundle, metadata)
        val nbPages = Settings.contentPageQuantity
        var initialLoad = nbPages * 3
        if (searchBundle.loadAll) {
            // Trump Android's algorithm by setting a number of pages higher that the actual number of results
            // to avoid having a truncated result set (see issue #501)
            initialLoad = ceil(contentRetrieval.first * 1.0 / nbPages).toInt() * nbPages
        }
        val cfg = PagedList.Config.Builder().setEnablePlaceholders(!searchBundle.loadAll)
            .setInitialLoadSizeHint(initialLoad).setPageSize(nbPages).build()
        return LivePagedListBuilder(contentRetrieval.second, cfg).build()
    }

    private fun getPagedContentByQuery(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): Pair<Long, DataSource.Factory<Int, Content>> {
        val isRandom = searchBundle.sortField == Settings.Value.ORDER_FIELD_RANDOM
        val isExclusionSearch = searchBundle.excludedAttributeTypes?.isNotEmpty() ?: false
        val query = if (isUniversal) {
            ObjectBoxDB.selectContentUniversalQ(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId)
            )
        } else if (isExclusionSearch) {
            val excludedAttrs = searchBundle.excludedAttributeTypes!!
            ObjectBoxDB.selectContentIdsWithoutAttributesQ(
                excludedAttrs.map { AttributeType.searchByCode(it) }.filterNotNull()
            )
        } else {
            ObjectBoxDB.selectContentSearchContentQ(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                metadata,
                ObjectBoxDB.libraryStatus
            )
        }
        return if (isRandom) {
            val shuffledIds = ObjectBoxDB.getShuffledIds()
            Pair(query.count(), RandomDataSourceFactory(query, shuffledIds))
        } else Pair(query.count(), ObjectBoxDataSource.Factory(query))
    }

    private fun getPagedContentByList(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): Pair<Long, DataSource.Factory<Int, Content>> {
        val isExclusionSearch = searchBundle.excludedAttributeTypes?.isNotEmpty() ?: false
        val ids = if (isUniversal) {
            ObjectBoxDB.selectContentUniversalByGroupItem(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId)
            )
        } else if (isExclusionSearch) {
            val excludedAttrs = searchBundle.excludedAttributeTypes!!
            ObjectBoxDB.selectContentIdsWithoutAttributes(
                excludedAttrs.map { AttributeType.searchByCode(it) }.filterNotNull()
            )
        } else {
            ObjectBoxDB.selectContentSearchContentByGroupItem(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                metadata
            )
        }
        return Pair(
            ids.size.toLong(), PredeterminedDataSourceFactory(
                { id -> ObjectBoxDB.selectContentById(id) }, ids
            )
        )
    }

    override fun selectContent(id: Long): Content? {
        return ObjectBoxDB.selectContentById(id)
    }

    override fun selectContent(id: LongArray): List<Content> {
        return ObjectBoxDB.selectContentById(id.toList())
    }

    // Find any book that has the given content URL _or_ a chapter with the given content URL _or_ has a cover starting with the given cover URL
    override fun selectContentByUrlOrCover(
        site: Site,
        contentUrl: String,
        coverUrl: String?,
        searchChapters: Boolean
    ): Content? {
        val coverUrlStart =
            if (coverUrl != null) Content.getNeutralCoverUrlRoot(coverUrl, site) else ""
        return ObjectBoxDB.selectContentByUrlOrCover(
            site,
            contentUrl,
            coverUrlStart,
            searchChapters
        )
    }

    // Find any book that has the given content URL _and_ has a cover starting with the given cover URL
    override fun selectContentsByUrl(
        site: Site,
        contentUrl: String
    ): Set<Content> {
        return ObjectBoxDB.selectContentByUrl(site, contentUrl)
    }

    // Find any book that has the given quality of pages _and_ size
    override fun selectContentsByQtyPageAndSize(qtyPage: Int, size: Long): Set<Content> {
        return ObjectBoxDB.selectContentsByQtyPageAndSize(qtyPage, size)
    }

    override fun selectContentBySourceAndUrl(
        site: Site,
        contentUrl: String,
        coverUrl: String?
    ): Content? {
        val coverUrlStart =
            if (coverUrl != null) Content.getNeutralCoverUrlRoot(coverUrl, site) else ""
        return ObjectBoxDB.selectContentBySourceAndUrl(site, contentUrl, coverUrlStart)
    }

    override fun selectAllSourceUrls(site: Site): Set<String> {
        return ObjectBoxDB.selectAllContentUrls(site.code)
    }

    override fun selectAllMergedUrls(site: Site): Set<String> {
        return ObjectBoxDB.selectAllMergedContentUrls(site)
    }

    override fun searchTitlesWith(word: String, contentStatusCodes: IntArray): List<Content> {
        return ObjectBoxDB.selectContentWithTitle(word, contentStatusCodes)
    }

    override fun selectContentByStorageUri(folderUri: String, onlyFlagged: Boolean): Content? {
        // Select only the "document" part of the URI, as the "tree" part can vary
        val index = folderUri.indexOf("/document/")
        if (-1 == index) return null
        val docPart = folderUri.substring(index)
        return ObjectBoxDB.selectContentEndWithStorageUri(docPart, onlyFlagged)
    }

    override fun selectContentByStorageRootUri(rootUri: String): List<Content> {
        return ObjectBoxDB.selectContentStartWithStorageUri(rootUri)
    }

    override fun insertContent(content: Content): Long {
        val result = ObjectBoxDB.insertContentAndAttributes(content)
        // Attach new attributes to existing groups, if any
        for (a in result.second) {
            val g = selectGroupByName(Grouping.ARTIST.id, a.name)
            if (g != null) insertGroupItem(GroupItem(result.first, g, -1))
        }
        return result.first
    }

    override fun insertContentCore(content: Content): Long {
        return ObjectBoxDB.insertContentCore(content)
    }

    override fun updateContentStatus(updateFrom: StatusContent, updateTo: StatusContent) {
        ObjectBoxDB.updateContentStatus(updateFrom, updateTo)
    }

    override fun updateContentProcessedFlag(contentId: Long, flag: Boolean) {
        ObjectBoxDB.updateContentProcessedFlag(contentId, flag)
    }

    override fun updateContentsProcessedFlagById(contentIds: List<Long>, flag: Boolean) {
        ObjectBoxDB.updateContentsProcessedFlag(contentIds.toLongArray(), flag)
    }

    override fun updateContentsProcessedFlag(contents: List<Content>, flag: Boolean) {
        ObjectBoxDB.updateContentsProcessedFlag(contents.map { it.id }.toLongArray(), flag)
    }

    override fun deleteContent(content: Content) {
        ObjectBoxDB.deleteContentById(content.id)
    }

    override fun selectErrorRecordByContentId(contentId: Long): List<ErrorRecord> {
        return ObjectBoxDB.selectErrorRecordByContentId(contentId)
    }

    override fun insertErrorRecord(record: ErrorRecord) {
        ObjectBoxDB.insertErrorRecord(record)
    }

    override fun deleteErrorRecords(contentId: Long) {
        ObjectBoxDB.deleteErrorRecords(contentId)
    }

    override fun insertChapters(chapters: List<Chapter>) {
        ObjectBoxDB.insertChapters(chapters)
    }

    override fun deleteChapters(content: Content) {
        ObjectBoxDB.deleteChaptersByContentId(content.id)
    }

    override fun deleteChapter(chapter: Chapter) {
        ObjectBoxDB.deleteChapter(chapter.id)
    }

    override fun clearDownloadParams(contentId: Long) {
        val c = ObjectBoxDB.selectContentById(contentId) ?: return
        c.downloadParams = ""
        ObjectBoxDB.insertContentCore(c)
        val imgs = c.imageFiles
        for (img in imgs) img.downloadParams = ""
        ObjectBoxDB.insertImageFiles(imgs)
    }

    override fun shuffleContent() {
        ObjectBoxDB.shuffleContentIds()
    }

    override fun countAllInternalBooks(rootPath: String, favsOnly: Boolean): Long {
        return ObjectBoxDB.selectAllInternalContentsQ(rootPath, favsOnly, true).safeCount()
    }

    override fun countAllQueueBooks(): Long {
        // Count doesn't work here because selectAllQueueBooksQ uses a filter
        return ObjectBoxDB.selectAllQueueBooksQ().safeFindIds().size.toLong()
    }

    override fun countAllQueueBooksLive(): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val livedata = ObjectBoxLiveData(ObjectBoxDB.selectAllQueueBooksQ())
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { result.value = it.size }
        return result
    }

    override fun streamAllInternalBooks(
        rootPath: String,
        favsOnly: Boolean,
        consumer: Consumer<Content>
    ) {
        ObjectBoxDB.selectAllInternalContentsQ(rootPath, favsOnly, true).use { query ->
            query.forEach { consumer(it) }
        }
    }

    override fun countAllExternalBooks(): Long {
        return ObjectBoxDB.selectAllExternalContentsQ().safeCount()
    }

    override fun deleteAllExternalBooks() {
        ObjectBoxDB.deleteContentById(ObjectBoxDB.selectAllExternalContentsQ().safeFindIds())
    }

    override fun selectGroups(groupIds: LongArray): List<Group> {
        return ObjectBoxDB.selectGroups(groupIds) ?: emptyList()
    }

    override fun selectGroups(grouping: Int): List<Group> {
        return ObjectBoxDB.selectGroupsQ(
            grouping, null, 0, false, -1,
            groupFavouritesOnly = false,
            groupNonFavouritesOnly = false,
            filterRating = -1
        ).safeFind()
    }

    override fun selectGroups(grouping: Int, subType: Int): List<Group> {
        return ObjectBoxDB.selectGroupsQ(
            grouping,
            null,
            0,
            false,
            subType,
            groupFavouritesOnly = false,
            groupNonFavouritesOnly = false,
            filterRating = -1
        ).safeFind()
    }

    override fun selectEditedGroups(grouping: Int): List<Group> {
        return ObjectBoxDB.selectEditedGroups(grouping)
    }

    override fun countAllGroupsLive(grouping: Int): LiveData<Int> {
        val countLiveData = MediatorLiveData<Int>()
        val groupsLive = selectGroupsLive(
            grouping,
            "",
            0,
            true,
            Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS,
            groupFavouritesOnly = false,
            groupNonFavouritesOnly = false,
            filterRating = -1,
            displaySize = Settings.libraryDisplayGroupFigure == LIBRARY_DISPLAY_GROUP_SIZE,
            true
        )
        countLiveData.addSource(groupsLive) { countLiveData.value = it.size }
        return countLiveData
    }

    override fun selectGroupsLive(
        grouping: Int,
        query: String?,
        orderField: Int,
        orderDesc: Boolean,
        artistGroupVisibility: Int,
        groupFavouritesOnly: Boolean,
        groupNonFavouritesOnly: Boolean,
        filterRating: Int,
        displaySize: Boolean,
        countAll: Boolean
    ): LiveData<List<Group>> {
        val livedata: LiveData<List<Group>> =
            if (grouping == Grouping.ARTIST.id) selectArtistGroupsLive(
                query,
                orderDesc,
                artistGroupVisibility,
                groupFavouritesOnly,
                groupNonFavouritesOnly,
                filterRating,
                countAll
            ) else ObjectBoxLiveData(
                ObjectBoxDB.selectGroupsQ(
                    grouping,
                    query, orderField,
                    orderDesc,
                    -1,
                    groupFavouritesOnly,
                    groupNonFavouritesOnly,
                    filterRating
                )
            )
        if (countAll) return livedata

        var workingData = livedata


        // === SPECIFIC DATA

        // Download date grouping : groups are empty as they are dynamically populated
        //   -> Manually add items inside each of them
        //   -> Manually set a cover for each of them
        if (grouping == Grouping.DL_DATE.id) {
            val livedata2 = MediatorLiveData<List<Group>>()
            livedata2.addSource(livedata) { groups ->
                val enrichedWithItems =
                    groups.map {
                        enrichGroupWithItemsByDlDate(
                            it,
                            it.propertyMin,
                            it.propertyMax
                        )
                    }.toList()
                livedata2.value = enrichedWithItems
            }
            workingData = livedata2
        }

        // Dynamic grouping : groups are empty as they are dynamically populated
        //   -> Manually add items inside each of them
        //   -> Manually set a cover for each of them
        if (grouping == Grouping.DYNAMIC.id) {
            val livedata2 = MediatorLiveData<List<Group>>()
            livedata2.addSource(livedata) { groups ->
                val enrichedWithItems =
                    groups.map { enrichGroupWithItemsByQuery(it) }
                livedata2.value = enrichedWithItems
            }
            workingData = livedata2
        }

        // Custom "ungrouped" special group is dynamically populated
        // -> Manually add items
        if (grouping == Grouping.CUSTOM.id) {
            val livedata2 = MediatorLiveData<List<Group>>()
            livedata2.addSource(livedata) { groups ->
                val enrichedWithItems = groups.map { enrichCustomGroups(it) }
                livedata2.value = enrichedWithItems
            }
            workingData = livedata2
        }

        // === SIZE
        if (displaySize) {
            val livedata3 = MediatorLiveData<List<Group>>()
            livedata3.addSource(workingData) { groups ->
                val enrichedWithSize = groups.map { enrichGroupWithSize(it) }
                livedata3.value = enrichedWithSize
            }
            workingData = livedata3
        }


        // === ORDERING

        // Order by number of children (ObjectBox can't do that natively)
        if (Settings.Value.ORDER_FIELD_CHILDREN == orderField) {
            val result = MediatorLiveData<List<Group>>()
            result.addSource(workingData) { groups ->
                val sortOrder = if (orderDesc) -1 else 1
                val orderedByNbChildren = groups.sortedBy { it.getItems().size * sortOrder }
                result.value = orderedByNbChildren
            }
            return result
        }

        // Order by latest download date of children (ObjectBox can't do that natively)
        if (Settings.Value.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE == orderField) {
            val result = MediatorLiveData<List<Group>>()
            result.addSource(workingData) { groups ->
                val sortOrder = if (orderDesc) -1 else 1
                val orderedByDlDate = groups.sortedBy {
                    getLatestDlDate(it) * sortOrder
                }
                result.value = orderedByDlDate
            }
            return result
        }
        return workingData
    }

    private fun enrichGroupWithItemsByDlDate(g: Group, minDays: Int, maxDays: Int): Group {
        val items = selectGroupItemsByDlDate(g, minDays, maxDays)
        g.setItems(items)
        if (items.isNotEmpty()) g.coverContent.target = items[0].linkedContent
        return g
    }

    private fun enrichGroupWithItemsByQuery(g: Group): Group {
        val items = selectGroupItemsByQuery(g)
        g.setItems(items)
        if (items.isNotEmpty()) {
            val c = selectContent(items[0].contentId)
            g.coverContent.target = c
        }
        return g
    }

    private fun enrichCustomGroups(g: Group): Group {
        if (g.grouping == Grouping.CUSTOM) {
            val newItems: MutableList<GroupItem> = ArrayList()

            val groupContent = if (g.isUngroupedGroup) { // Populate Ungrouped custom group
                ObjectBoxDB.selectUngroupedContentIds().toList()
            } else { // Reselect items; only take items from the library to avoid counting those who've been sent back to the Queue
                ObjectBoxDB.selectContentIdsByGroup(g.id).toList()
            }
            groupContent.forEachIndexed { idx, c ->
                val order = if (g.isUngroupedGroup) -1 else idx
                newItems.add(GroupItem(c, g, order))
            }
            g.setItems(newItems)
            // Reset cover content if it isn't among remaining books
            if (newItems.isNotEmpty()) {
                val newContents = newItems.map { it.contentId }
                if (!newContents.contains(g.coverContent.targetId)) {
                    val c = selectContent(newItems[0].contentId)
                    g.coverContent.target = c
                }
            }
        }
        return g
    }

    private fun enrichGroupWithSize(g: Group): Group {
        val items = g.getItems()
        if (items.isEmpty()) return g

        val contentIds = items.map { it.contentId }.toLongArray()
        val sizes = ObjectBoxDB.selectContentSizes(contentIds)
        items.forEachIndexed { idx, gi -> gi.size = sizes[idx] }
        return g
    }

    private fun getLatestDlDate(g: Group): Long {
        // Manually select all content as g.getContents won't work (unresolved items)
        val contents = ObjectBoxDB.selectContentById(g.contentIds)
        return contents.maxOfOrNull { it.downloadDate } ?: 0
    }

    private fun selectArtistGroupsLive(
        query: String?,
        orderDesc: Boolean,
        artistGroupVisibility: Int,
        groupFavouritesOnly: Boolean,
        groupNonFavouritesOnly: Boolean,
        filterRating: Int,
        countAll: Boolean
    ): LiveData<List<Group>> {
        // Select as many groups as there are non-empty artist/circle master data
        val attrsLive: LiveData<List<Attribute>> = ObjectBoxLiveData(
            ObjectBoxDB.selectArtistsQ(query, orderDesc, artistGroupVisibility)
        )

        if (countAll) {
            val countLive = MediatorLiveData<List<Group>>()
            countLive.addSource(attrsLive) { attrs ->
                // We're just counting, we don't need to instanciate multiple groups
                // NB : +1 is for the "no artist" group
                val bogusGroup = Group()
                val groups: MutableList<Group> = ArrayList(attrs.size + 1)
                repeat(attrs.size + 1) { groups.add(bogusGroup) }
                countLive.value = groups
            }
            return countLive
        }

        val livedata2 = MediatorLiveData<List<Group>>()
        livedata2.addSource(attrsLive) { attrs ->
            val groups = attrs
                // Don't display empty groups
                .filterNot { it.contents.isEmpty() }
                .mapIndexed { idx, attr ->
                    val group = Group(Grouping.DYNAMIC, attr.name, idx + 1)
                    group.searchUri = buildSearchUri(setOf(attr)).toString()
                    group.subtype = if (AttributeType.CIRCLE == attr.type) 1 else 0
                    // WARNING : This is the place where things get slow
                    val items = attr.contents
                        .filter { isInLibrary(it.status) }
                        .mapIndexed { idx2, c ->
                            if (0 == idx2) group.coverContent.target = c
                            GroupItem(c.id, group, idx2)
                        }
                    group.setItems(items)
                    group
                }
            livedata2.value = groups
        }

        // Forge the "no artist / circle" group
        val noArtistLive: MutableLiveData<List<Group>> = MutableLiveData<List<Group>>()
        val exludedGrpRes = when (artistGroupVisibility) {
            Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS -> R.string.no_artist_circle_group_name
            Settings.Value.ARTIST_GROUP_VISIBILITY_GROUPS -> R.string.no_circle_group_name
            else -> R.string.no_artist_group_name
        }
        val exludedGrpLbl = HentoidApp.getInstance().resources.getString(exludedGrpRes)
        val noArtistGroup = Group(Grouping.DYNAMIC, exludedGrpLbl, 0)
        noArtistGroup.subtype = 2
        val excludedTypes: Set<AttributeType> = when (artistGroupVisibility) {
            Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS ->
                setOf(AttributeType.ARTIST, AttributeType.CIRCLE)

            Settings.Value.ARTIST_GROUP_VISIBILITY_GROUPS -> setOf(AttributeType.CIRCLE)
            else -> setOf(AttributeType.ARTIST)
        }
        noArtistGroup.searchUri = buildSearchUri(null, excludedTypes).toString()
        // Populate with Content
        val content = ObjectBoxDB.selectContentIdsWithoutAttributesQ(excludedTypes).safeFind()
        val items = content.mapIndexed { idx2, c ->
            if (0 == idx2) noArtistGroup.coverContent.target = c
            GroupItem(c.id, noArtistGroup, idx2)
        }
        noArtistGroup.setItems(items)
        noArtistLive.postValue(listOf(noArtistGroup))

        // Flagged groups
        val flaggedLive: LiveData<List<Group>> = ObjectBoxLiveData(
            ObjectBoxDB.selectGroupsByGroupingQ(Grouping.ARTIST.id, false)
        )

        // Merge actual groups with the "no artist / circle" forged group and enrich with flagged groups
        val combined =
            MergerLiveData.Three(
                noArtistLive,
                livedata2,
                flaggedLive,
                false
            ) { noArtistGrp, dynamicGrps, flaggedGrps ->
                val result = ArrayList<Group>()
                result.addAll(noArtistGrp)
                val flaggedMap =
                    flaggedGrps.groupBy { it.reducedStr }.mapValues { it.value.first() }
                // TODO it's pointless to create groupItems and to discard them on the 2nd pass -> should filter on the go instead
                val enrichedGrps = dynamicGrps.map { enrichGroupWithFlags(it, flaggedMap) }
                if (groupFavouritesOnly || groupNonFavouritesOnly || filterRating > -1) {
                    result.addAll(enrichedGrps.filter {
                        filterGroup(
                            it,
                            groupFavouritesOnly,
                            groupNonFavouritesOnly,
                            filterRating
                        )
                    })
                } else {
                    result.addAll(enrichedGrps)
                }
                result.toList()
            }
        return combined
    }

    private fun filterGroup(
        g: Group,
        groupFavouritesOnly: Boolean,
        groupNonFavouritesOnly: Boolean,
        filterRating: Int
    ): Boolean {
        return (groupFavouritesOnly && g.favourite)
                || (groupNonFavouritesOnly && !g.favourite)
                || (filterRating > -1 && g.rating == filterRating)
    }

    private fun enrichGroupWithFlags(g: Group, flaggedGroups: Map<String, Group>): Group {
        flaggedGroups[g.reducedStr]?.let {
            if (it.coverContent.targetId < 1) it.coverContent = g.coverContent
            it.searchUri = g.searchUri
            it.subtype = g.subtype
            it.grouping = Grouping.DYNAMIC
            it.setItems(g.getItems())
            return it
        }
        return g
    }

    override fun selectGroup(groupId: Long): Group? {
        if (groupId < 1) return null
        return ObjectBoxDB.selectGroup(groupId)
    }

    override fun selectGroupByName(grouping: Int, name: String): Group? {
        return ObjectBoxDB.selectGroupByName(grouping, name)
    }

    // Does NOT check name unicity
    override fun insertGroup(group: Group): Long {
        // Auto-number max order when not provided
        if (-1 == group.order) group.order = ObjectBoxDB.getMaxGroupOrderFor(group.grouping) + 1
        return ObjectBoxDB.insertGroup(group)
    }

    override fun countGroupsFor(grouping: Grouping): Long {
        return ObjectBoxDB.countGroupsFor(grouping)
    }

    override fun deleteGroup(groupId: Long) {
        ObjectBoxDB.deleteGroup(groupId)
    }

    override fun deleteAllGroups(grouping: Grouping) {
        ObjectBoxDB.deleteGroupItemsByGrouping(grouping.id)
        ObjectBoxDB.selectGroupsByGroupingQ(grouping.id).safeRemove()
    }

    override fun flagAllGroups(grouping: Grouping) {
        ObjectBoxDB.flagGroupsForDeletion(
            ObjectBoxDB.selectGroupsByGroupingQ(grouping.id).safeFind()
        )
    }

    override fun deleteAllFlaggedGroups() {
        ObjectBoxDB.selectFlaggedGroupsQ().use { flaggedGroups ->
            // Delete related GroupItems first
            val groups = flaggedGroups.find()
            for (g in groups) ObjectBoxDB.deleteGroupItemsByGroup(g.id)

            // Actually delete the Groups
            flaggedGroups.remove()
        }
    }

    override fun deleteEmptyArtistGroups() {
        ObjectBoxDB.deleteEmptyArtistGroups()
    }

    override fun insertGroupItem(item: GroupItem): Long {
        // Auto-number max order when not provided
        if (-1 == item.order) item.order = ObjectBoxDB.getMaxGroupItemOrderFor(item.groupId) + 1

        // If target group doesn't have a cover, get the corresponding Content's
        item.linkedGroup?.coverContent?.let { groupCoverContent ->
            if (!groupCoverContent.isResolvedAndNotNull) {
                val c: Content? = item.linkedContent ?: selectContent(item.contentId)
                groupCoverContent.setAndPutTarget(c)
            }
        }

        return ObjectBoxDB.insertGroupItem(item)
    }

    override fun selectGroupItems(contentId: Long, grouping: Grouping): List<GroupItem> {
        return ObjectBoxDB.selectGroupItems(contentId, grouping.id)
    }

    private fun selectGroupItemsByDlDate(
        group: Group,
        minDays: Int,
        maxDays: Int
    ): List<GroupItem> {
        val contentResult = ObjectBoxDB.selectContentByDlDate(minDays, maxDays)
        return contentResult.map { GroupItem(it, group, -1) }
    }

    private fun selectGroupItemsByQuery(group: Group): List<GroupItem> {
        val criteria = parseSearchUri(group.searchUri.toUri())
        val bundle = fromSearchCriteria(criteria)
        val contentResult = searchContentIds(bundle, this)
        return contentResult.map { GroupItem(it, group, -1) }
    }

    override fun deleteGroupItems(groupItemIds: List<Long>) {
        // Check if one of the GroupItems to delete is linked to the content that contains the group's cover picture
        val groupItems = ObjectBoxDB.selectGroupItems(groupItemIds.toLongArray())
        for (gi in groupItems) {
            gi.linkedGroup?.coverContent?.let { groupCoverContent ->
                // If so, remove the cover picture
                if (groupCoverContent.isResolvedAndNotNull && groupCoverContent.targetId == gi.contentId)
                    groupCoverContent.setAndPutTarget(null)
            }
        }
        ObjectBoxDB.deleteGroupItems(groupItemIds.toLongArray())
    }

    override fun flagAllInternalBooks(rootPath: String, includePlaceholders: Boolean) {
        ObjectBoxDB.flagContentsForDeletion(
            ObjectBoxDB.selectAllInternalContentsQ(
                rootPath,
                false,
                includePlaceholders
            ).safeFindIds(), true
        )
    }

    override fun flagAllExternalContents() {
        ObjectBoxDB.flagContentsForDeletion(
            ObjectBoxDB.selectAllExternalContentsQ().safeFindIds(),
            true
        )
    }

    override fun deleteAllInternalContents(rootPath: String, resetRemainingImagesStatus: Boolean) {
        ObjectBoxDB.deleteContentById(
            ObjectBoxDB.selectAllInternalContentsQ(rootPath, false).safeFindIds()
        )
        if (resetRemainingImagesStatus) resetRemainingImagesStatus(rootPath)
    }

    override fun deleteAllFlaggedContents(resetRemainingImagesStatus: Boolean, pathRoot: String?) {
        ObjectBoxDB.deleteContentById(ObjectBoxDB.selectAllFlaggedContentsQ().safeFindIds())
        if (resetRemainingImagesStatus && pathRoot != null) resetRemainingImagesStatus(pathRoot)
    }

    // Switch status of all remaining images (i.e. from queued books) to SAVED, as we cannot guarantee the files are still there
    private fun resetRemainingImagesStatus(rootPath: String) {
        val remainingContentIds = ObjectBoxDB.selectAllQueueBooksQ(rootPath).safeFindIds()
        for (contentId in remainingContentIds) ObjectBoxDB.updateImageContentStatus(
            contentId,
            null,
            StatusContent.SAVED
        )
    }

    override fun flagAllErrorBooksWithJson() {
        ObjectBoxDB.flagContentsForDeletion(
            ObjectBoxDB.selectAllErrorJsonBooksQ().safeFindIds(),
            true
        )
    }

    override fun deleteAllQueuedBooks() {
        Timber.i("Cleaning up queue")
        ObjectBoxDB.deleteContentById(ObjectBoxDB.selectAllQueueBooksQ().safeFindIds())
        ObjectBoxDB.deleteQueueRecords()
    }

    override fun insertImageFile(img: ImageFile) {
        ObjectBoxDB.insertImageFile(img)
    }

    override fun insertImageFiles(imgs: List<ImageFile>) {
        ObjectBoxDB.insertImageFiles(imgs)
    }

    override fun replaceImageList(contentId: Long, newList: List<ImageFile>) {
        ObjectBoxDB.replaceImageFiles(contentId, newList)
    }

    override fun updateImageContentStatus(
        contentId: Long,
        updateFrom: StatusContent?,
        updateTo: StatusContent
    ) {
        ObjectBoxDB.updateImageContentStatus(contentId, updateFrom, updateTo)
    }

    override fun updateImageFileStatusParamsMimeTypeUriSize(image: ImageFile) {
        ObjectBoxDB.updateImageFileStatusParamsMimeTypeUriSize(image)
    }

    override fun deleteImageFiles(imgs: List<ImageFile>) {
        // Delete the page
        ObjectBoxDB.deleteImageFiles(imgs)

        // Lists all relevant content
        val contents = imgs.map { it.content.targetId }.distinct()

        // Update the contents
        for (contentId in contents) {
            val content = ObjectBoxDB.selectContentById(contentId)
            if (content != null) {
                // Compute new size and page count
                content.computeSize()
                content.qtyPages = content.imageList.size
                ObjectBoxDB.insertContentCore(content)

                // Prune empty chapters
                val emptyChapters = content.chaptersList.filter { it.imageList.isEmpty() }
                emptyChapters.forEach { deleteChapter(it) }
            }
        }
    }

    override fun selectImageFile(id: Long): ImageFile? {
        return ObjectBoxDB.selectImageFile(id)
    }

    override fun selectImageFiles(ids: LongArray): List<ImageFile> {
        return ObjectBoxDB.selectImageFiles(ids)
    }

    override fun selectChapterImageFiles(ids: LongArray): List<ImageFile> {
        return ObjectBoxDB.selectChapterImageFiles(ids)
    }

    override fun flagImagesForDeletion(ids: LongArray, value: Boolean) {
        return ObjectBoxDB.flagImagesForDeletion(ids, value)
    }

    override fun selectDownloadedImagesFromContentLive(id: Long): LiveData<List<ImageFile>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectDownloadedImagesFromContentQ(id))
    }

    override fun selectDownloadedImagesFromContent(id: Long): List<ImageFile> {
        return ObjectBoxDB.selectDownloadedImagesFromContentQ(id).safeFind()
    }

    override fun countProcessedImagesById(contentId: Long): Map<StatusContent, Pair<Int, Long>> {
        return ObjectBoxDB.countProcessedImagesById(contentId)
    }

    override fun selectAllFavouritePagesLive(): LiveData<List<ImageFile>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectAllFavouritePagesQ())
    }

    override fun countAllFavouritePagesLive(): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val livedata = ObjectBoxLiveData(ObjectBoxDB.selectAllFavouritePagesQ())
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { result.value = it.size }
        return result
    }

    override fun selectPrimaryMemoryUsagePerSource(): Map<Site, Pair<Int, Long>> {
        return ObjectBoxDB.selectPrimaryMemoryUsagePerSource("")
    }

    override fun selectPrimaryMemoryUsagePerSource(rootPath: String): Map<Site, Pair<Int, Long>> {
        return ObjectBoxDB.selectPrimaryMemoryUsagePerSource(rootPath)
    }

    override fun selectExternalMemoryUsagePerSource(): Map<Site, Pair<Int, Long>> {
        return ObjectBoxDB.selectExternalMemoryUsagePerSource()
    }

    override fun addContentToQueue(
        content: Content,
        sourceImageStatus: StatusContent?,
        targetImageStatus: StatusContent?,
        position: QueuePosition,
        replacedContentId: Long,
        replacementTitle: String?,
        isQueueActive: Boolean
    ) {
        if (targetImageStatus != null) ObjectBoxDB.updateImageContentStatus(
            content.id,
            sourceImageStatus,
            targetImageStatus
        )
        content.status = StatusContent.PAUSED
        content.isBeingProcessed = false // Remove any UI animation
        if (replacedContentId > -1) content.setContentIdToReplace(replacedContentId)
        content.replacementTitle = replacementTitle ?: ""
        insertContent(content)
        if (!ObjectBoxDB.isContentInQueue(content)) {
            val targetPosition: Int =
                if (position == QueuePosition.BOTTOM) {
                    ObjectBoxDB.selectMaxQueueOrder().toInt() + 1
                } else { // Top - don't put #1 if queue is active not to interrupt current download
                    if (isQueueActive) 2 else 1
                }
            insertQueueAndRenumber(content.id, targetPosition)
        }
    }

    private fun insertQueueAndRenumber(contentId: Long, order: Int) {
        val queue = ObjectBoxDB.selectQueueRecordsQ().safeFind().toMutableList()
        val newRecord = QueueRecord(contentId, order)

        // Put in the right place
        if (order > queue.size) queue.add(newRecord) else {
            val newOrder = min((queue.size + 1).toDouble(), order.toDouble()).toInt()
            queue.add(newOrder - 1, newRecord)
        }
        // Renumber everything and save
        var index = 1
        for (qr in queue) qr.rank = index++
        ObjectBoxDB.updateQueue(queue)
    }

    override fun insertQueue(contentId: Long, order: Int) {
        ObjectBoxDB.insertQueue(contentId, order)
    }

    private fun getDynamicGroupContent(groupId: Long): LongArray {
        var result = emptyList<Long>()
        if (groupId > 0) {
            val g = selectGroup(groupId)
            if (g != null && g.grouping == Grouping.DYNAMIC) {
                result = selectGroupItemsByQuery(g).map { it.contentId }
            }
        }
        return result.toLongArray()
    }

    private fun contentIdSearch(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): List<Long> {
        return if (isUniversal) {
            ObjectBoxDB.selectContentUniversalId(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                ObjectBoxDB.libraryStatus
            ).toList()
        } else {
            ObjectBoxDB.selectContentSearchId(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                metadata,
                ObjectBoxDB.libraryStatus
            ).toList()
        }
    }

    private fun pagedAttributeSearch(
        attrTypes: List<AttributeType>,
        filter: String?,
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        attrs: Set<Attribute>?,
        location: Location,
        contentType: Type,
        includeFreeAttrs: Boolean,
        sortOrder: Int,
        pageNum: Int,
        itemPerPage: Int
    ): AttributeQueryResult {
        val result: MutableList<Attribute> = ArrayList()
        var totalSelectedAttributes: Long = 0
        if (attrTypes.isNotEmpty()) {
            if (attrTypes[0] == AttributeType.SOURCE) {
                result.addAll(
                    ObjectBoxDB.selectAvailableSources(
                        groupId,
                        dynamicGroupContentIds,
                        attrs,
                        location,
                        contentType,
                        includeFreeAttrs
                    )
                )
                totalSelectedAttributes = result.size.toLong()
            } else {
                for (type in attrTypes) {
                    // TODO fix sorting when concatenating both lists
                    result.addAll(
                        ObjectBoxDB.selectAvailableAttributes(
                            type,
                            groupId,
                            dynamicGroupContentIds,
                            attrs,
                            location,
                            contentType,
                            includeFreeAttrs,
                            filter,
                            sortOrder,
                            pageNum,
                            itemPerPage,
                            Settings.searchAttributesCount
                        )
                    )
                    totalSelectedAttributes += ObjectBoxDB.countAvailableAttributes(
                        type,
                        groupId,
                        dynamicGroupContentIds,
                        attrs,
                        location,
                        contentType,
                        includeFreeAttrs,
                        filter
                    )
                }
            }
        }
        return AttributeQueryResult(result, totalSelectedAttributes)
    }

    private fun countAttributes(
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        filter: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): SparseIntArray {
        val result: SparseIntArray
        if (filter.isNullOrEmpty() && Location.ANY == location && Type.ANY == contentType && -1L == groupId) {
            result = ObjectBoxDB.countAvailableAttributesPerType()
            result.put(AttributeType.SOURCE.code, ObjectBoxDB.selectAvailableSources().size)
        } else {
            result = ObjectBoxDB.countAvailableAttributesPerType(
                groupId,
                dynamicGroupContentIds,
                filter,
                location,
                contentType
            )
            result.put(
                AttributeType.SOURCE.code,
                ObjectBoxDB.selectAvailableSources(
                    groupId,
                    dynamicGroupContentIds,
                    filter,
                    location,
                    contentType,
                    false
                ).size
            )
        }
        return result
    }

    override fun selectQueueLive(): LiveData<List<QueueRecord>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectQueueRecordsQ())
    }

    override fun selectQueueLive(query: String?, source: Site?): LiveData<List<QueueRecord>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectQueueRecordsQ(query, source))
    }

    override fun selectQueue(): List<QueueRecord> {
        return ObjectBoxDB.selectQueueRecordsQ().safeFind()
    }

    override fun selectQueueUrls(site: Site): Set<String> {
        return ObjectBoxDB.selectQueueUrls(site)
    }

    override fun updateQueue(queue: List<QueueRecord>) {
        ObjectBoxDB.updateQueue(queue)
    }

    override fun deleteQueue(content: Content) {
        ObjectBoxDB.deleteQueueRecords(content)
    }

    override fun deleteQueue(index: Int) {
        ObjectBoxDB.deleteQueueRecords(index)
    }

    override fun deleteQueueRecordsCore() {
        ObjectBoxDB.deleteQueueRecords()
    }

    override fun selectHistory(s: Site): SiteHistory {
        return ObjectBoxDB.selectHistory(s) ?: SiteHistory()
    }

    override fun selectHistory(): List<SiteHistory> {
        return ObjectBoxDB.selectHistory()
    }

    override fun insertSiteHistory(site: Site, url: String, timestamp: Long) {
        ObjectBoxDB.insertSiteHistory(site, url, timestamp)
    }

    // BOOKMARKS
    override fun countAllBookmarks(): Long {
        return ObjectBoxDB.selectBookmarksQ(null).safeCount()
    }

    override fun selectAllBookmarks(): List<SiteBookmark> {
        return ObjectBoxDB.selectBookmarksQ(null).safeFind()
    }

    override fun deleteAllBookmarks() {
        ObjectBoxDB.selectBookmarksQ(null).safeRemove()
    }

    override fun selectBookmarks(s: Site): List<SiteBookmark> {
        return ObjectBoxDB.selectBookmarksQ(s).safeFind()
    }

    override fun selectHomepage(s: Site): SiteBookmark? {
        return ObjectBoxDB.selectHomepage(s)
    }

    override fun insertBookmark(bookmark: SiteBookmark): Long {
        // Auto-number max order when not provided
        if (-1 == bookmark.order) bookmark.order =
            ObjectBoxDB.getMaxBookmarkOrderFor(bookmark.site) + 1
        return ObjectBoxDB.insertBookmark(bookmark)
    }

    override fun insertBookmarks(bookmarks: List<SiteBookmark>) {
        // Mass insert method; no need to renumber here
        ObjectBoxDB.insertBookmarks(bookmarks)
    }

    override fun deleteBookmark(bookmarkId: Long) {
        ObjectBoxDB.deleteBookmark(bookmarkId)
    }

    override fun deleteBookmarks(bookmarkIds: List<Long>) {
        ObjectBoxDB.deleteBookmarks(bookmarkIds)
    }


    // SEARCH HISTORY
    override fun selectSearchRecordsLive(): LiveData<List<SearchRecord>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectSearchRecordsQ())
    }

    private fun selectSearchRecords(entityType: SearchRecord.EntityType): List<SearchRecord> {
        return ObjectBoxDB.selectSearchRecordsQ(entityType).safeFind()
    }

    override fun insertSearchRecord(record: SearchRecord, limit: Int) {
        record.timestamp = Instant.now().toEpochMilli()

        val records = selectSearchRecords(record.entityType).toMutableList()
        val existing = records.firstOrNull { it == record }
        if (existing != null) {
            // Update timestamp on existing entry
            existing.timestamp = record.timestamp
            ObjectBoxDB.insertSearchRecords(listOf(existing))
            return
        }

        while (records.size >= limit) {
            ObjectBoxDB.deleteSearchRecord(records.first().id)
            records.removeAt(0)
        }
        records.add(record)
        ObjectBoxDB.insertSearchRecords(records)
    }

    override fun deleteAllSearchRecords(entityType: SearchRecord.EntityType) {
        ObjectBoxDB.selectSearchRecordsQ(entityType).safeRemove()
    }


    // RENAMING RULES
    override fun selectRenamingRule(id: Long): RenamingRule? {
        return ObjectBoxDB.selectRenamingRule(id)
    }

    override fun selectRenamingRulesLive(
        type: AttributeType,
        nameFilter: String?
    ): LiveData<List<RenamingRule>> {
        return ObjectBoxLiveData(
            ObjectBoxDB.selectRenamingRulesQ(
                type,
                nameFilter ?: ""
            )
        )
    }

    override fun selectRenamingRules(type: AttributeType, nameFilter: String?): List<RenamingRule> {
        return ObjectBoxDB.selectRenamingRulesQ(type, nameFilter ?: "").safeFind()
    }

    override fun insertRenamingRule(rule: RenamingRule): Long {
        return ObjectBoxDB.insertRenamingRule(rule)
    }

    override fun insertRenamingRules(rules: List<RenamingRule>) {
        ObjectBoxDB.insertRenamingRules(rules)
    }

    override fun deleteRenamingRules(ids: List<Long>) {
        ObjectBoxDB.deleteRenamingRules(ids.toLongArray())
    }


    // ONE-TIME USE QUERIES (MIGRATION & CLEANUP)
    override fun selectContentIdsWithUpdatableJson(): LongArray {
        return ObjectBoxDB.selectContentIdsWithUpdatableJson()
    }
}
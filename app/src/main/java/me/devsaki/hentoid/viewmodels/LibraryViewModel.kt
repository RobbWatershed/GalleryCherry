package me.devsaki.hentoid.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.paging.PagedList
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.buildSearchUri
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.SEED_CONTENT
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.SearchRecord
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.Location
import me.devsaki.hentoid.util.MergerLiveData
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.RandomSeed
import me.devsaki.hentoid.util.SearchCriteria
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getParent
import me.devsaki.hentoid.util.isDownloadable
import me.devsaki.hentoid.util.moveContentToCustomGroup
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.parseFromScratch
import me.devsaki.hentoid.util.persistJson
import me.devsaki.hentoid.util.persistLocationCredentials
import me.devsaki.hentoid.util.purgeContent
import me.devsaki.hentoid.util.reparseFromScratch
import me.devsaki.hentoid.util.splitUniqueStr
import me.devsaki.hentoid.util.updateGroupsJson
import me.devsaki.hentoid.util.updateJson
import me.devsaki.hentoid.widget.ContentSearchManager
import me.devsaki.hentoid.widget.FolderSearchManager
import me.devsaki.hentoid.widget.GroupSearchManager
import me.devsaki.hentoid.workers.BaseDeleteWorker
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.MergeWorker
import me.devsaki.hentoid.workers.SplitMergeType
import me.devsaki.hentoid.workers.SplitWorker
import me.devsaki.hentoid.workers.UpdateJsonWorker
import me.devsaki.hentoid.workers.data.DeleteData
import me.devsaki.hentoid.workers.data.SplitMergeData
import me.devsaki.hentoid.workers.data.UpdateJsonData
import timber.log.Timber
import java.security.InvalidParameterException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LibraryViewModel(application: Application, val dao: CollectionDAO) :
    AndroidViewModel(application) {

    // Search managers
    private val contentSearchManager = ContentSearchManager()
    private val groupSearchManager = GroupSearchManager()
    private val folderSearchManager = FolderSearchManager()

    // Cleanup for all work observers
    private val workObservers: MutableList<Pair<UUID, Observer<WorkInfo?>>> = ArrayList()

    // Content data
    private var currentSource: LiveData<PagedList<Content>>? = null
    val totalContent: LiveData<Int> = dao.countAllBooksLive()
    val libraryPaged = MediatorLiveData<PagedList<Content>>()
    val contentSearchBundle = MutableLiveData<Bundle>()

    // Groups data
    val group = MutableLiveData<Group>()
    private var currentGroupsSource: LiveData<Pair<List<Group>, Int>>? = null
    val groups = MediatorLiveData<Pair<List<Group>, Int>>()

    val groupSearchBundle = MutableLiveData<Bundle>()

    // Folders data
    val folderRoot = MutableLiveData<Uri>()
    val folders = MediatorLiveData<List<DisplayFile>>()
    val foldersDetail = MediatorLiveData<List<DisplayFile>>()
    val folderSearchBundle = MutableLiveData<Bundle>()
    val parentsCache = HashMap<Uri, Uri>() // Key = child folder, Value = parent folder
    val detailsFlowKillSwitch = AtomicBoolean(true)

    // True if there's at least one existing custom group; false instead
    val isCustomGroupingAvailable = MutableLiveData<Boolean>()

    // True if there's at least one existing dynamic group; false instead
    val isDynamicGroupingAvailable = MutableLiveData<Boolean>()


    // Other data
    val searchRecords: LiveData<List<SearchRecord>> = dao.selectSearchRecordsLive()
    val totalQueue: LiveData<Int> = dao.countAllQueueBooksLive()
    val favPages: LiveData<Int> = dao.countAllFavouritePagesLive()

    // Updated whenever a new Contentsearch is performed
    val newContentSearch = MediatorLiveData<Boolean>()


    init {
        refreshAvailableGroupings()
    }

    fun onSaveState(outState: Bundle) {
        contentSearchManager.saveToBundle(outState)
        groupSearchManager.saveToBundle(outState)
    }

    fun onRestoreState(savedState: Bundle?) {
        if (savedState == null) return
        contentSearchManager.loadFromBundle(savedState)
        groupSearchManager.loadFromBundle(savedState)
    }

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
        if (workObservers.isEmpty()) {
            val workManager = WorkManager.getInstance(getApplication())
            for (info in workObservers)
                workManager.getWorkInfoByIdLiveData(info.first).removeObserver(info.second)
        }
        folderSearchManager.clear()
    }


    // =========================
    // ========= LIBRARY ACTIONS
    // =========================
    /**
     * Perform a new library search
     */
    private suspend fun doSearchContent(resetTags: Boolean = false) {
        // Update search properties set directly through Preferences
        contentSearchManager.setContentSortField(Settings.contentSortField)
        contentSearchManager.setContentSortDesc(Settings.isContentSortDesc)
        if (Settings.getGroupingDisplayG() == Grouping.FLAT) {
            contentSearchManager.setGroup(null)
            if (resetTags) {
                contentSearchManager.clearTags()
                contentSearchManager.clearExcludedAttrs()
                contentSearchManager.setLocation(Location.ANY.value)
                contentSearchManager.setContentType(Type.ANY.value)
            }
        }
        val newSource = withContext(Dispatchers.IO) {
            try {
                contentSearchManager.getLibrary(dao)
            } finally {
                dao.cleanup()
            }
        }
        synchronized(contentSearchManager) {
            currentSource?.let { libraryPaged.removeSource(it) }
            currentSource = newSource
            currentSource?.let { libraryPaged.addSource(it) { s -> libraryPaged.value = s } }
        }
        contentSearchBundle.postValue(contentSearchManager.toBundle())
    }

    /**
     * Perform a new content universal search using the given query
     *
     * @param query Query to use for the universal search
     */
    fun searchContentUniversal(query: String) {
        // If user searches in main toolbar, universal search takes over advanced search
        contentSearchManager.clearTags()
        contentSearchManager.setLocation(Location.ANY.value)
        contentSearchManager.setContentType(Type.ANY.value)
        contentSearchManager.setQuery(query)
        newContentSearch.value = true
        if (query.isNotEmpty()) {
            val searchUri = buildSearchUri(null, query = query)
            dao.insertSearchRecord(SearchRecord.contentSearch(searchUri), 10)
            dao.cleanup()
        }
        viewModelScope.launch { doSearchContent() }
    }

    /**
     * Perform a new content search using the given query and metadata
     *
     * @param query    Query to use for the search
     * @param criteria Metadata to use for the search
     */
    fun searchContent(query: String, criteria: SearchCriteria, searchUri: Uri) {
        contentSearchManager.setQuery(query)
        contentSearchManager.setExcludedAttrs(criteria.excludedAttributeTypes)
        contentSearchManager.setTags(criteria.attributes)
        contentSearchManager.setLocation(criteria.location.value)
        contentSearchManager.setContentType(criteria.contentType.value)
        newContentSearch.value = true
        if (!criteria.isEmpty()) {
            dao.insertSearchRecord(
                SearchRecord.contentSearch(
                    searchUri,
                    criteria.toString(getApplication())
                ), 10
            )
            dao.cleanup()
        }
        viewModelScope.launch { doSearchContent() }
    }

    fun clearContent() {
        currentSource?.let { libraryPaged.removeSource(it) }
        currentSource = dao.selectNoContent()
        currentSource?.let { s -> libraryPaged.addSource(s) { libraryPaged.value = it } }
        dao.cleanup()
    }

    fun searchGroup() {
        viewModelScope.launch { doSearchGroup() }
    }

    private suspend fun doSearchGroup() {
        // Update search properties set directly through Preferences
        groupSearchManager.setSortField(Settings.groupSortField)
        groupSearchManager.setSortDesc(Settings.isGroupSortDesc)
        groupSearchManager.setGrouping(Settings.getGroupingDisplayG())
        groupSearchManager.setArtistGroupVisibility(Settings.artistGroupVisibility)

        withContext(Dispatchers.IO) {
            try {
                val newSource = groupSearchManager.getGroups(dao)
                val newTotalSource = groupSearchManager.getAllGroups(dao)

                // Send results and total count with the same LiveData
                val combined =
                    MergerLiveData.Two(newSource, newTotalSource, false) { data1, data2 ->
                        Pair(data1, data2)
                    }

                withContext(Dispatchers.Main) {
                    synchronized(groupSearchManager) {
                        currentGroupsSource?.let { groups.removeSource(it) }
                        currentGroupsSource = combined
                        currentGroupsSource?.let { groups.addSource(it) { s -> groups.value = s } }
                    }
                }
            } finally {
                dao.cleanup()
            }
        }

        groupSearchBundle.postValue(groupSearchManager.toBundle())
        refreshAvailableGroupings()
    }

    fun refreshAvailableGroupings() {
        isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0)
        isDynamicGroupingAvailable.postValue(dao.countGroupsFor(Grouping.DYNAMIC) > 0)
        dao.cleanup()
    }

    private suspend fun doSearchFolders() {
        detailsFlowKillSwitch.set(true)
        val root = folderRoot.value ?: Uri.EMPTY
        val isARoot = Settings.libraryFoldersRoots.contains(root.toString()) || root == Uri.EMPTY
        val ctx: Context = getApplication()
        if (isARoot) {
            folderSearchManager.clear()
            folderRoot.postValue(Uri.EMPTY)
            // Display roots (level 0)
            withContext(Dispatchers.IO) {
                val entries = ArrayList<DisplayFile>()
                // "Add root" button
                entries.add(
                    DisplayFile(
                        ctx.resources.getString(R.string.add_root),
                        DisplayFile.Type.ADD_BUTTON
                    )
                )
                // External library (if plugged)
                if (Settings.externalLibraryUri.isNotBlank()) {
                    entries.add(
                        DisplayFile(
                            ctx.resources.getString(R.string.external_library),
                            DisplayFile.Type.ROOT_FOLDER,
                            DisplayFile.SubType.EXTERNAL_LIB,
                            Settings.externalLibraryUri.toUri()
                        )
                    )
                }
                // Root entries
                Settings.libraryFoldersRoots.forEach { r ->
                    getDocumentFromTreeUriString(ctx, r)?.let { entries.add(DisplayFile(it)) }
                }
                folders.postValue(entries)
            }
        } else {
            // Search actual folders
            folderSearchManager.setSortField(Settings.folderSortField)
            folderSearchManager.setSortDesc(Settings.isFolderSortDesc)

            // First retrieval = minimal data
            withContext(Dispatchers.IO) {
                val files = folderSearchManager.getFoldersFast(ctx, root)
                    .filterNot { it.type == DisplayFile.Type.OTHER }
                folders.postValue(files)
                // Fill parents cache
                files.filter { it.type == DisplayFile.Type.FOLDER }.forEach {
                    parentsCache[it.uri] = it.parent
                }
                folderSearchBundle.postValue(folderSearchManager.toBundle())
            }

            // Details
            withContext(Dispatchers.IO) {
                detailsFlowKillSwitch.set(false)
                folderSearchManager.getFoldersDetails(ctx, root)
                    .takeWhile { !detailsFlowKillSwitch.get() }
                    .filterNot { it.type == DisplayFile.Type.OTHER }
                    .map { enrichWithMetadata(it, dao) }
                    .transform {
                        // Fill parents cache
                        if (it.type == DisplayFile.Type.FOLDER) parentsCache[it.uri] = it.parent
                        emit(it)
                    }
                    .scan(ArrayList<DisplayFile>()) { accumulator, value ->
                        accumulator.add(value)
                        accumulator
                    }
                    .collect { foldersDetail.postValue(it) }
                dao.cleanup()
            }
        }
    }

    private fun enrichWithMetadata(f: DisplayFile, dao: CollectionDAO): DisplayFile {
        dao.selectContentByStorageUri(f.uri.toString(), false)?.let {
            Timber.d("Mapped metadata for ${it.title}")
            f.coverUri = it.cover.usableUri.toUri()
            f.contentId = it.id
        }
        return f
    }

    /**
     * Toggle the completed filter
     */
    fun setCompletedFilter(value: Boolean) {
        contentSearchManager.setFilterBookCompleted(value)
        newContentSearch.value = true
        viewModelScope.launch { doSearchContent() }
    }

    /**
     * Toggle the "not completed" filter
     */
    fun setNotCompletedFilter(value: Boolean) {
        contentSearchManager.setFilterBookNotCompleted(value)
        newContentSearch.value = true
        viewModelScope.launch { doSearchContent() }
    }

    /**
     * Toggle the books favourite filter
     */
    fun setContentFavouriteFilter(value: Boolean) {
        contentSearchManager.setFilterBookFavourites(value)
        newContentSearch.value = true
        viewModelScope.launch { doSearchContent() }
    }

    /**
     * Toggle the books non-favourite filter
     */
    fun setContentNonFavouriteFilter(value: Boolean) {
        contentSearchManager.setFilterBookNonFavourites(value)
        newContentSearch.value = true
        viewModelScope.launch { doSearchContent() }
    }

    /**
     * Toggle the groups favourite filter
     */
    fun setGroupFavouriteFilter(value: Boolean) {
        groupSearchManager.setFilterFavourites(value)
        viewModelScope.launch { doSearchGroup() }
    }

    /**
     * Toggle the groups non-favourite filter
     */
    fun setGroupNonFavouriteFilter(value: Boolean) {
        groupSearchManager.setFilterNonFavourites(value)
        viewModelScope.launch { doSearchGroup() }
    }

    /**
     * Toggle the books rating filter
     */
    fun setContentRatingFilter(value: Int) {
        contentSearchManager.setFilterRating(value)
        newContentSearch.value = true
        viewModelScope.launch { doSearchContent() }
    }

    /**
     * Toggle the groups rating filter
     */
    fun setGroupRatingFilter(value: Int) {
        groupSearchManager.setFilterRating(value)
        viewModelScope.launch { doSearchGroup() }
    }

    fun setGroupQuery(value: String) {
        if (value.isNotEmpty()) {
            val searchUri = buildSearchUri(null, query = value)
            dao.insertSearchRecord(SearchRecord.groupSearch(searchUri), 10)
            dao.cleanup()
        }
        groupSearchManager.setQuery(value)
        viewModelScope.launch { doSearchGroup() }
    }

    fun goUpOneFolder() {
        val currentFolder = folderRoot.value ?: return
        parentsCache[currentFolder]?.let { setFolderRoot(it) }
            ?: run {
                // Identify the current folder's parent and get there
                Settings.libraryFoldersRoots.firstOrNull { currentFolder.toString().startsWith(it) }
                    ?.let { r ->
                        getParent(getApplication(), r.toUri(), currentFolder)
                            ?.let { setFolderRoot(it) }
                    } ?: run { setFolderRoot(Uri.EMPTY) }
            }
    }

    fun searchFolder() {
        viewModelScope.launch { doSearchFolders() }
    }

    fun setFolderRoot(value: Uri) {
        folderRoot.value = value
        viewModelScope.launch { doSearchFolders() }
    }

    fun setFolderQuery(value: String) {
        folderSearchManager.setQuery(value)
        viewModelScope.launch { doSearchFolders() }
    }

    fun setGrouping(groupingId: Int) {
        val currentGrouping = Settings.groupingDisplay
        if (groupingId != currentGrouping) {
            Settings.groupingDisplay = groupingId
            // Already handled by LibraryActivity.onSharedPreferenceChanged
            if (currentGrouping == Grouping.FLAT.id) return

            when (groupingId) {
                Grouping.FLAT.id -> viewModelScope.launch { doSearchContent(true) }
                Grouping.FOLDERS.id -> viewModelScope.launch { doSearchFolders() }
                else -> viewModelScope.launch { doSearchGroup() }
            }
        }
    }

    fun clearGroupFilters() {
        groupSearchManager.clearFilters()
        viewModelScope.launch { doSearchGroup() }
    }

    fun clearContentFilters() {
        contentSearchManager.clearFilters()
        viewModelScope.launch { doSearchContent() }
    }

    fun clearFolderFilters() {
        folderSearchManager.clearFilters()
        viewModelScope.launch { doSearchFolders() }
    }

    /**
     * Set Content paging mode (endless or paged)
     */
    fun setContentPagingMethod(isEndless: Boolean) {
        contentSearchManager.setLoadAll(!isEndless)
        newContentSearch.value = true
        viewModelScope.launch { doSearchContent() }
    }

    fun searchContent(active: Boolean = true) {
        newContentSearch.value = active
        viewModelScope.launch { doSearchContent() }
    }

    fun setGroup(group: Group, forceRefresh: Boolean) {
        val currentGroup = this.group.value
        if (!forceRefresh && group == currentGroup) return

        // Reset content sorting to TITLE when reaching the Ungrouped group with CUSTOM sorting (can't work)
        if (Settings.Value.ORDER_FIELD_CUSTOM == Settings.contentSortField && (!group.grouping.canReorderBooks || group.isUngroupedGroup))
            Settings.contentSortField = Settings.Value.ORDER_FIELD_TITLE
        this.group.postValue(group)

        if (Grouping.DYNAMIC == group.grouping) {
            val searchUri = SearchActivityBundle.parseSearchUri(group.searchUri)
            contentSearchManager.setTags(searchUri.attributes)
            contentSearchManager.setExcludedAttrs(searchUri.excludedAttributeTypes)
            contentSearchManager.setLocation(searchUri.location.value)
            contentSearchManager.setContentType(searchUri.contentType.value)
        } else contentSearchManager.setGroup(group)

        newContentSearch.value = true
        // Don't search now as the UI will inevitably search as well upon switching to books view
        // TODO only useful when browsing custom groups ?
        viewModelScope.launch { doSearchContent(Settings.getGroupingDisplayG() == Grouping.FLAT) }
    }

    // =========================
    // ========= CONTENT ACTIONS
    // =========================
    fun toggleContentCompleted(content: List<Content>, onSuccess: Runnable) {
        viewModelScope.launch {
            try {
                doToggleContentCompleted(content.map { it.id })
            } catch (t: Throwable) {
                Timber.e(t)
            }
            onSuccess.run()
        }
    }

    /**
     * Toggle the "completed" state of the given content
     *
     * @param ids ID of the content whose completed state to toggle
     */
    private suspend fun doToggleContentCompleted(ids: List<Long>) = withContext(Dispatchers.IO) {
        // Check if given content still exists in DB
        ids.forEach {
            val theContent = dao.selectContent(it)
                ?: throw InvalidParameterException("Invalid ContentId : $it")

            if (theContent.isBeingProcessed) return@forEach

            theContent.completed = !theContent.completed
            persistJson(getApplication(), theContent)
            dao.insertContentCore(theContent)
        }
        dao.cleanup()
    }

    fun resetReadStats(content: List<Content>, onSuccess: Runnable) {
        viewModelScope.launch {
            try {
                doResetReadStats(content.map { it.id })
            } catch (t: Throwable) {
                Timber.e(t)
            }
            onSuccess.run()
        }
    }

    /**
     * Reset read stats of the given content
     *
     * @param ids ID of the content whose read stats to reset
     */
    private suspend fun doResetReadStats(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach {
            // Check if given content still exists in DB
            val theContent = dao.selectContent(it)
                ?: throw InvalidParameterException("Invalid ContentId : $it")

            if (theContent.isBeingProcessed) return@forEach

            theContent.reads = 0
            theContent.readPagesCount = 0
            theContent.lastReadPageIndex = 0
            theContent.lastReadDate = 0
            val imgs = theContent.imageFiles
            for (img in imgs) img.read = false
            dao.insertImageFiles(imgs)
            persistJson(getApplication(), theContent)
            dao.insertContentCore(theContent)
        }
        dao.cleanup()
    }

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param content Content whose favourite state to toggle
     */
    fun toggleContentFavourite(content: Content, onSuccess: Runnable) {
        viewModelScope.launch {
            try {
                doToggleContentFavourite(content.id)
            } catch (t: Throwable) {
                Timber.e(t)
            } finally {
                dao.cleanup()
            }
            onSuccess.run()
        }
    }

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param contentId ID of the content whose favourite state to toggle
     * @return Resulting content
     */
    private suspend fun doToggleContentFavourite(contentId: Long) =
        withContext(Dispatchers.IO) {
            // Check if given content still exists in DB
            val theContent = dao.selectContent(contentId)
                ?: throw InvalidParameterException("Invalid ContentId : $contentId")

            theContent.favourite = !theContent.favourite
            persistJson(getApplication(), theContent)
            dao.insertContent(theContent)
            dao.cleanup()
        }

    /**
     * Set the rating to the given value for the given content IDs
     *
     * @param contentIds   Content IDs to set the rating for
     * @param targetRating Rating to set
     * @param onSuccess    Runnable to call if the operation succeeds
     */
    fun rateContents(contentIds: List<Long>, targetRating: Int, onSuccess: Runnable) {
        viewModelScope.launch {
            try {
                doRateContent(contentIds, targetRating)
            } catch (t: Throwable) {
                Timber.e(t)
            }
            onSuccess.run()
        }
    }

    /**
     * Set the rating to the given value for the given content ID
     *
     * @param ids    Content ID to set the rating for
     * @param targetRating Rating to set
     */
    private suspend fun doRateContent(ids: List<Long>, targetRating: Int) =
        withContext(Dispatchers.IO) {
            ids.forEach {
                // Check if given content still exists in DB
                val theContent = dao.selectContent(it)
                    ?: throw InvalidParameterException("Invalid ContentId : $it")

                theContent.rating = targetRating
                persistJson(getApplication(), theContent)
                dao.insertContent(theContent)
            }
            dao.cleanup()
        }

    /**
     * General purpose download/redownload
     * @reparseContent : True to reparse Content metadata from the site
     * @reparseImages : True to reparse and redownload images from the site
     */
    fun downloadContent(
        contentList: List<Content>,
        reparseContent: Boolean,
        reparseImages: Boolean,
        position: QueuePosition,
        onSuccess: Consumer<Int>,
        onError: Consumer<Throwable>
    ) {
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            if (WebkitPackageHelper.getWebViewUpdating()) onError.invoke(
                EmptyResultException(
                    getApplication<Application>().getString(R.string.download_updating_webview)
                )
            ) else onError.invoke(EmptyResultException(getApplication<Application>().getString(R.string.download_missing_webview)))
            return
        }

        // Flag contents as "being processed" (trigger blink animation)
        dao.updateContentsProcessedFlag(contentList, true)

        val sourceImageStatus = if (reparseImages) null else StatusContent.ERROR
        val targetImageStatus =
            if (!reparseImages) null else if (reparseContent) StatusContent.ERROR else StatusContent.SAVED

        val nbErrors = AtomicInteger(0)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentList.forEach { c ->
                        var res: Content? = c
                        var areModifiedImages = false

                        // Merged books
                        val chaps = c.chaptersList.toMutableList() // Safe copy
                        if (c.manuallyMerged && chaps.isNotEmpty()) { // Merged with chapters
                            // TODO do something smart when some images have been deleted

                            // Reparse main book from scratch if images are KO
                            if (reparseContent || !isDownloadable(c)) {
                                if (!reparseContent) Timber.d("Pages unreachable; reparsing content")
                                // Reparse content itself
                                res = reparseFromScratch(c)
                            }

                            // Reparse chapters from scratch if images are KO
                            chaps.forEachIndexed { idx, ch ->
                                if (res != null) {
                                    if (reparseContent || !isDownloadable(ch)) {
                                        if (!reparseContent) Timber.d("Pages unreachable; reparsing chapter $idx")
                                        if (parseFromScratch(ch.url) != null) {
                                            // Flagging all pics as ERROR; will be reparsed by the downloader
                                            ch.imageList.forEach { it.status = StatusContent.ERROR }
                                            areModifiedImages = true
                                        } else res = null
                                    }
                                }
                            }
                            res?.let {
                                it.setChapters(chaps)
                                it.setImageFiles(chaps.flatMap { ch -> ch.imageList })
                            }
                        } else { // Classic content
                            val isDownloadable = isDownloadable(c)
                            if (reparseContent || !isDownloadable) {
                                var msg = "Reparsing content"
                                if (!isDownloadable) msg += " (pages unreachable)"
                                Timber.d(msg)
                                // Reparse content itself
                                res = reparseFromScratch(c, reparseImages)
                            }
                        }

                        res?.let {
                            it.downloadMode = DownloadMode.DOWNLOAD
                            if (areModifiedImages) {
                                dao.insertChapters(it.chaptersList)
                                dao.insertImageFiles(it.imageList)
                            }

                            dao.addContentToQueue(
                                it, sourceImageStatus, targetImageStatus, position, -1, null,
                                isQueueActive(getApplication())
                            )
                            // Non-blocking performance bottleneck; run in a dedicated worker
                            if (reparseImages) purgeContent(
                                getApplication(),
                                it,
                                keepCover = false
                            )
                        } ?: run { // Undownloadable book => cancel operation
                            dao.updateContentProcessedFlag(c.id, false)
                            nbErrors.incrementAndGet()
                            onError.invoke(
                                if (c.manuallyMerged && chaps.isNotEmpty()) EmptyResultException(
                                    getApplication<Application>().getString(R.string.download_canceled_merged)
                                )
                                else EmptyResultException(
                                    getApplication<Application>().getString(R.string.download_canceled)
                                )
                            )
                        }
                    } // Each content
                    dao.cleanup()
                } // Dispatchers.IO
                if (Settings.isQueueAutostart) resumeQueue(getApplication())
                onSuccess.invoke(contentList.size - nbErrors.get())
            } catch (t: Throwable) {
                // Unflag contents as "being processed" (cancel blink animation)
                dao.updateContentsProcessedFlag(contentList, false)
                onError.invoke(t)
            }
        } // ViewModelScope
        dao.cleanup()
    }

    /**
     * Delete the given list of content
     */
    fun deleteItems(
        contentIds: List<Long>,
        groupIds: List<Long>,
        deleteGroupsOnly: Boolean = false,
        onSuccess: Runnable? = null
    ) {
        val builder = DeleteData.Builder()
        builder.setOperation(BaseDeleteWorker.Operation.DELETE)
        if (contentIds.isNotEmpty()) builder.setContentIds(contentIds)
        if (groupIds.isNotEmpty()) builder.setGroupIds(groupIds)
        builder.setDeleteGroupsOnly(deleteGroupsOnly)
        val workManager = WorkManager.getInstance(getApplication())
        val request: WorkRequest =
            OneTimeWorkRequest.Builder(DeleteWorker::class.java).setInputData(builder.data).build()
        workManager.enqueue(request)
        val workInfoObserver =
            Observer { workInfo: WorkInfo? ->
                workInfo?.let {
                    if (it.state.isFinished) {
                        onSuccess?.run()
                        refreshAvailableGroupings()
                    }
                }
            }
        workObservers.add(Pair(request.id, workInfoObserver))
        workManager.getWorkInfoByIdLiveData(request.id).observeForever(workInfoObserver)
    }

    fun deleteOnStorage(uris: Collection<String>, onSuccess: Runnable? = null) {
        val builder = DeleteData.Builder()
        builder.setOperation(BaseDeleteWorker.Operation.DELETE)
        if (uris.isNotEmpty()) builder.setDocUris(uris)
        doDeleteOnStorage(builder.data, onSuccess)
    }

    fun doDeleteOnStorage(data: Data, onSuccess: Runnable? = null) {
        val workManager = WorkManager.getInstance(getApplication())
        val request: WorkRequest =
            OneTimeWorkRequest.Builder(DeleteWorker::class.java).setInputData(data).build()
        workManager.enqueue(request)
        val workInfoObserver =
            Observer { workInfo: WorkInfo? ->
                workInfo?.let {
                    if (it.state.isFinished) {
                        onSuccess?.run()
                    }
                }
            }
        workObservers.add(Pair(request.id, workInfoObserver))
        workManager.getWorkInfoByIdLiveData(request.id).observeForever(workInfoObserver)
    }

    /**
     * Stream the given list of content
     *
     * @param contents List of content to be streamed
     */
    fun streamContent(
        contents: List<Content>,
        onError: Consumer<Throwable>
    ) {
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            if (WebkitPackageHelper.getWebViewUpdating()) onError.invoke(
                EmptyResultException(
                    getApplication<Application>().getString(R.string.stream_updating_webview)
                )
            ) else onError.invoke(EmptyResultException(getApplication<Application>().getString(R.string.stream_missing_webview)))
            return
        }

        val builder = DeleteData.Builder()
        builder.setOperation(BaseDeleteWorker.Operation.STREAM)
        if (contents.isNotEmpty()) builder.setContentIds(contents.map { it.id })
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.delete_service_stream.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(
                DeleteWorker::class.java
            ).setInputData(builder.data).build()
        )
    }

    fun attachFolderRoot(uri: Uri): Boolean {
        val roots = Settings.libraryFoldersRoots.toMutableList()
        if (roots.contains(uri.toString())) return false

        // Persist I/O permissions; keep existing ones if present
        persistLocationCredentials(getApplication(), uri)
        roots.add(uri.toString())
        Settings.libraryFoldersRoots = roots
        searchFolder()
        return true
    }

    fun detachFolderRoots(uris: List<Uri>) {
        val roots = Settings.libraryFoldersRoots.toMutableList()
        roots.removeAll(uris.map {
            val str = it.toString()
            val docPos = str.indexOf("/document/")
            str.substring(0, docPos)
        })
        Settings.libraryFoldersRoots = roots

        // Remove corresponding books from DB; identify JSONs to be deleted
        viewModelScope.launch {
            val jsons = ArrayList<String>()
            withContext(Dispatchers.IO) {
                uris.forEach {
                    dao.selectContentByStorageRootUri(it.toString()).forEach { r ->
                        try {
                            if (r.jsonUri.isNotBlank()) jsons.add(r.jsonUri.toUri().toString())
                            dao.deleteContent(r)
                        } catch (t: Throwable) {
                            Timber.w(t)
                        }
                    }
                }
                dao.cleanup()
            }
            deleteOnStorage(jsons)
            searchFolder()
        }
    }

    fun setGroupCoverContent(group: Group, coverContent: Content) {
        // Check if given group still exists in DB
        var theGroup = dao.selectGroup(group.id)
        if (null == theGroup && Settings.groupingDisplay == Grouping.ARTIST.id) {
            // Create flagged group
            theGroup = Group(Grouping.ARTIST, group.name, -1)
            theGroup.subtype = group.subtype

            // Persist in it DB
            theGroup.id = dao.insertGroup(theGroup)
        }
        theGroup ?: throw InvalidParameterException("Invalid GroupId : ${group.id}")

        theGroup.coverContent.setAndPutTarget(coverContent)
        dao.cleanup()
    }

    fun saveContentPositions(
        orderedContent: List<Content>,
        onSuccess: Runnable
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doSaveContentPositions(orderedContent)
                    updateGroupsJson(getApplication(), dao)
                    dao.cleanup()
                }
                onSuccess.run()
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun doSaveContentPositions(orderedContent: List<Content>) {
        val localGroup = group.value ?: return

        // Update the "has custom book order" group flag
        localGroup.hasCustomBookOrder = true
        var order = 0
        for (c in orderedContent) for (gi in localGroup.getItems()) if (gi.contentId == c.id) {
            gi.order = order++
            dao.insertGroupItem(gi)
            break
        }
        dao.insertGroup(localGroup)
        dao.cleanup()
    }

    fun saveGroupPositions(orderedGroups: List<Group>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doSaveGroupPositions(orderedGroups)
                    updateGroupsJson(getApplication(), dao)
                    dao.cleanup()
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun doSaveGroupPositions(orderedGroups: List<Group>) {
        var order = 0
        for (g in orderedGroups) {
            g.order = order++
            dao.insertGroup(g)
        }
        dao.cleanup()
    }

    fun getGroupContents(group: Group): List<Content> {
        return dao.selectContent(group.contentIds.toLongArray())
    }

    fun newGroup(
        grouping: Grouping,
        newGroupName: String,
        searchUri: String?,
        onNameExists: Runnable
    ) {
        // Check if the group already exists
        val groupingGroups = dao.selectGroups(grouping.id)
        val groupMatchingName = groupingGroups.filter { g ->
            g.name.equals(newGroupName, ignoreCase = true)
        }
        if (groupMatchingName.isNotEmpty()) { // Existing group with the same name
            onNameExists.run()
        } else {
            val newGroup = Group(grouping, newGroupName, -1)
            if (!searchUri.isNullOrEmpty()) newGroup.searchUri = searchUri
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        dao.insertGroup(newGroup)
                        refreshAvailableGroupings()
                        updateGroupsJson(getApplication(), dao)
                        dao.cleanup()
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
        dao.cleanup()
    }

    fun renameGroup(
        group: Group, newGroupName: String,
        onFail: Consumer<Int>,
        onSuccess: Runnable
    ) {
        // Check if the group already exists
        val localGroups = groups.value?.first ?: return
        val groupMatchingName =
            localGroups.filter { it.name.equals(newGroupName, ignoreCase = true) }
        if (groupMatchingName.isNotEmpty()) { // Existing group with the same name
            onFail.invoke(R.string.group_name_exists)
        } else if (group.isUngroupedGroup) { // "Ungrouped" group can't be renamed because it stops to work (TODO investigate that)
            onFail.invoke(R.string.group_rename_forbidden)
        } else {
            group.name = newGroupName
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        dao.insertGroup(group)
                        refreshAvailableGroupings()

                        // Update all JSONs of the books inside the renamed group so that they refer to the correct name
                        val builder = UpdateJsonData.Builder()
                        builder.setContentIds(group.contentIds.toLongArray())
                        builder.setUpdateGroups(true)
                        val workManager =
                            WorkManager.getInstance(getApplication())
                        workManager.enqueueUniqueWork(
                            R.id.udpate_json_service.toString(),
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(UpdateJsonWorker::class.java)
                                .setInputData(builder.data)
                                .build()
                        )
                        dao.cleanup()
                    }
                    onSuccess.run()
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    /**
     * Toggle the "favourite" state of the given group
     *
     * @param group Group whose favourite state to toggle
     */
    fun toggleGroupFavourite(group: Group) {
        if (group.isBeingProcessed) return
        viewModelScope.launch {
            try {
                doToggleGroupFavourite(group.id, group.name, group.subtype)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Toggle the "favourite" state of the given group
     *
     * @param groupId ID of the group whose favourite state to toggle
     * @return Resulting group
     */
    private suspend fun doToggleGroupFavourite(groupId: Long, name: String, subtype: Int) =
        withContext(Dispatchers.IO) {
            // Check if given group still exists in DB
            var theGroup = dao.selectGroup(groupId)
            if (null == theGroup && Settings.groupingDisplay == Grouping.ARTIST.id) {
                // Create flagged group
                theGroup = Group(Grouping.ARTIST, name, -1)
                theGroup.subtype = subtype
            }
            theGroup ?: throw InvalidParameterException("Invalid GroupId : $groupId")

            theGroup.favourite = !theGroup.favourite

            // Persist in it DB
            dao.insertGroup(theGroup)

            // Persist in it JSON
            updateGroupsJson(getApplication(), dao)
            dao.cleanup()
        }

    /**
     * Set the rating to the given value for the given group IDs
     *
     * @param groupIds     Groups IDs to set the rating for
     * @param targetRating Rating to set
     */
    fun rateGroups(groupIds: List<String>, targetRating: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            groupIds.forEach {
                try {
                    doRateGroup(it, targetRating)
                } catch (t: Throwable) {
                    Timber.w(t)
                } finally {
                    dao.cleanup()
                }
            }
        }
    }

    /**
     * Set the rating to the given value for the given group ID
     *
     * @param uniqueStr    Group unique String to set the rating for
     * @param targetRating Rating to set
     */
    private suspend fun doRateGroup(uniqueStr: String, targetRating: Int): Group {
        // Check if given group still exists in DB
        val parts = splitUniqueStr(uniqueStr)
        val grouping = Grouping.searchByName(parts.first)
        var theGroup = dao.selectGroupByName(grouping.id, parts.second)
        if (null == theGroup && Settings.groupingDisplay == Grouping.ARTIST.id) {
            // Create flagged group
            theGroup = Group(Grouping.ARTIST, parts.second, -1)
            theGroup.subtype = parts.third
        }
        theGroup ?: throw InvalidParameterException("Invalid uniqueStr : $uniqueStr")

        if (!theGroup.isBeingProcessed) {
            theGroup.rating = targetRating
            // Persist in it JSON
            updateGroupsJson(getApplication(), dao)

            // Persist in it DB
            dao.insertGroup(theGroup)
        }
        return theGroup
    }

    fun moveContentsToNewCustomGroup(
        contentIds: LongArray,
        newGroupName: String,
        onProcessed: Consumer<Int>
    ) {
        val newGroup = Group(Grouping.CUSTOM, newGroupName.trim(), -1)
        newGroup.id = dao.insertGroup(newGroup)
        moveContentsToCustomGroup(contentIds, newGroup, onProcessed)
        dao.cleanup()
    }

    fun moveContentsToCustomGroup(
        contentIds: LongArray,
        group: Group?,
        onProcessed: Consumer<Int>
    ) {
        var nbProcessed = 0
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentIds.forEach {
                        dao.selectContent(it)?.let { c ->
                            moveContentToCustomGroup(c, group, dao)
                            updateJson(getApplication(), c)
                            nbProcessed++
                        } ?: run {
                            Timber.w("Book couldn't be added to group")
                        }
                    }
                    refreshAvailableGroupings()
                    updateGroupsJson(getApplication(), dao)
                    dao.cleanup()
                }
                withContext(Dispatchers.Main) {
                    onProcessed.invoke(nbProcessed)
                }
            } catch (t: Throwable) {
                Timber.e(t, "Book couldn't be added to group")
            }
        }
    }

    fun shuffleContent() {
        RandomSeed.renewSeed(SEED_CONTENT)
        dao.shuffleContent()
        dao.cleanup()
    }

    fun mergeContents(
        contentList: List<Content>,
        newTitle: String,
        useBookAsChapter: Boolean,
        keepFirstBookChaps: Boolean,
        deleteAfterMerging: Boolean
    ) {
        if (contentList.isEmpty()) return
        val builder = SplitMergeData.Builder()
        builder.setOperation(SplitMergeType.MERGE)
        builder.setContentIds(contentList.map { it.id })
        builder.setNewTitle(newTitle)
        builder.setUseBooksAsChapters(useBookAsChapter)
        builder.setKeepFirstBookChaps(keepFirstBookChaps)
        builder.setDeleteAfterOps(deleteAfterMerging)
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.merge_service.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(MergeWorker::class.java)
                .setInputData(builder.data)
                .addTag(WORK_CLOSEABLE).build()
        )
    }

    fun splitContent(
        content: Content,
        chapters: List<Chapter>,
        deleteAfter: Boolean
    ) {
        val builder = SplitMergeData.Builder()
        builder.setOperation(SplitMergeType.SPLIT)
        builder.setContentIds(listOf(content.id))
        builder.setChapterIdsForSplit(chapters.map { it.id })
        builder.setDeleteAfterOps(deleteAfter)
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.split_service.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(SplitWorker::class.java)
                .setInputData(builder.data)
                .addTag(WORK_CLOSEABLE).build()
        )
    }

    fun clearSearchHistory(isGroup: Boolean) {
        dao.deleteAllSearchRecords(if (isGroup) SearchRecord.EntityType.GROUP else SearchRecord.EntityType.CONTENT)
        dao.cleanup()
    }
}
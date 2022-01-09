package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public class HinaDAO implements CollectionDAO {

    private static final CompositeDisposable disposable = new CompositeDisposable();

    // Hack to avoid introducing a Consumer<Boolean> argument into CollectionDAO
    // Should become unnecessary wil Android paging 3
    private Consumer<Integer> completionCallback;
    // TODO doc
    private Consumer<List<Content>> interceptor;


    public void setCompletionCallback(Consumer<Integer> completionCallback) {
        this.completionCallback = completionCallback;
    }

    public void setInterceptor(Consumer<List<Content>> interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public LiveData<PagedList<Content>> selectNoContent() {
        return null;
    }

    @Nullable
    @Override
    public Content selectContent(long id) {
        return null;
    }

    @Override
    public List<Content> selectContent(long[] id) {
        return null;
    }

    @Nullable
    @Override
    public Content selectContentByStorageUri(@NonNull String folderUri, boolean onlyFlagged) {
        return null;
    }

    @Nullable
    @Override
    public Content selectContentBySourceAndUrl(@NonNull Site site, @NonNull String contentUrl, @NonNull String coverUrl) {
        return null;
    }

    @Override
    public Set<String> selectAllSourceUrls(@NonNull Site site) {
        return null;
    }

    @Override
    public List<Content> searchTitlesWith(@NonNull @NotNull String word, int[] contentStatusCodes) {
        return null;
    }

    @Override
    public long insertContent(@NonNull Content content) {
        return 0;
    }

    @Override
    public void updateContentStatus(@NonNull StatusContent updateFrom, @NonNull StatusContent updateTo) {

    }

    @Override
    public void deleteContent(@NonNull Content content) {

    }

    @Override
    public List<ErrorRecord> selectErrorRecordByContentId(long contentId) {
        return null;
    }

    @Override
    public void insertErrorRecord(@NonNull ErrorRecord record) {

    }

    @Override
    public void deleteErrorRecords(long contentId) {

    }

    @Override
    public void clearDownloadParams(long contentId) {

    }

    @Override
    public void shuffleContent() {

    }

    @Override
    public long countAllInternalBooks(boolean favsOnly) {
        return 0;
    }

    @Override
    public List<Content> selectAllInternalBooks(boolean favsOnly) {
        return null;
    }

    @Override
    public void flagAllInternalBooks() {

    }

    @Override
    public void deleteAllInternalBooks(boolean resetRemainingImagesStatus) {

    }

    @Override
    public void flagAllErrorBooksWithJson() {

    }

    @Override
    public long countAllQueueBooks() {
        return 0;
    }

    @Override
    public List<Content> selectAllQueueBooks() {
        return null;
    }

    @Override
    public void deleteAllQueuedBooks() {

    }

    @Override
    public void deleteAllFlaggedBooks(boolean resetRemainingImagesStatus) {

    }

    @Override
    public long countAllExternalBooks() {
        return 0;
    }

    @Override
    public void deleteAllExternalBooks() {

    }

    @Override
    public List<Group> selectGroups(long[] groupIds) {
        return null;
    }

    @Override
    public LiveData<List<Group>> selectGroupsLive(int grouping, @androidx.annotation.Nullable String query, int orderField, boolean orderDesc, int artistGroupVisibility, boolean groupFavouritesOnly) {
        return null;
    }

    @Override
    public List<Group> selectGroups(int grouping, int subType) {
        return null;
    }

    @Override
    public List<Group> selectGroups(int grouping) {
        return null;
    }

    @Nullable
    @Override
    public Group selectGroup(long groupId) {
        return null;
    }

    @Nullable
    @Override
    public Group selectGroupByName(int grouping, @NonNull String name) {
        return null;
    }

    @Override
    public long countGroupsFor(Grouping grouping) {
        return 0;
    }

    @Override
    public long insertGroup(Group group) {
        return 0;
    }

    @Override
    public void deleteGroup(long groupId) {

    }

    @Override
    public void deleteAllGroups(Grouping grouping) {

    }

    @Override
    public void flagAllGroups(Grouping grouping) {

    }

    @Override
    public void deleteAllFlaggedGroups() {

    }

    @Override
    public long insertGroupItem(GroupItem item) {
        return 0;
    }

    @Override
    public List<GroupItem> selectGroupItems(long contentId, Grouping grouping) {
        return null;
    }

    @Override
    public void deleteGroupItems(List<Long> groupItemIds) {

    }

    @Override
    public List<Content> selectStoredContent(boolean nonFavouriteOnly, boolean includeQueued, int orderField, boolean orderDesc) {
        return null;
    }

    @Override
    public List<Long> selectStoredContentIds(boolean nonFavouritesOnly, boolean includeQueued, int orderField, boolean orderDesc) {
        return null;
    }

    @Override
    public long countStoredContent(boolean nonFavouriteOnly, boolean includeQueued) {
        return 0;
    }

    @Override
    public List<Content> selectContentWithUnhashedCovers() {
        return null;
    }

    @Override
    public long countContentWithUnhashedCovers() {
        return 0;
    }

    @Override
    public void streamStoredContent(boolean nonFavouritesOnly, boolean includeQueued, int orderField, boolean orderDesc, Consumer<Content> consumer) {

    }

    @Override
    public Single<List<Long>> selectRecentBookIds(long groupId, int orderField, boolean orderDesc, boolean bookFavouritesOnly, boolean pageFavouritesOnly, boolean bookCompletedOnly, boolean bookNotCompletedOnly) {
        return null;
    }

    @Override
    public Single<List<Long>> searchBookIds(String query, long groupId, List<Attribute> metadata, int orderField, boolean orderDesc, boolean bookFavouritesOnly, boolean pageFavouritesOnly, boolean bookCompletedOnly, boolean bookNotCompletedOnly) {
        return null;
    }

    @Override
    public Single<List<Long>> searchBookIdsUniversal(String query, long groupId, int orderField, boolean orderDesc, boolean bookFavouritesOnly, boolean pageFavouritesOnly, boolean bookCompletedOnly, boolean bookNotCompletedOnly) {
        return null;
    }

    @Override
    public LiveData<PagedList<Content>> selectRecentBooks(long groupId, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll, boolean bookCompletedOnly, boolean bookNotCompletedOnly) {
        PagedList.Config config = (new PagedList.Config.Builder())
                .setEnablePlaceholders(true)
                .setInitialLoadSizeHint(20)
                .setPageSize(10)
                .build();

        return (new LivePagedListBuilder<>(new HinaDataSource.HinaDataSourceFactory(disposable, completionCallback, interceptor), config)).build();
    }

    @Override
    public LiveData<PagedList<Content>> searchBooks(String query, long groupId, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll, boolean bookCompletedOnly, boolean bookNotCompletedOnly) {
        return null;
    }

    @Override
    public LiveData<PagedList<Content>> searchBooksUniversal(String query, long groupId, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll, boolean bookCompletedOnly, boolean bookNotCompletedOnly) {
        PagedList.Config config = (new PagedList.Config.Builder())
                .setEnablePlaceholders(true)
                .setInitialLoadSizeHint(20)
                .setPageSize(10)
                .build();

        return (new LivePagedListBuilder<>(new HinaDataSource.HinaDataSourceFactory(disposable, query, completionCallback, interceptor), config)).build();
    }

    @Override
    public LiveData<Map<String, StatusContent>> selectContentUniqueIdStates(@NonNull Site site) {
        return null;
    }

    @Override
    public LiveData<List<Content>> selectErrorContent() {
        return null;
    }

    @Override
    public List<Content> selectErrorContentList() {
        return null;
    }

    @Override
    public LiveData<Integer> countBooks(String query, long groupId, List<Attribute> metadata, boolean favouritesOnly, boolean bookCompletedOnly, boolean bookNotCompletedOnly) {
        return null;
    }

    @Override
    public LiveData<Integer> countAllBooks() {
        return null;
    }

    @Override
    public void insertImageFile(@NonNull ImageFile img) {

    }

    @Override
    public void insertImageFiles(@NonNull List<ImageFile> imgs) {

    }

    @Override
    public void replaceImageList(long contentId, @NonNull List<ImageFile> newList) {

    }

    @Override
    public void updateImageContentStatus(long contentId, StatusContent updateFrom, @NonNull StatusContent updateTo) {

    }

    @Override
    public void updateImageFileStatusParamsMimeTypeUriSize(@NonNull ImageFile image) {

    }

    @Override
    public void deleteImageFiles(@NonNull List<ImageFile> imgs) {

    }

    @Override
    public ImageFile selectImageFile(long id) {
        return null;
    }

    @Override
    public LiveData<List<ImageFile>> selectDownloadedImagesFromContentLive(long id) {
        return null;
    }

    @Override
    public List<ImageFile> selectDownloadedImagesFromContent(long id) {
        return null;
    }

    @Override
    public Map<StatusContent, ImmutablePair<Integer, Long>> countProcessedImagesById(long contentId) {
        return null;
    }

    @Override
    public Map<Site, ImmutablePair<Integer, Long>> selectPrimaryMemoryUsagePerSource() {
        return null;
    }

    @Override
    public Map<Site, ImmutablePair<Integer, Long>> selectExternalMemoryUsagePerSource() {
        return null;
    }

    @Override
    public List<QueueRecord> selectQueue() {
        return null;
    }

    @Override
    public List<QueueRecord> selectQueue(String query) {
        return null;
    }

    @Override
    public LiveData<List<QueueRecord>> selectQueueLive() {
        return null;
    }

    @Override
    public LiveData<List<QueueRecord>> selectQueueLive(String query) {
        return null;
    }

    @Override
    public void addContentToQueue(@NonNull Content content, StatusContent targetImageStatus, int mode, boolean isQueueActive) {

    }

    @Override
    public void insertQueue(long contentId, int order) {

    }

    @Override
    public void updateQueue(@NonNull List<QueueRecord> queue) {

    }

    @Override
    public void deleteQueue(@NonNull Content content) {

    }

    @Override
    public void deleteQueue(int index) {

    }

    @Override
    public Single<AttributeQueryResult> selectAttributeMasterDataPaged(@NonNull @NotNull List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, boolean bookCompletedOnly, boolean bookNotCompletedOnly, int page, int booksPerPage, int orderStyle) {
        return null;
    }

    @Override
    public Single<SparseIntArray> countAttributesPerType(List<Attribute> filter) {
        return null;
    }

    @Override
    public List<Chapter> selectChapters(long contentId) {
        return null;
    }

    @Override
    public void insertChapters(@NonNull List<Chapter> chapters) {

    }

    @Override
    public void deleteChapters(@NonNull Content content) {

    }

    @Override
    public void deleteChapter(@NonNull Chapter chapter) {

    }

    @Override
    public SiteHistory selectHistory(@NonNull Site s) {
        return null;
    }

    @Override
    public void insertSiteHistory(@NonNull Site site, @NonNull String url) {

    }

    @Override
    public long countAllBookmarks() {
        return 0;
    }

    @Override
    public List<SiteBookmark> selectAllBookmarks() {
        return null;
    }

    @Override
    public List<SiteBookmark> selectBookmarks(@NonNull Site s) {
        return null;
    }

    @androidx.annotation.Nullable
    @Override
    public SiteBookmark selectHomepage(@NonNull Site s) {
        return null;
    }

    @Override
    public long insertBookmark(@NonNull SiteBookmark bookmark) {
        return 0;
    }

    @Override
    public void insertBookmarks(@NonNull List<SiteBookmark> bookmarks) {

    }

    @Override
    public void deleteBookmark(long bookmarkId) {

    }

    @Override
    public void deleteAllBookmarks() {

    }

    @Override
    public void cleanup() {
        disposable.clear();
        completionCallback = null;
    }

    @Override
    public long getDbSizeBytes() {
        return 0;
    }

    @Override
    public Single<List<Long>> selectOldStoredBookIds() {
        return null;
    }

    @Override
    public long countOldStoredContent() {
        return 0;
    }
}

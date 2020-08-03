package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public class HinaDAO implements CollectionDAO {

    private static final CompositeDisposable disposable = new CompositeDisposable();

    @Nullable
    @Override
    public Content selectContent(long id) {
        return null;
    }

    @Nullable
    @Override
    public Content selectContentByFolderUri(@NonNull String folderUri, boolean onlyFlagged) {
        return null;
    }

    @Nullable
    @Override
    public Content selectContentBySourceAndUrl(@NonNull Site site, @NonNull String url) {
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
    public Single<List<Long>> getStoredBookIds(boolean nonFavouriteOnly, boolean includeQueued) {
        return null;
    }

    @Override
    public Single<List<Long>> getRecentBookIds(int orderField, boolean orderDesc, boolean favouritesOnly) {
        return null;
    }

    @Override
    public Single<List<Long>> searchBookIds(String query, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly) {
        return null;
    }

    @Override
    public Single<List<Long>> searchBookIdsUniversal(String query, int orderField, boolean orderDesc, boolean favouritesOnly) {
        return null;
    }

    @Override
    public LiveData<PagedList<Content>> searchBooksUniversal(String query, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll) {
        PagedList.Config config = (new PagedList.Config.Builder())
                .setEnablePlaceholders(true)
                .setInitialLoadSizeHint(20)
                .setPageSize(10)
                .build();

        return (new LivePagedListBuilder<>(new HinaDataSource.HinaDataSource2Factory(disposable, query), config)).build();
    }

    @Override
    public LiveData<PagedList<Content>> searchBooks(String query, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll) {
        return null;
    }

    @Override
    public LiveData<PagedList<Content>> getRecentBooks(int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll) {
        PagedList.Config config = (new PagedList.Config.Builder())
                .setEnablePlaceholders(true)
                .setInitialLoadSizeHint(20)
                .setPageSize(10)
                .build();

        return (new LivePagedListBuilder<>(new HinaDataSource.HinaDataSource2Factory(disposable), config)).build();
    }

    @Override
    public LiveData<Map<String, StatusContent>> selectContentUniqueIdStates(@NonNull Site site) {
        return null;
    }

    @Override
    public LiveData<List<Content>> getErrorContent() {
        return null;
    }

    @Override
    public LiveData<Integer> countBooks(String query, List<Attribute> metadata, boolean favouritesOnly) {
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
    public LiveData<List<ImageFile>> getDownloadedImagesFromContent(long id) {
        return null;
    }

    @Override
    public Map<StatusContent, ImmutablePair<Integer, Long>> countProcessedImagesById(long contentId) {
        return null;
    }

    @Override
    public Map<Site, ImmutablePair<Integer, Long>> getMemoryUsagePerSource() {
        return null;
    }

    @Override
    public List<QueueRecord> selectQueue() {
        return null;
    }

    @Override
    public LiveData<List<QueueRecord>> getQueueContent() {
        return null;
    }

    @Override
    public void addContentToQueue(@NonNull Content content, StatusContent targetImageStatus) {

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
    public Single<AttributeQueryResult> getAttributeMasterDataPaged(@NonNull List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle) {
        return null;
    }

    @Override
    public Single<SparseIntArray> countAttributesPerType(List<Attribute> filter) {
        return null;
    }

    @Override
    public SiteHistory getHistory(@NonNull Site s) {
        return null;
    }

    @Override
    public void insertSiteHistory(@NonNull Site site, @NonNull String url) {

    }

    @Override
    public void cleanup() {
        disposable.clear();
    }

    @Override
    public Single<List<Long>> getOldStoredBookIds() {
        return null;
    }

    @Override
    public long countOldStoredContent() {
        return 0;
    }
}

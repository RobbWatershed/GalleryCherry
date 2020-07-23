package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagedList;

import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.widget.ContentSearchManager;


public class HinaViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO dao;
    // Library search manager
    private final ContentSearchManager searchManager;
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data
    private LiveData<PagedList<Content>> currentSource;
    private LiveData<Integer> totalContent;
    private final MediatorLiveData<PagedList<Content>> libraryPaged = new MediatorLiveData<>();

    // Updated whenever a new search is performed
    private MutableLiveData<Boolean> newSearch = new MutableLiveData<>();


    public HinaViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        dao = collectionDAO;
        searchManager = new ContentSearchManager(dao);
        totalContent = dao.countAllBooks();
    }

    public void onSaveState(Bundle outState) {
        searchManager.saveToBundle(outState);
    }

    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState == null) return;
        searchManager.loadFromBundle(savedState);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeDisposable.clear();
        dao.cleanup();
    }

    @NonNull
    public LiveData<PagedList<Content>> getLibraryPaged() {
        return libraryPaged;
    }

    @NonNull
    public LiveData<Integer> getTotalContent() {
        return totalContent;
    }

    @NonNull
    public LiveData<Boolean> getNewSearch() {
        return newSearch;
    }

    // =========================
    // ========= LIBRARY ACTIONS
    // =========================

    /**
     * Perform a new library search
     */
    private void performSearch() {
        if (currentSource != null) libraryPaged.removeSource(currentSource);

        searchManager.setContentSortField(Preferences.getContentSortField());
        searchManager.setContentSortDesc(Preferences.isContentSortDesc());

        currentSource = searchManager.getLibrary();

        libraryPaged.addSource(currentSource, libraryPaged::postValue);
    }

    /**
     * Perform a new universal search using the given query
     *
     * @param query Query to use for the universal search
     */
    public void searchUniversal(@NonNull String query) {
        searchManager.clearSelectedSearchTags(); // If user searches in main toolbar, universal search takes over advanced search
        searchManager.setQuery(query);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Perform a new search using the given query and metadata
     *
     * @param query    Query to use for the search
     * @param metadata Metadata to use for the search
     */
    public void search(@NonNull String query, @NonNull List<Attribute> metadata) {
        searchManager.setQuery(query);
        searchManager.setTags(metadata);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Set the mode (endless or paged)
     */
    public void setPagingMethod(boolean isEndless) {
        searchManager.setLoadAll(!isEndless);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Update the order of the list
     */
    public void updateOrder() {
        newSearch.setValue(true);
        performSearch();
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================

    /**
     * Add the given content to the download queue
     *
     * @param content Content to be added to the download queue
     */
    public void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus) {
        dao.addContentToQueue(content, targetImageStatus);
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.HinaDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.widget.ContentSearchManager;


public class HinaViewModel extends AndroidViewModel {

    // DAOs
    private final CollectionDAO hinaDao;
    private final CollectionDAO hentoidDao;
    // Library search manager
    private final ContentSearchManager searchManager;
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data
    private LiveData<PagedList<Content>> currentSource;
    private final LiveData<Map<String, StatusContent>> hinaBooksStatus;
    private final MediatorLiveData<PagedList<Content>> libraryPaged = new MediatorLiveData<>();

    // Updated whenever a new search is performed
    private final MutableLiveData<Boolean> newSearch = new MutableLiveData<>();
    private final MutableLiveData<Integer> hinaCallStatus = new MutableLiveData<>();


    public HinaViewModel(@NonNull Application application, @NonNull CollectionDAO hinaDAO, @NonNull CollectionDAO hentoidDAO) {
        super(application);
        hinaDao = hinaDAO;
        // Hack to avoid introducing a Consumer<Boolean> argument into CollectionDAO
        // Should become unnecessary with Android paging 3
        ((HinaDAO) hinaDAO).setCompletionCallback(hinaCallStatus::postValue);
        // TODO doc
        ((HinaDAO) hinaDAO).setInterceptor(this::embedLibraryStatus);
        hentoidDao = hentoidDAO;
        searchManager = new ContentSearchManager(hinaDao);
        hinaBooksStatus = hentoidDAO.selectContentUniqueIdStates(Site.HINA);
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
        hinaDao.cleanup();
        hentoidDao.cleanup();
    }

    @NonNull
    public LiveData<PagedList<Content>> getLibraryPaged() {
        return libraryPaged;
    }

    @NonNull
    public LiveData<Map<String, StatusContent>> getHinaBooksStatus() {
        return hinaBooksStatus;
    }

    @NonNull
    public LiveData<Boolean> getNewSearch() {
        return newSearch;
    }

    @NonNull
    public LiveData<Integer> getHinaCallStatus() {
        return hinaCallStatus;
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
        // Clean the search string
        query = query.trim().replace("   ", " ").replace("  ", " ");
        if (query.length() > 0) query = manageQuotes(query);

        searchManager.setQuery(query);
        newSearch.setValue(true);
        performSearch();
    }

    // Auto-add "'s around words that are not quoted to make a "cumulative AND" query
    private String manageQuotes(@NonNull final String query) {
        String result = query;

        // 1- Identify quotes
        List<Integer> quoteIndexes = new ArrayList<>();
        int quoteIndex = result.indexOf('"');
        while (quoteIndex > -1) {
            quoteIndexes.add(quoteIndex);
            quoteIndex = result.indexOf('"', quoteIndex + 1);
        }

        // 2- Build the definitive query
        int spaceIndex = result.indexOf(' ');
        int offset = 0;
        while (spaceIndex > -1) {
            if (!isInRange(spaceIndex, quoteIndexes, offset)) {
                boolean isPreviousQuote = ('"' == result.charAt(spaceIndex - 1));
                boolean isNextQuote = ('"' == result.charAt(spaceIndex + 1));
                String toInsert = (isPreviousQuote ? "" : "\"") + " " + (isNextQuote ? "" : "\"");
                result = result.substring(0, spaceIndex) + toInsert + result.substring(spaceIndex + 1);
                spaceIndex += 2;
                offset += toInsert.length() - 1;
            }
            spaceIndex = result.indexOf(' ', spaceIndex + 1);
        }

        if (!result.startsWith("\"")) result = "\"" + result;
        if (!result.endsWith("\"")) result = result + "\"";

        return result;
    }

    private boolean isInRange(int index, List<Integer> ranges, int offset) {
        if (ranges.size() < 2) return false;

        for (int i = 0; i < ranges.size() / 2; i += 2)
            if (index >= ranges.get(i) + offset && index < ranges.get(i + 1) + offset) return true;

        return false;
    }

    /**
     * Set the mode (endless or paged)
     */
    public void setPagingMethod(boolean isEndless) {
        searchManager.setLoadAll(!isEndless);
        newSearch.setValue(true);
        performSearch();
    }

    private void embedLibraryStatus(@NonNull List<Content> contents) {
        if (null == hinaBooksStatus || null == hinaBooksStatus.getValue()) return;
        Map<String, StatusContent> booksStatus = hinaBooksStatus.getValue();
        for (Content c : contents) {
            if (booksStatus.containsKey(c.getUniqueSiteId())) {
                StatusContent s = booksStatus.get(c.getUniqueSiteId());
                if (s != null) c.setStatus(s);
            }
        }
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
        hentoidDao.addContentToQueue(content, targetImageStatus);
    }
}

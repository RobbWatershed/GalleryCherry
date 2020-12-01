package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.extensions.ExtensionsFactories;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.paged.PagedModelAdapter;
import com.mikepenz.fastadapter.select.SelectExtensionFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ContentItemBundle;
import me.devsaki.hentoid.database.HinaDataSource;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.fragments.library.GalleryDialogFragment;
import me.devsaki.hentoid.json.UpdateInfo;
import me.devsaki.hentoid.retrofit.HinaServer;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewmodels.HinaViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

/**
 * Handles hosting of QueueFragment for a single screen.
 */
public class HinaActivity extends BaseActivity implements GalleryDialogFragment.Parent {

    private static final String KEY_LAST_LIST_POSITION = "last_list_position";

    // ======== COMMUNICATION
    // Viewmodel
    private HinaViewModel viewModel;


    // ======== UI
    // Text that displays in the background when the list is empty
    private TextView loadingText;
    // Action view associated with search menu button
    private SearchView mainSearchView;
    // Main view where books are displayed
    private RecyclerView recyclerView;
    // LayoutManager of the recyclerView
    private LinearLayoutManager llm;

    // ==== Advanced search / sort bar
    // CLEAR button
    private TextView searchClearButton;

    // === TOOLBAR
    private Toolbar toolbar;
    // "Search" button on top menu
    private MenuItem searchMenu;

    // Alert message panel and text
    private View alertBanner;
    private ImageView alertIcon;
    private TextView alertMessage;

    // === FASTADAPTER COMPONENTS AND HELPERS
    private PagedModelAdapter<Content, ContentItem> pagedItemAdapter;
    private FastAdapter<ContentItem> fastAdapter;


    // ======== VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // Total number of books in the whole unfiltered collection
    private int totalContentCount = -1;
    // True when a new search has been performed and its results have not been handled yet
    // False when the refresh is passive (i.e. not from a direct user action)
    private boolean newSearch = false;
    // Collection of books according to current filters
    private PagedList<Content> library;
    // Position of top item to memorize or restore (used when activity is destroyed and recreated)
    private int topItemPosition = -1;

    // Alert to be displayed
    private UpdateInfo.SourceAlert alert;

    // Used to start processing when the recyclerView has finished updating
    private Debouncer<Integer> listRefreshDebouncer;

    // TODO comment
    private Map<String, StatusContent> booksStatus;


    // === SEARCH PARAMETERS
    // Current text search query
    private String query = "";
    // Current metadata search query
    private List<Attribute> metadata = Collections.emptyList();

    private Disposable downloadDisposable;


    /**
     * Diff calculation rules for list items
     * <p>
     * Created once and for all to be used by FastAdapter in endless mode (=using Android PagedList)
     */
    private final AsyncDifferConfig<Content> asyncDifferConfig = new AsyncDifferConfig.Builder<>(new DiffUtil.ItemCallback<Content>() {
        @Override
        public boolean areItemsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.getUniqueSiteId().equals(newItem.getUniqueSiteId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.getUrl().equalsIgnoreCase(newItem.getUrl())
                    && oldItem.getSite().equals(newItem.getSite());
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Content oldItem, @NonNull Content newItem) {
            ContentItemBundle.Builder diffBundleBuilder = new ContentItemBundle.Builder();

            if (oldItem.isFavourite() != newItem.isFavourite()) {
                diffBundleBuilder.setIsFavourite(newItem.isFavourite());
            }
            if (oldItem.getReads() != newItem.getReads()) {
                diffBundleBuilder.setReads(newItem.getReads());
            }

            if (diffBundleBuilder.isEmpty()) return null;
            else return diffBundleBuilder.getBundle();
        }

    }).build();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ExtensionsFactories.INSTANCE.register(new SelectExtensionFactory());
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        setContentView(R.layout.activity_hina);

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(HinaViewModel.class);

        initUI();

        initToolbar();
        toolbar.setOnMenuItemClickListener(this::toolbarOnItemClicked);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        viewModel.getHinaCallStatus().observe(this, this::onHinaCallStatus);
        viewModel.getNewSearch().observe(this, this::onNewSearch);
        viewModel.getLibraryPaged().observe(this, this::onLibraryChanged);
        viewModel.getHinaBooksStatus().observe(this, this::onHinaBooksStatusChanged);

        // Alert banner
        alertBanner = findViewById(R.id.web_alert_group);
        alertIcon = findViewById(R.id.web_alert_icon);
        alertMessage = findViewById(R.id.web_alert_txt);
        displayAlertBanner();

        listRefreshDebouncer = new Debouncer<>(this, 75, this::onRecyclerUpdated);
    }

    /**
     * Initialize the UI components
     */
    private void initUI() {
        loadingText = findViewById(R.id.hina_loading_txt);

        // Clear search
        searchClearButton = findViewById(R.id.search_clear_btn);
        searchClearButton.setOnClickListener(v -> {
            query = "";
            mainSearchView.setQuery("", false);
            metadata.clear();
            viewModel.searchUniversal("");
        });

        // RecyclerView
        recyclerView = findViewById(R.id.library_list);
        llm = (LinearLayoutManager) recyclerView.getLayoutManager();
        new FastScrollerBuilder(recyclerView).build();

        initPagingMethod();
    }

    private void initToolbar() {
        toolbar = findViewById(R.id.library_toolbar);

        searchMenu = toolbar.getMenu().findItem(R.id.action_search);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                invalidateNextQueryTextChange = true;

                // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                if (!query.isEmpty())
                    // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                    new Handler().postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        mainSearchView.setQuery(query, false);
                    }, 100);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                invalidateNextQueryTextChange = true;
                return true;
            }
        });

        mainSearchView = (SearchView) searchMenu.getActionView();
        mainSearchView.setIconifiedByDefault(true);
        mainSearchView.setQueryHint(getString(R.string.search_hint));
        // Change display when text query is typed
        mainSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                showLoadingMessage();
                query = s;
                viewModel.searchUniversal(query);
                mainSearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    showLoadingMessage();
                    query = "";
                    viewModel.searchUniversal(query);
                    searchClearButton.setVisibility(View.GONE);
                }

                return true;
            }
        });
    }

    private void showLoadingMessage() {
        loadingText.setText(R.string.hina_loading);
        loadingText.startAnimation(new BlinkAnimation(750, 20));
        loadingText.setVisibility(View.VISIBLE);
    }

    /**
     * Displays the top alert banner
     * (the one that contains the alerts when downloads are broken or sites are unavailable)
     */
    private void displayAlertBanner() {
        if (alertMessage != null && alert != null) {
            alertIcon.setImageResource(alert.getStatus().getIcon());
            alertMessage.setText(formatAlertMessage(alert));
            alertBanner.setVisibility(View.VISIBLE);
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateEvent(UpdateEvent event) {
        if (event.sourceAlerts.containsKey(Site.HINA)) {
            alert = event.sourceAlerts.get(Site.HINA);
            displayAlertBanner();
        }
    }

    /**
     * Handler for the close icon of the top alert banner
     */
    public void onAlertCloseClick(View view) {
        alertBanner.setVisibility(View.GONE);
    }

    /**
     * Format the message to display for the given source alert
     *
     * @param alert Source alert
     * @return Message to be displayed for the user for the given source alert
     */
    private String formatAlertMessage(@NonNull final UpdateInfo.SourceAlert alert) {
        String result = "";

        // Main message body
        if (alert.getStatus().equals(AlertStatus.ORANGE)) {
            result = getResources().getString(R.string.alert_orange);
        } else if (alert.getStatus().equals(AlertStatus.RED)) {
            result = getResources().getString(R.string.alert_red);
        } else if (alert.getStatus().equals(AlertStatus.GREY)) {
            result = getResources().getString(R.string.alert_grey);
        } else if (alert.getStatus().equals(AlertStatus.BLACK)) {
            result = getResources().getString(R.string.alert_black);
        }

        // End of message
        if (alert.getFixedByBuild() < Integer.MAX_VALUE)
            result = result.replace("%s", getResources().getString(R.string.alert_fix_available));
        else result = result.replace("%s", getResources().getString(R.string.alert_wip));

        return result;
    }

    /**
     * Callback method used when a sort method is selected in the sort drop-down menu
     * Updates the UI according to the chosen sort method
     *
     * @param menuItem Toolbar of the fragment
     */
    private boolean toolbarOnItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_order:
                ///aaa
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    private boolean isSearchQueryActive() {
        return (!query.isEmpty() || !metadata.isEmpty());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewModel != null) viewModel.onSaveState(outState);
        if (fastAdapter != null) fastAdapter.saveInstanceState(outState);

        // Remember current position in the sorted list
        int currentPosition = getTopItemPosition();
        if (currentPosition > 0 || -1 == topItemPosition) topItemPosition = currentPosition;

        outState.putInt(KEY_LAST_LIST_POSITION, topItemPosition);
        topItemPosition = -1;
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        topItemPosition = 0;

        if (viewModel != null) viewModel.onRestoreState(savedInstanceState);
        if (fastAdapter != null) fastAdapter.withSavedInstanceState(savedInstanceState);
        // Mark last position in the list to be the one it will come back to
        topItemPosition = savedInstanceState.getInt(KEY_LAST_LIST_POSITION, 0);
    }

    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    /**
     * Initialize the paging method of the screen
     */
    private void initPagingMethod() {
        showLoadingMessage();
        viewModel.setPagingMethod(true); // Runs a new search

        pagedItemAdapter = new PagedModelAdapter<>(asyncDifferConfig, i -> new ContentItem(ContentItem.ViewType.ONLINE), c -> new ContentItem(c, null, ContentItem.ViewType.ONLINE, null));
        fastAdapter = FastAdapter.with(pagedItemAdapter);
        fastAdapter.setHasStableIds(true);
        ContentItem item = new ContentItem(ContentItem.ViewType.ONLINE);
        fastAdapter.registerItemFactory(item.getType(), item);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onBookClick(i));

        // Download button click listener
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                if (item.getContent() != null)
                    downloadDisposable = HinaServer.API.getGallery(
                            item.getContent().getUniqueSiteId(),
                            BuildConfig.RAPIDAPI_KEY,
                            HinaServer.HINA_RAPIDAPI_HOST)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    g -> downloadContent(g.toContent()),
                                    Timber::e);
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getDownloadButton();
                }
                return super.onBind(viewHolder);
            }
        });

        recyclerView.setAdapter(fastAdapter);
    }

    /**
     * LiveData callback when a new search takes place
     *
     * @param b Unused parameter (always set to true)
     */
    private void onNewSearch(Boolean b) {
        newSearch = b;
    }

    /**
     * LiveData callback when the library changes
     * - Either because a new search has been performed
     * - Or because a book has been downloaded, deleted, updated
     *
     * @param result Current library according to active filters
     */
    private void onLibraryChanged(PagedList<Content> result) {
        Timber.i(">>Library changed ! Size=%s", result.size());

        // TODO message when loading failed

        // Update visibility of advanced search bar
        if (isSearchQueryActive()) {
            if (!result.isEmpty() && searchMenu != null) searchMenu.collapseActionView();
        } else {
            searchClearButton.setVisibility(View.GONE);
        }

        // If the update is the result of a new search, get back on top of the list
        if (newSearch) topItemPosition = 0;

        result.addWeakCallback(null, new PagedList.Callback() {
            @Override
            // Called when progressing through the list
            // Items are being load to replace placeholders at the given location
            public void onChanged(int position, int count) {
                updateContentStatus(position, count);
            }

            @Override
            // Called once when loading the results for the 1st time
            // Contains the whole list with the first X elements and placeholders
            public void onInserted(int position, int count) {
                if (-1 == totalContentCount) totalContentCount = count;
                updateTitle(count, totalContentCount);
                updateContentStatus(position, count);
            }

            @Override
            public void onRemoved(int position, int count) {
                Timber.i(">> library removed %s %s", position, count);
            }
        });

        // Update displayed books
        pagedItemAdapter.submitList(result, this::differEndCallback);

        newSearch = false;
        library = result;
    }

    private void onHinaCallStatus(Integer status) {
        loadingText.clearAnimation();
        switch (status) {
            case HinaDataSource.Status.SUCCESS:
                loadingText.setVisibility(View.GONE);
                break;
            case HinaDataSource.Status.SUCCESS_EMPTY:
                loadingText.setText(R.string.hina_no_results);
                loadingText.setVisibility(View.VISIBLE);
                break;
            case HinaDataSource.Status.ERROR:
                loadingText.setText(R.string.hina_loading_failed);
                loadingText.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateContentStatus(int position, int count) {
        List<ContentItem> contents = pagedItemAdapter.getAdapterItems();
        int listPosition = 0;
        for (ContentItem c : contents) {
            if (c.getContent() != null && listPosition >= position && listPosition < position + count) {
                if (booksStatus.containsKey(c.getContent().getUniqueSiteId())) {
                    StatusContent s = booksStatus.get(c.getContent().getUniqueSiteId());
                    if (s != null) {
                        final int updatePosition = listPosition;
                        new Handler().postDelayed(() -> {
                            Bundle payload = new ContentItemBundle.Builder().setStatus(s).getBundle();
                            fastAdapter.notifyAdapterItemChanged(updatePosition, payload);
                        }, 150);
                    }
                }
            }
            listPosition++;
        }
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    private void updateTitle(long totalSelectedCount, long totalCount) {
        String title;
        if (totalSelectedCount == totalCount)
            title = totalCount + " items";
        else {
            title = getResources().getQuantityString(R.plurals.number_of_book_search_results, (int) totalSelectedCount, (int) totalSelectedCount, totalCount);
        }
        toolbar.setTitle(title);
    }

    private void onHinaBooksStatusChanged(Map<String, StatusContent> booksStatus) {
        if (this.booksStatus != null) {
            Map<String, StatusContent> changes = new HashMap<>();

            // 1st pass : detect added elements
            List<String> addedBooksIds = new ArrayList<>(booksStatus.keySet());
            addedBooksIds.removeAll(this.booksStatus.keySet());
            for (String id : addedBooksIds)
                changes.put(id, booksStatus.get(id));

            // 2nd pass : detect updated elements
            for (Map.Entry<String, StatusContent> entry : this.booksStatus.entrySet())
                if (booksStatus.containsKey(entry.getKey())) {
                    StatusContent newValue = booksStatus.get(entry.getKey());
                    if (newValue != null && entry.getValue() != null && !newValue.equals(entry.getValue()))
                        changes.put(entry.getKey(), newValue);
                }

            for (Map.Entry<String, StatusContent> changedEntry : changes.entrySet()) {
                Bundle payload = new ContentItemBundle.Builder().setStatus(changedEntry.getValue()).getBundle();
                fastAdapter.notifyAdapterItemChanged(fastAdapter.getPosition(Content.hash(0L, changedEntry.getKey())), payload);
            }
        }

        this.booksStatus = booksStatus;
    }

    /**
     * Callback for the book holder itself
     *
     * @param item ContentItem that has been clicked on
     */
    private boolean onBookClick(@NonNull ContentItem item) {
        GalleryDialogFragment.invoke(this, item.getContent().getUniqueSiteId());

        return false;
    }

    @Override
    public void downloadContent(Content content) {
        if (downloadDisposable != null) {
            downloadDisposable.dispose();
            downloadDisposable = null;
        }
        viewModel.addContentToQueue(content, null);

        if (Preferences.isQueueAutostart())
            ContentQueueManager.getInstance().resumeQueue(this);

        String message = getResources().getQuantityString(R.plurals.add_to_queue, 1, 1);
        Snackbar snackbar = Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG);
        snackbar.setAction("VIEW QUEUE", v -> viewQueue());
        snackbar.show();
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private void differEndCallback() {
        if (topItemPosition > -1) {
            int targetPos = topItemPosition;
            listRefreshDebouncer.submit(targetPos);
            topItemPosition = -1;
        }
    }

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
    private void onRecyclerUpdated(int topItemPosition) {
        int currentPosition = getTopItemPosition();
        if (currentPosition != topItemPosition)
            llm.scrollToPositionWithOffset(topItemPosition, 0); // Used to restore position after activity has been stopped and recreated
    }

    /**
     * Calculate the position of the top visible item of the book list
     *
     * @return position of the top visible item of the book list
     */
    private int getTopItemPosition() {
        return Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition());
    }

    /**
     * Navigate to the queue screen
     */
    private void viewQueue() {
        Intent intent = new Intent(this, QueueActivity.class);
        startActivity(intent);
    }
}

package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.extensions.ExtensionsFactories.register
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.fastadapter.select.SelectExtension
import com.mikepenz.fastadapter.select.SelectExtensionFactory
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.FragmentLibraryLrrBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.retrofit.sources.LrrServer
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.contentItemDiffCallback
import me.devsaki.hentoid.util.dpToPx
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.viewholders.ContentItem
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.AutofitGridLayoutManager
import me.devsaki.hentoid.widget.DragSelectTouchListener
import me.devsaki.hentoid.widget.DragSelectionProcessor
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper
import me.devsaki.hentoid.widget.ScrollPositionListener
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.Locale

class LibraryLrrFragment : Fragment(),
    PopupTextProvider,
    ItemTouchCallback,
    SimpleSwipeCallback.ItemSwipeCallback {

    // ======== COMMUNICATION
    private var callback: OnBackPressedCallback? = null

    // Viewmodel
    private lateinit var viewModel: LibraryViewModel

    // Activity
    private lateinit var activity: WeakReference<LibraryActivity>


    // ======== UI
    private var binding: FragmentLibraryLrrBinding? = null

    // LayoutManager of the recyclerView
    private var llm: LinearLayoutManager? = null

    // === FASTADAPTER COMPONENTS AND HELPERS
    private var itemAdapter: ItemAdapter<ContentItem> = ItemAdapter()
    private var fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension: SelectExtension<ContentItem>? = null
    private var touchHelper: ItemTouchHelper? = null
    private var mDragSelectTouchListener: DragSelectTouchListener? = null


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private var backButtonPressed: Long = 0

    private lateinit var pagingDebouncer: Debouncer<Unit>

    // Search and filtering criteria in the form of a Bundle (see LrrSearchManager.LrrSearchBundle)
    private var lrrSearchBundle: Bundle? = null


    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is LibraryActivity) { "Parent activity has to be a LibraryActivity" }
        activity = WeakReference(requireActivity() as LibraryActivity)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        pagingDebouncer = Debouncer(lifecycleScope, 100) { setPagingMethod() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        register(SelectExtensionFactory())
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        callback?.remove()
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLibraryLrrBinding.inflate(inflater, container, false)
        initUI()
        activity.get()?.initFragmentToolbars(
            selectExtension!!,
            { onToolbarItemClicked(it) }
        ) { onSelectionToolbarItemClicked(it) }
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.lrrArchives.observe(viewLifecycleOwner) { onArchivesChanged(it) }
        viewModel.lrrSearchBundle.observe(viewLifecycleOwner) { lrrSearchBundle = it }

        // Trigger a blank search
        // TODO only do that when the view is activated?
        viewModel.searchLrr()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.onSaveState(outState)
        fastAdapter.saveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (null == savedInstanceState) return
        viewModel.onRestoreState(savedInstanceState)
        fastAdapter.withSavedInstanceState(savedInstanceState)
    }

    private fun onEnable() {
        callback?.isEnabled = true
    }

    private fun onDisable() {
        callback?.isEnabled = false
    }

    /**
     * Initialize the UI components
     */
    private fun initUI() {
        // RecyclerView
        llm =
            if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay) LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.VERTICAL,
                false
            ) else AutofitGridLayoutManager(
                requireContext(),
                dpToPx(requireContext(), Settings.libraryGridCardWidthDP)
            )

        binding?.apply {
            recyclerView.layoutManager = llm
            FastScrollerBuilder(recyclerView)
                .setPopupTextProvider(this@LibraryLrrFragment)
                .useMd2Style()
                .build()

            swipeContainer.isEnabled = false // Shuffle mode isn't available for LRR

            val scrollListener = ScrollPositionListener(lifecycleScope) { _ -> /* Nothing */ }
            scrollListener.setOnEndOutOfBoundScrollListener { viewModel.loadMoreLrr() }
            recyclerView.addOnScrollListener(scrollListener)
        }

        // Pager
        setPagingMethod()
        addCustomBackControl()
    }

    private fun addCustomBackControl() {
        callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                customBackPress()
            }
        }
        activity.get()!!.onBackPressedDispatcher.addCallback(activity.get()!!, callback!!)
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            // TODO
            //R.id.action_edit -> enterEditMode()
            else -> return activity.get()!!.toolbarOnItemClicked(menuItem)
        }
        return true
    }

    private fun onSelectionToolbarItemClicked(menuItem: MenuItem): Boolean {
        var keepToolbar = false
        when (menuItem.itemId) {
            R.id.action_select_all -> {
                // Make certain _everything_ is properly selected (selectExtension.select() as doesn't get everything the 1st time it's called)
                var count = 0
                selectExtension?.apply {
                    while (selections.size < itemAdapter.adapterItemCount && ++count < 5)
                        IntRange(0, itemAdapter.adapterItemCount - 1).forEach {
                            select(it, false, considerSelectableFlag = true)
                        }
                }
                keepToolbar = true
            }

            else -> {
                activity.get()!!.getSelectionToolbar()?.visibility = View.GONE
                return false
            }
        }
        if (!keepToolbar) activity.get()!!.getSelectionToolbar()?.visibility = View.GONE
        return true
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return
        if (ProcessEvent.Type.COMPLETE != event.eventType) return
        viewModel.refreshAvailableGroupings()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.LIBRARY_LRR && event.recipient != CommunicationEvent.Recipient.ALL) return
        when (event.type) {
            CommunicationEvent.Type.UPDATE_TOOLBAR -> {
                addCustomBackControl()
                selectExtension?.let { se ->
                    activity.get()?.initFragmentToolbars(se, { onToolbarItemClicked(it) })
                    { onSelectionToolbarItemClicked(it) }
                }
            }

            CommunicationEvent.Type.SEARCH, CommunicationEvent.Type.SEARCH_NO_HISTORY ->
                onSubmitSearch(event.message)

            CommunicationEvent.Type.ENABLE -> onEnable()
            CommunicationEvent.Type.DISABLE -> onDisable()
            CommunicationEvent.Type.SCROLL_TOP -> llm?.scrollToPositionWithOffset(0, 0)
            else -> {}
        }
    }

    private fun customBackPress() {
        // If content is selected, deselect it
        if (selectExtension!!.selections.isNotEmpty()) {
            leaveSelectionMode()
            backButtonPressed = 0
            return
        }
        activity.get()?.apply {
            if (!collapseSearchMenu() && !closeLeftDrawer()) {
                // If none of the above and a search filter is on => clear search filter
                if (isFilterActive()) {
                    viewModel.clearLrrFilters()
                } else if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
                    callback?.remove()
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    backButtonPressed = SystemClock.elapsedRealtime()
                    toast(R.string.press_back_again)
                    llm!!.scrollToPositionWithOffset(0, 0)
                }
            }
        }
    }

    /**
     * Initialize the paging method of the screen
     */
    private fun setPagingMethod(recreate: Boolean = false) {
        // Rebuild to be certain all layouts are recreated from scratch when switching to and from edit mode
        if (recreate) fastAdapter = FastAdapter.with(itemAdapter)
        if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension?.apply {
            multiSelect = true
            selectOnLongClick = true
            selectWithItemUpdate = true
            selectionListener =
                object : ISelectionListener<ContentItem> {
                    override fun onSelectionChanged(item: ContentItem, selected: Boolean) {
                        onSelectionChanged()
                    }
                }
            val helper = FastAdapterPreClickSelectHelper(fastAdapter, this)
            fastAdapter.onPreClickListener =
                { _, _, _, position -> helper.onPreClickListener(position) }
            fastAdapter.onPreLongClickListener =
                { _, _, _, p ->
                    // Warning : specific code for drag selection
                    mDragSelectTouchListener?.startDragSelection(p)
                    helper.onPreLongClickListener(p)
                }
        }

        // Select / deselect on swipe
        val onDragSelectionListener: DragSelectTouchListener.OnDragSelectListener =
            DragSelectionProcessor(object : DragSelectionProcessor.ISelectionHandler {
                override val selection: Set<Int>
                    get() = selectExtension!!.selections

                override fun isSelected(index: Int): Boolean {
                    return selectExtension!!.selections.contains(index)
                }

                override fun updateSelection(
                    start: Int,
                    end: Int,
                    isSelected: Boolean,
                    calledFromOnStart: Boolean
                ) {
                    selectExtension?.let { se ->
                        if (isSelected) IntRange(start, end).forEach {
                            se.select(
                                it,
                                fireEvent = false,
                                considerSelectableFlag = true
                            )
                        }
                        else se.deselect(IntRange(start, end).toMutableList())
                    }
                }
            }).withMode(DragSelectionProcessor.Mode.Simple)

        DragSelectTouchListener().withSelectListener(onDragSelectionListener).let {
            mDragSelectTouchListener = it
            binding?.recyclerView?.addOnItemTouchListener(it)
        }

        // Drag, drop & swiping
        if (activity.get()!!.isEditMode()) {
            val dragSwipeCallback: SimpleDragCallback = SimpleSwipeDragCallback(
                this,
                this,
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_action_delete)
            ).withSensitivity(10f).withSurfaceThreshold(0.75f)
            dragSwipeCallback.notifyAllDrops = true
            dragSwipeCallback.setIsDragEnabled(false) // Despite its name, that's actually to disable drag on long tap
            touchHelper = ItemTouchHelper(dragSwipeCallback)
            binding?.recyclerView?.let {
                touchHelper?.attachToRecyclerView(it)
            }
        }

        // Item click listener
        fastAdapter.onClickListener = { _, _, i: ContentItem, _ -> onItemClick(i) }

        // Favourite button click listener
        fastAdapter.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<ContentItem>,
                item: ContentItem
            ) {
                if (item.content != null) onFavouriteClick(item.content)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.favouriteButton
                } else super.onBind(viewHolder)
            }
        })

        fastAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding?.recyclerView?.apply {
            adapter = fastAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * LiveData callback when the archives change
     * Happens when navigating
     */
    private fun onArchivesChanged(result: Pair<List<Content>, Int>) {
        val enabled = activity.get()?.isLrrDisplayed() == true
        callback?.isEnabled = enabled
        if (!enabled) return

        val resSize = result.first.size
        val maxItems = result.second
        Timber.i(">> LRR archives changed [new] (LRR) ! Size=$resSize/$maxItems)")

        val isEmpty = 0 == resSize
        activity.get()?.updateTitle(resSize, maxItems)

        // Grid won't be used in edit mode
        val viewType =
            if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay) ContentItem.ViewType.LIBRARY
            else ContentItem.ViewType.LIBRARY_GRID

        // Copy result to new list to avoid concurrency issues when processing updated list
        val archives = result.first.toList().map { ContentItem(it, null, viewType) }
        set(itemAdapter, archives, contentItemDiffCallback)

        // Update visibility and content of advanced search bar
        // - After getting results from a search
        // - When switching between Group and Content view
        activity.get()?.updateSearchBarOnResults(!isEmpty)
    }

    // TODO doc
    private fun onSubmitSearch(query: String) {
        viewModel.setLrrQuery(query)
    }

    /**
     * Callback for the item holder itself
     *
     * @param item item that has been clicked on
     */
    private fun onItemClick(item: ContentItem): Boolean {
        if (selectExtension!!.selections.isEmpty()) {
            item.content?.let {
                openReaderForResource(requireContext(), it)
            }
            return true
        }
        return false
    }

    fun openReaderForResource(
        context: Context,
        lrrArchive: Content
    ): Boolean {
        val dialog = ProgressDialogFragment.invoke(
            this,
            resources.getString(R.string.lrr_server_extracting),
            -1
        )

        // Forge a streamed book from LRR
        lifecycleScope.launch(Dispatchers.IO) {
            LrrServer.api.extract(lrrArchive.uniqueSiteId).execute().let { extraction ->
                if (extraction.isSuccessful) {
                    extraction.body()?.let { eb ->
                        lrrArchive.setImageFiles(
                            urlsToImageFiles(
                                eb.pages
                                    .map {
                                        it.replace("/api/", "${Settings.lrrEndpoint}/api/")
                                    },
                                "",
                                StatusContent.ONLINE,
                                Site.LRR
                            )
                        )

                        // Save content as temp material and open it with the reader
                        val dao = ObjectBoxDAO()
                        try {
                            lrrArchive.status = StatusContent.SAVED
                            val contentId = addContent(context, dao, lrrArchive)
                            val builder = ReaderActivityBundle()
                            builder.contentId = contentId

                            val intent = Intent(context, ReaderActivity::class.java)
                            intent.putExtras(builder.bundle)

                            context.startActivity(intent)
                        } finally {
                            dao.cleanup()
                        }
                    }
                }
            }
            dialog.dismissAllowingStateLoss()
        }
        return true
    }

    /**
     * Callback for the "favourite" button of the book holder
     *
     * @param content Content whose "favourite" button has been clicked on
     */
    private fun onFavouriteClick(content: Content) {
        viewModel.toggleContentFavourite(content)
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        val selectedCount = selectedItems.size
        if (0 == selectedCount) {
            activity.get()?.getSelectionToolbar()?.visibility = View.GONE
            selectExtension?.selectOnLongClick = true
        } else {
            activity.get()?.apply {
                updateSelectionToolbar(selectedCount, 0, 0, 0, 0, 0, 0, false)
                getSelectionToolbar()?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * DRAG, DROP & SWIPE METHODS
     */
    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        onMove(itemAdapter, oldPosition, newPosition) // change position
        return true
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        // Nothing; final position will be saved once the "save" button is hit
    }

    override fun itemTouchStartDrag(viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder is IDraggableViewHolder) {
            (viewHolder as IDraggableViewHolder).onDragged()
        }
    }

    override fun itemSwiped(position: Int, direction: Int) {
        // TODO
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    /**
     * Callback for the rating dialog
     */
    fun leaveSelectionMode() {
        selectExtension?.apply {
            selectOnLongClick = true
            // Warning : next line makes FastAdapter cycle through all items,
            // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
            // flagging the end of the list as being the last displayed position
            val selection = selections
            if (selection.isNotEmpty()) deselect(selection.toMutableSet())
        }
        activity.get()?.getSelectionToolbar()?.visibility = View.GONE
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val title = itemAdapter.getAdapterItem(position).title
        return when (Settings.lrrSortField) {

            Settings.Value.ORDER_FIELD_TITLE -> if (title.isEmpty()) ""
            else (title).uppercase(Locale.getDefault()).substring(1)

            else -> ""
        }
    }
}
package me.devsaki.hentoid.fragments.tools

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.fastadapter.select.SelectExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DuplicateDetectorActivity
import me.devsaki.hentoid.activities.bundles.DuplicateItemBundle
import me.devsaki.hentoid.customssiv.util.FILECHUNK_AUTHORITY
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.databinding.FragmentDuplicateDetailsBinding
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.fragments.library.MergeDialogFragment
import me.devsaki.hentoid.util.image.getMediaDimensions
import me.devsaki.hentoid.util.openReader
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.viewContentGalleryPage
import me.devsaki.hentoid.viewholders.DuplicateItem
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

@Suppress("PrivatePropertyName")
class DuplicateDetailsFragment : Fragment(R.layout.fragment_duplicate_details),
    MergeDialogFragment.Parent {

    private var binding: FragmentDuplicateDetailsBinding? = null

    // Communication
    private var callback: OnBackPressedCallback? = null
    private lateinit var activity: WeakReference<DuplicateDetectorActivity>
    lateinit var viewModel: DuplicateViewModel

    // UI
    private val itemAdapter = ItemAdapter<DuplicateItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension: SelectExtension<DuplicateItem>? = null

    // Vars
    private var enabled = true


    private val ITEM_DIFF_CALLBACK: DiffCallback<DuplicateItem> =
        object : DiffCallback<DuplicateItem> {
            override fun areItemsTheSame(
                oldItem: DuplicateItem,
                newItem: DuplicateItem
            ): Boolean {
                return oldItem.identifier == newItem.identifier
            }

            override fun areContentsTheSame(
                oldItem: DuplicateItem,
                newItem: DuplicateItem
            ): Boolean {
                return (oldItem.keep == newItem.keep)
                        && (oldItem.isBeingDeleted == newItem.isBeingDeleted)
            }

            override fun getChangePayload(
                oldItem: DuplicateItem,
                oldItemPosition: Int,
                newItem: DuplicateItem,
                newItemPosition: Int
            ): Any? {
                val diffBundleBuilder = DuplicateItemBundle()
                if (oldItem.keep != newItem.keep) {
                    diffBundleBuilder.isKeep = newItem.keep
                }
                if (oldItem.isBeingDeleted != newItem.isBeingDeleted) {
                    diffBundleBuilder.isBeingDeleted = newItem.isBeingDeleted
                }
                return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is DuplicateDetectorActivity) { "Parent activity has to be a DuplicateDetectorActivity" }
        activity =
            WeakReference<DuplicateDetectorActivity>(requireActivity() as DuplicateDetectorActivity)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDuplicateDetailsBinding.inflate(inflater, container, false)
        addCustomBackControl()
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[DuplicateViewModel::class.java]

        initSelectionToolbar()

        // List
        binding?.list?.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            FastScrollerBuilder(this).build()
            adapter = fastAdapter
        }

        viewModel.selectedDuplicates.observe(viewLifecycleOwner) { this.onDuplicatesChanged(it) }

        // Item click listener
        fastAdapter.onClickListener = { _, _, item, _ ->
            onItemClick(item)
            false
        }

        // Site button click listener
        fastAdapter.addEventHook(object : ClickEventHook<DuplicateItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<DuplicateItem>,
                item: DuplicateItem
            ) {
                item.content?.let {
                    viewContentGalleryPage(requireContext(), it)
                }
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is DuplicateItem.ViewHolder) {
                    viewHolder.siteButton
                } else super.onBind(viewHolder)
            }
        })

        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension?.apply {
            isSelectable = true
            multiSelect = true
            selectOnLongClick = true
            selectWithItemUpdate = true
            selectionListener =
                object : ISelectionListener<DuplicateItem> {
                    override fun onSelectionChanged(item: DuplicateItem, selected: Boolean) {
                        onSelectionChanged()
                    }
                }
            val helper = FastAdapterPreClickSelectHelper(fastAdapter, this)
            fastAdapter.onPreClickListener =
                { _, _, _, position -> helper.onPreClickListener(position) }
            fastAdapter.onPreLongClickListener =
                { _, _, _, p ->
                    // Warning : specific code for drag selection
                    helper.onPreLongClickListener(p)
                }
        }

        lifecycleScope.launch {
            activity.get()?.duplicateDetectorEvents?.collect(this@DuplicateDetailsFragment::onActivityEvent)
        }

        binding?.applyBtn?.apply {
            setOnClickListener {
                isEnabled = false
                viewModel.applyChoices {
                    isEnabled = true
                    activity.get()?.goBackToMain()
                }
            }
        }
    }

    private fun initSelectionToolbar() {
        activity.get()?.getSelectionToolbar()?.apply {
            setNavigationOnClickListener { _ ->
                selectExtension.apply { leaveSelectionMode() }
                visibility = View.GONE
            }
            setOnMenuItemClickListener { onSelectionToolbarItemClicked(it) }
        }
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedItems = selectExtension?.selectedItems ?: return
        val selectedCount = selectedItems.size

        val externalCount = selectedItems.mapNotNull { it.content }
            .count { it.status == StatusContent.EXTERNAL }
        val streamedCount = selectedItems.mapNotNull { it.content }
            .count { it.downloadMode == DownloadMode.STREAM }
        val localCount = selectedCount - externalCount - streamedCount

        // streamed, external
        activity.get()
            ?.updateSelectionToolbar(selectedCount > 0, localCount, externalCount, streamedCount)

        if (0 == selectedCount) {
            selectExtension?.selectOnLongClick = true
        }
    }

    private fun addCustomBackControl() {
        if (callback != null) callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onCustomBackPress()
            }
        }
        activity.get()?.let { a ->
            a.onBackPressedDispatcher.addCallback(a, callback!!)
        }
    }

    private fun onCustomBackPress() {
        Handler(Looper.getMainLooper()).postDelayed({ activity.get()?.goBackToMain() }, 100)
    }

    private fun onItemClick(item: DuplicateItem) {
        val c: Content? = item.content
        // Process the click
        if (null == c) {
            toast(R.string.err_no_content)
            return
        }

        if (!openReader(requireContext(), c, newTask = true)) toast(R.string.err_no_content)
    }

    private fun onBookChoice(item: Content?, isKeep: Boolean) {
        item ?: return
        viewModel.setBookChoice(item, isKeep)
    }

    @Synchronized
    private fun onDuplicatesChanged(duplicates: List<DuplicateEntry>?) {
        if (null == duplicates) return

        Timber.i(">> New selected duplicates ! Size=${duplicates.size}")

        if (duplicates.isEmpty()) activity.get()?.goBackToMain()

        lifecycleScope.launch(Dispatchers.Main) {
            // Auto-suggest action = detect books to keep according to multiple criteria
            // See https://codeberg.org/VioletKnight/Hentoid/issues/53#issuecomment-21987356
            val kept: MutableSet<Content> = HashSet()

            // Number of pages (keep the highest; no tolerance)
            val critPagesMap = duplicates.groupBy { it.duplicateContent?.qtyPages ?: 0 }
            val bestPages = critPagesMap.getValue(critPagesMap.keys.max())
            kept.addAll(bestPages.mapNotNull { it.duplicateContent })

            // Make sure we can directly read files (no PDFs or exotic archives)
            val readableKept = kept.filter { hasAccessibleFiles(it) }

            // Resolution
            if (readableKept.size == kept.size && kept.size > 1) {
                try {
                    val qtyPages = kept.first().qtyPages
                    // Select 3 pages for reference (based on the same number of pages => indexes should match)
                    val indexes = listOf(
                        (qtyPages * 0.25).roundToInt(),
                        (qtyPages * 0.50).roundToInt(),
                        (qtyPages * 0.75).roundToInt()
                    )
                    // Get resolution for selected pages
                    val resPerBook = HashMap<Long, List<Point>>()
                    withContext(Dispatchers.IO) {
                        kept.forEach { b ->
                            val imgList = b.imageList
                            val bookRes = ArrayList<Point>()
                            indexes.forEach { i ->
                                bookRes.add(
                                    getMediaDimensions(requireContext(), imgList[i].fileUri.toUri())
                                )
                            }
                            resPerBook[b.id] = bookRes
                        }
                    }
                    // Best book is the one with the larger surface on all selected pages
                    // NB : Equivalent surfaces (<=2% variation) can select multiple books
                    var bestBooks = kept.map { it.id }.toSet()
                    repeat(3) { i ->
                        val idxSurface = ArrayList<Pair<Long, Int>>()
                        resPerBook.keys.forEach { b ->
                            idxSurface.add(
                                Pair(
                                    b,
                                    resPerBook.getValue(b)[i].x * resPerBook.getValue(b)[i].y
                                )
                            )
                        }
                        val bestSurface = idxSurface.maxOf { it.second }
                        val idxBestBooks = idxSurface
                            .filter { it.second >= bestSurface * 0.98 }
                            .map { it.first }.toSet()
                        bestBooks = bestBooks.intersect(idxBestBooks)
                        if (bestBooks.isEmpty()) return@repeat
                    }
                    if (bestBooks.isNotEmpty()) {
                        kept.clear()
                        kept.addAll(
                            duplicates
                                .filter { bestBooks.contains(it.duplicateId) }
                                .mapNotNull { it.duplicateContent }
                        )
                    }
                } catch (e: Throwable) {
                    Timber.w(e)
                }
            }

            // Size (keep largest; multiple books if identical size)
            if (kept.size > 1) {
                val maxSize = kept.maxOf { it.size }
                val largest = kept.filter { it.size == maxSize }
                kept.clear()
                kept.addAll(largest)
            }

            // Flag them
            val keptIds = kept.map { it.id }
            duplicates.forEach { it.keep = keptIds.contains(it.duplicateContent?.id ?: 0) }

            // Order by relevance desc and transform to DuplicateItem
            val items = duplicates.sortedByDescending { it.calcTotalScore() }
                .map { DuplicateItem(it, DuplicateItem.ViewType.DETAILS, it.keep) }.toMutableList()
            items.forEach { it.onKeepChange = { b -> onBookChoice(it.content, b) } }
            set(itemAdapter, items, ITEM_DIFF_CALLBACK)
        }
    }

    private fun hasAccessibleFiles(c: Content): Boolean {
        if (c.isPdf) return false
        if (c.isArchive)
            return c.imageList.all { it.fileUri.toUri().authority == FILECHUNK_AUTHORITY }
        return c.imageList.none { it.isOnline }
    }

    private fun onActivityEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.DUPLICATE_DETAILS) return
        when (event.type) {
            CommunicationEvent.Type.ENABLE -> onEnable()
            CommunicationEvent.Type.DISABLE -> onDisable()
            CommunicationEvent.Type.UNSELECT -> leaveSelectionMode()
            else -> {}
        }
    }

    private fun onSelectionToolbarItemClicked(menuItem: MenuItem): Boolean {
        val items = selectExtension?.selectedItems ?: return true
        when (menuItem.itemId) {
            R.id.action_merge -> {
                MergeDialogFragment.invoke(
                    this,
                    items.mapNotNull { it.content },
                    true
                )
            }
        }
        return true
    }

    private fun onEnable() {
        enabled = true
        callback?.isEnabled = true
    }

    private fun onDisable() {
        enabled = false
        callback?.isEnabled = false
    }

    override fun mergeContents(
        contentList: List<Content>,
        newTitle: String,
        useBookAsChapter: Boolean, // Ignored on duplicate detector
        keepFirstBookChaps: Boolean,
        deleteAfterMerging: Boolean
    ) {
        viewModel.mergeContents(
            contentList,
            newTitle,
            useBookAsChapter,
            deleteAfterMerging,
        ) {
            toast(R.string.merge_success)
        }
        ProgressDialogFragment.invoke(
            this,
            resources.getString(R.string.merge_progress),
            R.plurals.page
        )
    }

    override fun leaveSelectionMode() {
        selectExtension?.let {
            it.selectOnLongClick = true
            // Warning : next line makes FastAdapter cycle through all items,
            // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
            // flagging the end of the list as being the last displayed position
            val selection = it.selections
            if (selection.isNotEmpty()) it.deselect(selection.toMutableSet())
        }
        activity.get()?.getSelectionToolbar()?.visibility = View.GONE
    }
}
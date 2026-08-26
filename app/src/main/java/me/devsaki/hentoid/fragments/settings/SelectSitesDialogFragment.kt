package me.devsaki.hentoid.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogSelectSitesBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewholders.SiteItem

class SelectSitesDialogFragment : BaseDialogFragment<SelectSitesDialogFragment.Parent>(),
    ItemTouchCallback {

    companion object {
        private const val SELECTED_SITES = "sites"
        private const val FILTER_VISIBLE = "filter_visible"
        private const val INCLUDE_NONE = "include_none"

        fun invoke(
            parentFragment: Fragment,
            activeSites: List<Site>,
            filterVisible: Boolean = false,
            includeNone: Boolean = false
        ) {
            val args = getArgs(activeSites, filterVisible, includeNone)
            invoke(parentFragment, SelectSitesDialogFragment(), args)
        }

        fun invoke(
            activity: FragmentActivity,
            activeSites: List<Site>,
            filterVisible: Boolean = false,
            includeNone: Boolean = false
        ): DialogFragment {
            val args = getArgs(activeSites, filterVisible, includeNone)
            return invoke(activity, SelectSitesDialogFragment(), args, isCancelable = true)
        }

        private fun getArgs(
            activeSites: List<Site>,
            filterVisible: Boolean,
            includeNone: Boolean
        ): Bundle {
            val args = Bundle()
            args.putIntArray(SELECTED_SITES, activeSites.map { it.code }.toIntArray())
            args.putBoolean(FILTER_VISIBLE, filterVisible)
            args.putBoolean(INCLUDE_NONE, includeNone)
            return args
        }
    }

    // == UI
    private var binding: DialogSelectSitesBinding? = null
    private lateinit var recyclerView: RecyclerView
    private val itemAdapter = ItemAdapter<SiteItem>()
    private val fastAdapter: FastAdapter<SiteItem> = FastAdapter.with(itemAdapter)


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogSelectSitesBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        val bundle = arguments
        requireNotNull(bundle) { "No arguments found" }
        val selectedSites =
            bundle.getIntArray(SELECTED_SITES)?.map { Site.searchByCode(it) } ?: emptyList()
        val filterVisible = bundle.getBoolean(FILTER_VISIBLE, false)
        val includeNone = bundle.getBoolean(INCLUDE_NONE, false)

        binding?.apply {
            // Toolbar
            toolbar.setOnMenuItemClickListener { clickedMenuItem: MenuItem ->
                when (clickedMenuItem.itemId) {
                    R.id.action_check_all -> onCheckAll()
                    R.id.action_uncheck_all -> onUncheckAll()
                    else -> {}
                }
                true
            }
            recyclerView = drawerEditList
            okBtn.setOnClickListener { onValidateClick() }
        }

        // Activate drag & drop
        val dragCallback = SimpleDragCallback(this)
        dragCallback.notifyAllDrops = true
        val touchHelper = ItemTouchHelper(dragCallback)

        // Recycler
        val items: MutableList<SiteItem> = ArrayList()

        // First add active sites
        items.addAll(
            selectedSites.filter {
                filterSite(it, filterVisible, includeNone)
            }.map {
                SiteItem(it, true, touchHelper)
            }
        )

        // Then add the others
        items.addAll(
            Site.entries.filter {
                !selectedSites.contains(it) &&
                        filterSite(it, filterVisible, includeNone)
            }.map {
                SiteItem(it, false, touchHelper)
            }
        )
        itemAdapter.add(items)

        recyclerView.adapter = fastAdapter
        recyclerView.setHasFixedSize(true)
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun filterSite(site: Site, filterVisible: Boolean, includeNone: Boolean): Boolean {
        return if (site == Site.NONE && includeNone) true
        else if (filterVisible) site.isVisible else site.isUsable
    }

    private fun onCheckAll() {
        for (s in itemAdapter.adapterItems) s.isSelected = true
        fastAdapter.notifyDataSetChanged()
    }

    private fun onUncheckAll() {
        for (s in itemAdapter.adapterItems) s.isSelected = false
        fastAdapter.notifyDataSetChanged()
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        onMove(itemAdapter, oldPosition, newPosition) // change position
        return true
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        val vh = recyclerView.findViewHolderForAdapterPosition(newPosition)
        if (vh is IDraggableViewHolder) {
            (vh as IDraggableViewHolder).onDropped()
        }
    }

    override fun itemTouchStartDrag(viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder is IDraggableViewHolder) {
            (viewHolder as IDraggableViewHolder).onDragged()
        }
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    private fun onValidateClick() {
        parent?.onSitesSelected(itemAdapter.adapterItems.filter { it.isSelected }.map { it.site })
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun onSitesSelected(sites: List<Site>)
    }
}
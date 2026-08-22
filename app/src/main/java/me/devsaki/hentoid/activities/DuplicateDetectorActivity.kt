package me.devsaki.hentoid.activities

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.ActivityDuplicateDetectorBinding
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.fragments.tools.DuplicateDetailsFragment
import me.devsaki.hentoid.fragments.tools.DuplicateMainFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class DuplicateDetectorActivity : BaseActivity() {

    private var binding: ActivityDuplicateDetectorBinding? = null
    private lateinit var viewPager: ViewPager2
    val duplicateDetectorEvents =
        MutableSharedFlow<CommunicationEvent>(2, 2, BufferOverflow.DROP_OLDEST)

    // Viewmodel
    private lateinit var viewModel: DuplicateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDuplicateDetectorBinding.inflate(layoutInflater)
        binding?.apply {
            setContentView(root)
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            selectionToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[DuplicateViewModel::class.java]

        if (!Settings.recentVisibility) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // Cancel any previous worker that might be running TODO CANCEL BUTTON
//        WorkManager.getInstance(application).cancelAllWorkByTag(DuplicateDetectorWorker.WORKER_TAG)

        initUI()
        updateToolbar()
    }

    override fun onPause() {
        super.onPause()
        viewModel.allDuplicates.removeObservers(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun initUI() {
        if (null == binding || null == binding?.duplicatesPager) return

        viewPager = binding?.duplicatesPager as ViewPager2

        // Main tabs
        viewPager.isUserInputEnabled = false // Disable swipe to change tabs

        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                enableCurrentFragment()
                updateToolbar()
                viewModel.allDuplicates.observe(
                    this@DuplicateDetectorActivity
                ) { entry ->
                    updateTitle(entry.groupBy { it.referenceContent }
                        .mapValues { it.value.sumOf { 1L } }.size * -1)
                }
            }
        })

        updateDisplay()
    }

    fun initFragmentToolbars(
        toolbarOnItemClicked: Toolbar.OnMenuItemClickListener
    ) {
        binding?.toolbar?.setOnMenuItemClickListener(toolbarOnItemClicked)
    }

    private fun updateDisplay() {
        val pagerAdapter: FragmentStateAdapter = DuplicatePagerAdapter(this)
        viewPager.adapter = pagerAdapter
        pagerAdapter.notifyDataSetChanged()
        enableCurrentFragment()
    }

    fun goBackToMain() {
        enableFragment(0)
        viewPager.currentItem = 0
    }

    fun showDetailsFor(content: Content) {
        enableFragment(1)
        viewModel.setContent(content)
        viewPager.currentItem = 1
    }

    private fun enableCurrentFragment() {
        enableFragment(viewPager.currentItem)
    }

    private fun enableFragment(fragmentIndex: Int) {
        lifecycleScope.launch {
            duplicateDetectorEvents.emit(
                CommunicationEvent(
                    CommunicationEvent.Type.ENABLE,
                    if (0 == fragmentIndex) CommunicationEvent.Recipient.DUPLICATE_MAIN else CommunicationEvent.Recipient.DUPLICATE_DETAILS
                )
            )
            duplicateDetectorEvents.emit(
                CommunicationEvent(
                    CommunicationEvent.Type.DISABLE,
                    if (0 == fragmentIndex) CommunicationEvent.Recipient.DUPLICATE_DETAILS else CommunicationEvent.Recipient.DUPLICATE_MAIN
                )
            )
        }
    }

    /**
     * Update the title of the DuplicateDetectorActivity
     * ```
     * if count>0, update the title to "n duplicates" for the detail page
     * if count<0, update the title to "n item(s) left" for the main page
     * if count=0, update the title to "Duplicate Detector" for the main page without any items
     * ```
     * @param count Number of items
     */
    fun updateTitle(count: Int) {
        binding!!.toolbar.title = if (count > 0) resources.getQuantityString(
            R.plurals.duplicate_detail_title,
            count,
            count
        ) else if (count < 0) resources.getQuantityString(
            R.plurals.duplicate_main_title,
            -count,
            -count
        ) else resources.getString(R.string.title_activity_duplicate_detector)
    }

    fun updateToolbar() {
        binding?.apply {
            toolbar.menu.findItem(R.id.action_settings).isVisible =
                (0 == viewPager.currentItem)
        }
    }

    fun updateSelectionToolbar(
        visible: Boolean,
        localCount: Int,
        externalCount: Int,
        streamedCount: Int
    ) {
        binding?.selectionToolbar?.apply {
            isVisible = visible
            menu.findItem(R.id.action_merge).isVisible =
                (localCount > 1 && 0 == streamedCount && 0 == externalCount)
                        || (streamedCount > 1 && 0 == localCount && 0 == externalCount)
                        || (externalCount > 1 && 0 == localCount && 0 == streamedCount)
        }
    }

    // === PUBLIC ACCESSORS (to be used by fragments)

    fun getToolbarView(): View? {
        return binding?.toolbar
    }

    fun getSelectionToolbar(): Toolbar? {
        return binding?.selectionToolbar
    }

    /**
     * ============================== SUBCLASS
     */
    private class DuplicatePagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return if (0 == position) {
                DuplicateMainFragment()
            } else {
                DuplicateDetailsFragment()
            }
        }

        override fun getItemCount(): Int {
            return 2
        }
    }
}
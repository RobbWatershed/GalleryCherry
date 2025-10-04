package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.URL_GITHUB_WIKI_TRANSFER
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogToolsMetaExportBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.util.exportToDownloadsFolder
import me.devsaki.hentoid.util.getPathRoot
import me.devsaki.hentoid.util.serializeToJson
import timber.log.Timber
import java.nio.charset.StandardCharsets

class MetaExportDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(fragment: Fragment) {
            invoke(fragment, MetaExportDialogFragment())
        }
    }

    // == UI
    private var binding: DialogToolsMetaExportBinding? = null

    // == VARIABLES
    private lateinit var dao: CollectionDAO
    private var locationIndex = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogToolsMetaExportBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        dao = ObjectBoxDAO()
        val nbLibraryBooks = dao.countAllInternalBooks("", false)
        val nbQueueBooks = dao.countAllQueueBooks()
        val nbBookmarks = dao.countAllBookmarks()
        binding?.apply {
            exportQuestion.setOnCheckedChangeListener { _, id ->
                run {
                    exportQuestion.isEnabled = false
                    exportQuestionYes.isEnabled = false
                    exportQuestionNo.isEnabled = false
                    val yes = (R.id.export_question_yes == id)
                    exportGroupYes.isVisible = yes
                    exportGroupNo.isVisible = !yes
                }
            }

            exportLocation.text = resources.getString(
                R.string.export_location,
                resources.getString(R.string.refresh_location_internal)
            )
            exportLocation.setOnClickListener {
                MaterialAlertDialogBuilder(requireActivity())
                    .setCancelable(true)
                    .setSingleChoiceItems(
                        R.array.export_location_entries,
                        locationIndex
                    ) { dialog, which ->
                        locationIndex = which
                        exportLocation.text = resources.getString(
                            R.string.export_location,
                            resources.getStringArray(R.array.export_location_entries)[locationIndex]
                        )
                        refreshFavsDisplay()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }

            exportFavsOnly.setOnCheckedChangeListener { _, _ -> refreshFavsDisplay() }
            if (nbLibraryBooks > 0) {
                exportFileLibraryChk.text = resources.getQuantityString(
                    R.plurals.export_file_library,
                    nbLibraryBooks.toInt(),
                    nbLibraryBooks.toInt()
                )
                exportFileLibraryChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                exportGroupNo.addView(exportFileLibraryChk)
            }
            if (nbQueueBooks > 0) {
                exportFileQueueChk.text = resources.getQuantityString(
                    R.plurals.export_file_queue,
                    nbQueueBooks.toInt(),
                    nbQueueBooks.toInt()
                )
                exportFileQueueChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                exportGroupNo.addView(exportFileQueueChk)
            }
            if (nbBookmarks > 0) {
                exportFileBookmarksChk.text = resources.getQuantityString(
                    R.plurals.export_file_bookmarks,
                    nbBookmarks.toInt(),
                    nbBookmarks.toInt()
                )
                exportFileBookmarksChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                exportGroupNo.addView(exportFileBookmarksChk)
            }

            // Open library transfer FAQ
            exportWikiLink.setOnClickListener {
                requireActivity().startBrowserActivity(URL_GITHUB_WIKI_TRANSFER)
            }
            exportRunBtn.isEnabled = false
            if (0L == nbLibraryBooks + nbQueueBooks + nbBookmarks)
                exportRunBtn.visibility = View.GONE
            else exportRunBtn.setOnClickListener {
                runExport(
                    exportFileLibraryChk.isChecked,
                    exportFavsOnly.isChecked,
                    exportGroups.isChecked,
                    exportFileQueueChk.isChecked,
                    exportFileBookmarksChk.isChecked
                )
            }
        }
    }

    // Gray out run button if no option is selected
    private fun refreshDisplay() {
        binding?.apply {
            exportRunBtn.isEnabled =
                exportFileQueueChk.isChecked || exportFileLibraryChk.isChecked || exportFileBookmarksChk.isChecked
            exportLocation.isVisible = exportFileLibraryChk.isChecked
            exportFavsOnly.isVisible = exportFileLibraryChk.isChecked
            exportGroups.isVisible = exportFileLibraryChk.isChecked
        }
    }

    private fun refreshFavsDisplay() {
        binding?.let {
            val nbLibraryBooks = dao.countAllInternalBooks(
                getSelectedRootPath(locationIndex),
                it.exportFavsOnly.isChecked
            )
            it.exportFileLibraryChk.text = resources.getQuantityString(
                R.plurals.export_file_library,
                nbLibraryBooks.toInt(),
                nbLibraryBooks.toInt()
            )
            refreshDisplay()
        }
    }

    private fun getSelectedRootPath(locationIndex: Int): String {
        return if (locationIndex > 0) {
            var root =
                getPathRoot(if (1 == locationIndex) StorageLocation.PRIMARY_1 else StorageLocation.PRIMARY_2)
            if (root.isEmpty()) root = "FAIL" // Auto-fails condition if location is not set
            root
        } else ""
    }

    private fun runExport(
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportCustomGroups: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ) {
        binding?.let {
            it.exportFileLibraryChk.isEnabled = false
            it.exportFileQueueChk.isEnabled = false
            it.exportFileBookmarksChk.isEnabled = false
            it.exportRunBtn.visibility = View.GONE
            it.exportProgressBar.isIndeterminate = true
            it.exportProgressBar.visibility = View.VISIBLE
            isCancelable = false

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val collection = getExportedCollection(
                            exportLibrary,
                            exportFavsOnly,
                            exportCustomGroups,
                            exportQueue,
                            exportBookmarks
                        )
                        return@withContext serializeToJson(
                            collection,
                            JsonContentCollection::class.java
                        )
                    } catch (e: Exception) {
                        Timber.w(e)
                        Snackbar.make(
                            it.root,
                            R.string.export_failed,
                            BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                        // Dismiss after 3s, for the user to be able to see and use the snackbar
                        delay(3000)
                        dismissAllowingStateLoss()
                    }
                    return@withContext ""
                }
                if (result.isNotEmpty()) {
                    it.exportProgressBar.max = 2
                    it.exportProgressBar.progress = 1
                    it.exportProgressBar.isIndeterminate = false
                    onJsonSerialized(
                        result,
                        exportLibrary,
                        exportFavsOnly,
                        exportQueue,
                        exportBookmarks
                    )
                }
                it.exportProgressBar.progress = 2
            }
        }
    }

    private fun getExportedCollection(
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportCustomgroups: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ): JsonContentCollection {
        val jsonContentCollection = JsonContentCollection()

        if (exportLibrary) dao.streamAllInternalBooks(
            getSelectedRootPath(locationIndex),
            exportFavsOnly
        ) { content: Content ->
            jsonContentCollection.addToLibrary(content)
        } // Using streaming here to support large collections
        if (exportQueue) {
            val regularQueue = dao.selectQueue()
            val errorsQueue = dao.selectErrorContent()
            val exportedQueue = regularQueue.filter { qr -> qr.content.targetId > 0 }
                .map { qr ->
                    val c = qr.content.target
                    c.isFrozen = qr.frozen
                    return@map c
                }.toMutableList()
            exportedQueue.addAll(errorsQueue)
            jsonContentCollection.replaceQueue(exportedQueue)
        }
        jsonContentCollection.replaceGroups(
            Grouping.DYNAMIC,
            dao.selectGroups(Grouping.DYNAMIC.id)
        )
        if (exportCustomgroups) jsonContentCollection.replaceGroups(
            Grouping.CUSTOM,
            dao.selectGroups(Grouping.CUSTOM.id)
        )
        if (exportBookmarks) jsonContentCollection.replaceBookmarks(dao.selectAllBookmarks())
        jsonContentCollection.replaceRenamingRules(
            dao.selectRenamingRules(AttributeType.UNDEFINED, null)
        )
        return jsonContentCollection
    }

    private fun onJsonSerialized(
        json: String,
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ) {
        var targetFileName = "export-"
        if (exportBookmarks) targetFileName += "bkmks"
        if (exportQueue) targetFileName += "queue"
        if (exportLibrary && !exportFavsOnly) targetFileName += "library"
        else if (exportLibrary) targetFileName += "favs"
        targetFileName += ".json"

        exportToDownloadsFolder(
            requireContext(),
            json.toByteArray(StandardCharsets.UTF_8),
            targetFileName,
            binding?.root
        )

        dao.cleanup()
        // Dismiss after 3s, for the user to be able to see and use the snackbar
        Handler(Looper.getMainLooper()).postDelayed({ this.dismissAllowingStateLoss() }, 3000)
    }
}
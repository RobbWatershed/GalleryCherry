package me.devsaki.hentoid.views

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.databinding.DialogListpickerFilterableBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.viewholders.TextItem

const val ENTRIES = "entries"

class ListPickerDialogFragment(val handler: Consumer<Int>) : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(
            activity: FragmentActivity,
            handler: Consumer<Int>,
            entries: List<String>
        ): DialogFragment {
            val args = getArgs(entries)
            return invoke(activity, ListPickerDialogFragment(handler), args)
        }

        private fun getArgs(entries: List<String>): Bundle {
            val args = Bundle()
            args.putString(ENTRIES, TextUtils.join("|", entries))
            return args
        }
    }

    private var binding: DialogListpickerFilterableBinding? = null

    private val entries: MutableList<String> = ArrayList()

    private val itemAdapter = ItemAdapter<TextItem<String>>()
    private val fastAdapter = FastAdapter.with(itemAdapter)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = arguments
        requireNotNull(bundle) { "No arguments found" }
        val entriesStr = bundle.getString(ENTRIES, "")
        entries.addAll(entriesStr.split("|"))
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogListpickerFilterableBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding?.apply {
            searchTxt.editText?.setOnTextChangedListener(lifecycleScope) { refreshList(it) }
            recyclerView.adapter = fastAdapter
            fastAdapter.onClickListener = { _, _, i, _ -> onItemSelected(i.getObject() ?: "") }
        }
        itemAdapter.add(entries.map { TextItem(it, it, false) })
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun refreshList(str: String) {
        itemAdapter.clear()
        val filteredEntries =
            if (str.isBlank()) entries else entries.filter { it.contains(str, true) }

        itemAdapter.add(filteredEntries.map { TextItem(it, it, false) })
    }

    private fun onItemSelected(s: String): Boolean {
        handler.invoke(entries.indexOf(s))
        dismissAllowingStateLoss()
        return true
    }
}
package me.devsaki.hentoid.fragments.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.databinding.DialogLibraryChangeGroupBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

/**
 * Dialog to select or create a custom group
 */
class ChangeGroupDialogFragment : BaseDialogFragment<ChangeGroupDialogFragment.Parent>() {
    companion object {
        private const val BOOK_IDS = "BOOK_IDS"

        operator fun invoke(parent: Fragment, bookIds: LongArray) {
            val args = Bundle()
            args.putLongArray(BOOK_IDS, bookIds)
            invoke(parent, ChangeGroupDialogFragment(), args)
        }
    }

    private lateinit var contentIds: LongArray
    private lateinit var customGroups: List<Group>

    private var binding: DialogLibraryChangeGroupBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogLibraryChangeGroupBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        check(arguments != null)

        contentIds = requireArguments().getLongArray(BOOK_IDS)!!

        // Get existing custom groups
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            customGroups = dao.selectGroups(Grouping.CUSTOM.id, 0)

            binding?.apply {
                // Don't select the "Ungrouped" group there
                if (customGroups.isNotEmpty()) { // "Existing group" by default
                    existingRadio.isChecked = true
                    existingList.visibility = View.VISIBLE
                    existingList.entries = customGroups.map { it.name }
                    existingList.index = 0 // Default

                    // If all content are in the same group, show it by default
                    val groupIds = dao.selectContent(contentIds)
                        .flatMap { it.getGroupItems(Grouping.CUSTOM) }
                        .map { it.groupId }
                        .toSet()

                    if (1 == groupIds.size) {
                        for (i in customGroups.indices) {
                            if (groupIds.first() == customGroups[i].id) {
                                existingList.index = i
                                break
                            }
                        }
                        // If no group attached, no need to detach from it (!)
                    } else if (groupIds.isEmpty()) detachRadio.visibility = View.GONE

                } else { // If none of them exist, "new group" is suggested by default
                    existingRadio.visibility = View.GONE
                    newRadio.isChecked = true
                    newNameTxt.visibility = View.VISIBLE
                }

                // Radio logic
                existingRadio.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    onExistingRadioSelect(b)
                }
                newRadio.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    onNewRadioSelect(b)
                }
                detachRadio.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    onDetachRadioSelect(b)
                }

                // Item click listener
                actionButton.setOnClickListener { onOkClick() }
            }
        } finally {
            dao.cleanup()
        }
    }

    private fun onExistingRadioSelect(isChecked: Boolean) {
        if (isChecked) {
            binding?.apply {
                existingList.visibility = View.VISIBLE
                newNameTxt.visibility = View.GONE
                newRadio.isChecked = false
                detachRadio.isChecked = false
            }
        }
    }

    private fun onNewRadioSelect(isChecked: Boolean) {
        if (isChecked) {
            binding?.apply {
                existingList.visibility = View.GONE
                newNameTxt.visibility = View.VISIBLE
                existingRadio.isChecked = false
                detachRadio.isChecked = false
            }
        }
    }

    private fun onDetachRadioSelect(isChecked: Boolean) {
        if (isChecked) {
            binding?.apply {
                existingList.visibility = View.GONE
                newNameTxt.visibility = View.GONE
                newRadio.isChecked = false
                existingRadio.isChecked = false
            }
        }
    }

    private fun onOkClick() {
        val vmFactory = ViewModelFactory(requireActivity().application)
        val viewModel =
            ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        binding?.apply {
            if (existingRadio.isChecked) {
                if (existingList.index > -1) {
                    viewModel.moveContentsToCustomGroup(
                        contentIds,
                        customGroups[existingList.index]
                    ) { nbProcessed ->
                        parent?.onChangeGroupSuccess(nbProcessed, contentIds.size)
                        dismissAllowingStateLoss()
                    }
                } else {
                    toast(R.string.group_not_selected)
                }
            } else if (detachRadio.isChecked) {
                viewModel.moveContentsToCustomGroup(contentIds, null) { nbProcessed ->
                    parent?.onChangeGroupSuccess(nbProcessed, contentIds.size)
                    dismissAllowingStateLoss()
                }
            } else newNameTxt.editText?.let { edit -> // New group
                val newNameStr = edit.text.toString().trim { it <= ' ' }
                if (newNameStr.isNotEmpty()) {
                    val groupMatchingName =
                        customGroups.filter { g -> g.name.equals(newNameStr, ignoreCase = true) }
                    if (groupMatchingName.isEmpty()) { // No existing group with same name -> OK
                        viewModel.moveContentsToNewCustomGroup(contentIds, newNameStr)
                        { nbProcessed ->
                            parent?.onChangeGroupSuccess(nbProcessed, contentIds.size)
                            dismissAllowingStateLoss()
                        }
                    } else {
                        toast(R.string.group_name_exists)
                    }
                } else {
                    toast(R.string.group_name_empty)
                }
            }
        }
    }

    interface Parent {
        fun onChangeGroupSuccess(nbProcessed : Int, nbTotal: Int)
    }
}
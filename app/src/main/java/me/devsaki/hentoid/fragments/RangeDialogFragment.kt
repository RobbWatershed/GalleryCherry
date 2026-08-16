package me.devsaki.hentoid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogSelectRangeBinding
import me.devsaki.hentoid.util.isRangeChapters
import me.devsaki.hentoid.util.rangeToNumbers

/**
 * Dialog to select a page or chapter range
 */
private const val PROMPT = "PROMPT"
private const val RANGE = "VALUE"
private const val HAS_CHAPTERS = "HAS_CHAPTERS"

class RangeDialogFragment : BaseDialogFragment<RangeDialogFragment.Parent>() {

    // UI
    private var binding: DialogSelectRangeBinding? = null

    companion object {
        fun invoke(
            activity: FragmentActivity,
            prompt: String,
            range: String,
            hasChapters: Boolean
        ): DialogFragment {
            val args = getArgs(prompt, hasChapters, range)
            return invoke(activity, RangeDialogFragment(), args, isCancelable = true)
        }

        fun invoke(
            fragment: Fragment,
            prompt: String,
            range: String,
            hasChapters: Boolean,
            parentIsActivity: Boolean = false,
        ) {
            val args = getArgs(prompt, hasChapters, range)
            invoke(fragment, RangeDialogFragment(), args, parentIsActivity = parentIsActivity)
        }

        private fun getArgs(
            prompt: String,
            hasChapters: Boolean,
            range: String
        ): Bundle {
            val args = Bundle()
            args.putString(PROMPT, prompt)
            args.putBoolean(HAS_CHAPTERS, hasChapters)
            args.putString(RANGE, range)
            return args
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogSelectRangeBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        val prompt = requireArguments().getString(PROMPT, "")
        val hasChapters = requireArguments().getBoolean(HAS_CHAPTERS, false)
        val range = requireArguments().getString(RANGE, "")

        binding?.apply {
            pages.text = resources.getQuantityString(R.plurals.page, 2)
            chapters.text = resources.getQuantityString(R.plurals.chapter, 2)

            val isChapters = isRangeChapters(range)
            chapters.isChecked = isChapters && hasChapters
            pages.isChecked = !chapters.isChecked
            selection.isVisible = hasChapters

            rangeTxt.hint = prompt
            if (range.isNotBlank()) {
                val value = if (isChapters) range.substring(1) else range
                rangeTxt.editText?.setText(value)
            }

            actionButton.setOnClickListener {
                val result = rangeTxt.editText?.text.toString()

                if (result.isNotBlank()) {
                    val list = rangeToNumbers(result)
                    if (list.isNotEmpty()) {
                        parent?.onRangeSelected(chapters.isChecked, result)
                        this@RangeDialogFragment.dismissAllowingStateLoss()
                    }
                }
            }
        }
    }

    interface Parent {
        fun onRangeSelected(isChapters: Boolean, value: String)
    }
}
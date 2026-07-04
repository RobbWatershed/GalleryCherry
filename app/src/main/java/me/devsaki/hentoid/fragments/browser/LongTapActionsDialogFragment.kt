package me.devsaki.hentoid.fragments.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.databinding.DialogBrowserLongTapBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings

/**
 * Dialog to choose long tap actions
 */
class LongTapActionsDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        operator fun invoke(parent: FragmentActivity) {
            val args = Bundle()
            invoke(parent, LongTapActionsDialogFragment(), args)
        }
    }

    private var binding: DialogBrowserLongTapBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogBrowserLongTapBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding?.apply {
            actionBtn.setOnClickListener {
                Settings.areLongTapActionsChosen = true
                Settings.isBrowserQuickDl = chQuickDl.isChecked
                Settings.isBrowserGrabPics = chGrabImg.isChecked
                dismissAllowingStateLoss()
            }
        }
    }
}
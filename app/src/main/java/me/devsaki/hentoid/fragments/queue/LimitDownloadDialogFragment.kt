package me.devsaki.hentoid.fragments.queue

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogQueueLimitDownloadsBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.ui.invokeNumberInputDialog
import me.devsaki.hentoid.util.download.DownloadDataLimiter
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import java.util.Timer
import kotlin.concurrent.timer

/**
 * Dialog to control the "limit download" feature
 */
class LimitDownloadDialogFragment : BaseDialogFragment<Nothing>() {
    companion object {
        const val ID = "ID"
        fun invoke(fragment: Fragment) {
            val args = Bundle()
            invoke(fragment, LimitDownloadDialogFragment(), args)
        }
    }

    // == UI
    private var binding: DialogQueueLimitDownloadsBinding? = null
    private lateinit var displayTimer: Timer


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        binding = DialogQueueLimitDownloadsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nothing so far
    }

    override fun onDestroy() {
        displayTimer.cancel()
        super.onDestroy()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        updateDisplay()

        binding?.apply {
            reset.setOnClickListener {
                DownloadDataLimiter.reset()
                updateDisplay()
            }
            setNewBtn.setOnClickListener {
                invokeNumberInputDialog(requireActivity(), R.string.limit_download_set_new_prompt) {
                    if (it > 0) {
                        DownloadDataLimiter.limit = it * 1024 * 1024L // MB
                        updateDisplay()
                    }
                }
            }
        }
        displayTimer =
            timer("display-timer", false, 0, 500) {
                // Timer task is not on the UI thread
                val handler = Handler(Looper.getMainLooper())
                handler.post { updateDisplay() }
            }
    }

    private fun updateDisplay() {
        binding?.apply {
            if (DownloadDataLimiter.limit > 0) {
                details.text = resources.getString(
                    R.string.limit_download_limit_current,
                    formatHumanReadableSize(DownloadDataLimiter.consumed, resources),
                    formatHumanReadableSize(DownloadDataLimiter.limit, resources)
                )
                bar.max = DownloadDataLimiter.limit.toInt()
                bar.progress = DownloadDataLimiter.consumed.toInt()
                bar.isVisible = true
                reset.isVisible = true
            } else {
                details.text = resources.getString(R.string.limit_download_limit_none)
                bar.isVisible = false
                reset.isVisible = false
            }
        }
    }
}
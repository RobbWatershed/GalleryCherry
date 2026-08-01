package me.devsaki.hentoid.fragments.transform

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.TransformActivity
import me.devsaki.hentoid.core.checkRange
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.databinding.FragmentTransformEncodeAnimBinding
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.util.Settings
import java.lang.ref.WeakReference

/**
 * Transform encode image tab
 */
class EncodeAnimFragment : Fragment(R.layout.fragment_transform_encode_anim) {
    // == UI
    private var binding: FragmentTransformEncodeAnimBinding? = null

    // Activity
    private lateinit var activity: WeakReference<TransformActivity>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        check(requireActivity() is TransformActivity) { "Parent activity has to be a TransformActivity" }
        activity = WeakReference(requireActivity() as TransformActivity)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTransformEncodeAnimBinding.inflate(inflater, container, false)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Refresh before triggers are set
        refreshUI(true)

        // Populate values
        binding?.apply {
            val animEncoders = PictureEncoder.entries.filter { it.isAnimation }
            encoderAnim.entries = animEncoders.map { it.description }
            encoderAnim.values = animEncoders.map { it.value.toString() }
        }

        // Set triggers
        binding?.apply {
            encoderAnim.setOnValueChangeListener { value ->
                Settings.transcodeEncoderAnim = value.toInt()
                refreshUI()
                activity.get()?.updatePreview()
            }
            encoderAnimQuality.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (encoderAnimQuality.checkRange(50, 100)) {
                    Settings.transcodeAnimQuality = value.toInt()
                    activity.get()?.updatePreview()
                }
            }
        }
    }

    private fun refreshUI(applyValues: Boolean = false) {
        binding?.apply {
            val isAiUpscale = (3 == Settings.resizeMethod) && Settings.isResizeEnabled

            encoderAnim.isVisible = !isAiUpscale
            encoderAnimQuality.isVisible =
                (false == PictureEncoder.fromValue(Settings.transcodeEncoderAnim)?.isLossless)
            if (isAiUpscale) encoderAnimQuality.isVisible = false
            if (applyValues) {
                encoderAnim.value = Settings.transcodeEncoderAnim.toString()
                encoderAnimQuality.editText?.setText(Settings.transcodeAnimQuality.toString())
            }
        }
    }
}
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
import me.devsaki.hentoid.core.resubmit
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.databinding.FragmentTransformEncodeImgBinding
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.util.Settings
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference

/**
 * Transform encode image tab
 */
class EncodeImgFragment : Fragment(R.layout.fragment_transform_encode_img) {
    // == UI
    private var binding: FragmentTransformEncodeImgBinding? = null

    // Activity
    private lateinit var activity: WeakReference<TransformActivity>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        check(requireActivity() is TransformActivity) { "Parent activity has to be a TransformActivity" }
        activity = WeakReference(requireActivity() as TransformActivity)
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        binding = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTransformEncodeImgBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Refresh before triggers are set
        refreshUI(true)

        // Populate values
        binding?.apply {
            val stillEncoders = PictureEncoder.entries.filter { it.isImage }
            encoderAll.entries = stillEncoders.map { it.description }
            encoderAll.values = stillEncoders.map { it.value.toString() }
            encoderLossless.entries = stillEncoders.filter { it.isLossless }.map { it.description }
            encoderLossless.values =
                stillEncoders.filter { it.isLossless }.map { it.value.toString() }
            encoderLossy.entries = stillEncoders.filter { !it.isLossless }.map { it.description }
            encoderLossy.values =
                stillEncoders.filter { !it.isLossless }.map { it.value.toString() }
        }

        // Set triggers
        binding?.apply {
            transcodeMethod.setOnIndexChangeListener { index ->
                Settings.transcodeMethod = index
                refreshUI()
                activity.get()?.updatePreview()
            }
            encoderAll.setOnValueChangeListener { value ->
                Settings.transcodeEncoderAll = value.toInt()
                refreshUI()
                activity.get()?.updatePreview()
            }
            encoderLossless.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossless = value.toInt()
                refreshWarnings()
                activity.get()?.updatePreview()
            }
            encoderLossy.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossy = value.toInt()
                refreshWarnings()
                activity.get()?.updatePreview()
            }
            encoderQuality.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                val minQ = if (Settings.unlockTransformCaps) 1 else 50
                if (encoderQuality.checkRange(minQ, 100)) {
                    Settings.transcodeQuality = value.toInt()
                    activity.get()?.updatePreview()
                }
            }
        }
    }

    private fun refreshUI(applyValues: Boolean = false) {
        binding?.apply {
            val isAiUpscale = (3 == Settings.resizeMethod) && Settings.isResizeEnabled

            transcodeMethod.isVisible = !isAiUpscale
            if (applyValues) transcodeMethod.index = Settings.transcodeMethod
            encoderAll.isVisible = (0 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderAll.value = Settings.transcodeEncoderAll.toString()
            encoderLossless.isVisible = (1 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderLossless.value = Settings.transcodeEncoderLossless.toString()
            encoderLossy.isVisible = (1 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderLossy.value = Settings.transcodeEncoderLossy.toString()
            val isEncoderAllLossy =
                !(PictureEncoder.fromValue(Settings.transcodeEncoderAll)?.isLossless ?: false)
            encoderQuality.isVisible =
                (1 == transcodeMethod.index || (0 == transcodeMethod.index && isEncoderAllLossy))
            if (isAiUpscale) encoderQuality.isVisible = false
            if (applyValues) encoderQuality.editText?.setText(Settings.transcodeQuality.toString())
            refreshWarnings()
        }
    }

    private fun refreshWarnings() {
        if ((0 == Settings.transcodeMethod && (Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSY.value || Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSLESS.value))
            || (1 == Settings.transcodeMethod && (Settings.transcodeEncoderLossy == PictureEncoder.WEBP_LOSSY.value || Settings.transcodeEncoderLossless == PictureEncoder.WEBP_LOSSLESS.value))
        ) {
            activity.get()?.setWarnings(1, setOf(R.string.encoder_warning))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.TRANSFORM_ALL && event.recipient != CommunicationEvent.Recipient.ALL) return
        when (event.type) {
            CommunicationEvent.Type.UPDATE -> {
                // Small hack to force input validation
                binding?.encoderQuality?.resubmit()
                refreshUI()
            }

            else -> {}
        }
    }
}
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
import me.devsaki.hentoid.databinding.FragmentTransformResizeBinding
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.image.screenHeight
import me.devsaki.hentoid.util.image.screenWidth
import java.lang.ref.WeakReference
import kotlin.math.max

/**
 * Transform resize tab
 */
class ResizeFragment : Fragment(R.layout.fragment_transform_resize) {
    // == UI
    private var binding: FragmentTransformResizeBinding? = null

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
        binding = FragmentTransformResizeBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Refresh before triggers are set
        refreshUI(true)

        // Set triggers
        binding?.apply {
            resizeSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isResizeEnabled = isChecked
                refreshUI()
                activity.get()?.updatePreview()
            }
            resizeMethod.setOnIndexChangeListener { index ->
                Settings.resizeMethod = index
                refreshUI()
                activity.get()?.updatePreview()
            }
            resizeMethod1Ratio.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (resizeMethod1Ratio.checkRange(100, 200)) {
                    Settings.resizeMethod1Ratio = value.toInt()
                    activity.get()?.updatePreview()
                }
            }
            resizeMethod2MaxWidth.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (resizeMethod2MaxWidth.checkRange(screenWidth, screenWidth * 10)) {
                    Settings.resizeMethod2Width = value.toInt()
                    activity.get()?.updatePreview()
                }
            }
            resizeMethod2MaxHeight.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (resizeMethod2MaxHeight.checkRange(screenHeight, screenHeight * 10)) {
                    Settings.resizeMethod2Height = value.toInt()
                    activity.get()?.updatePreview()
                }
            }
            resizeMethod3Ratio.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (resizeMethod3Ratio.checkRange(10, 100)) {
                    Settings.resizeMethod3Ratio = value.toInt()
                    activity.get()?.updatePreview()
                }
            }
            resizeMethod5Images.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (resizeMethod5Images.checkRange(1, 200)) {
                    Settings.resizeMethod5Images = value.toInt()
                    activity.get()?.updatePreview()
                }
            }
        }
    }

    private fun refreshUI(applyValues: Boolean = false) {
        binding?.apply {
            if (applyValues) resizeSwitch.isChecked = Settings.isResizeEnabled

            if (applyValues) resizeMethod.index = Settings.resizeMethod
            resizeMethod.isVisible = Settings.isResizeEnabled
            resizeMethod1Ratio.isVisible = (0 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod1Ratio.editText?.setText(Settings.resizeMethod1Ratio.toString())
            resizeMethod2MaxWidth.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) {
                val value = max(Settings.resizeMethod2Width, screenWidth)
                resizeMethod2MaxWidth.editText?.setText(value.toString())
            }
            resizeMethod2MaxHeight.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) {
                val value = max(Settings.resizeMethod2Height, screenHeight)
                resizeMethod2MaxHeight.editText?.setText(value.toString())
            }
            resizeMethod3Ratio.isVisible = (2 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod3Ratio.editText?.setText(Settings.resizeMethod3Ratio.toString())
            resizeMethod5Images.isVisible = (4 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod5Images.editText?.setText(Settings.resizeMethod5Images.toString())

            val isAiUpscale = (3 == Settings.resizeMethod) && Settings.isResizeEnabled
            if (isAiUpscale) activity.get()?.setWarnings(1, setOf(R.string.ai_rescale_warning))
        }
    }
}
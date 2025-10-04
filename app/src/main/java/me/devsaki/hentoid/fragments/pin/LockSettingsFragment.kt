package me.devsaki.hentoid.fragments.pin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import dev.skomlach.biometric.compat.BiometricAuthRequest
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiometricsHelper
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.core.startBiometric
import me.devsaki.hentoid.databinding.FragmentPinSettingsOnBinding
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.snack

class LockSettingsFragment : Fragment(), DeactivatePinDialogFragment.Parent,
    ResetPinDialogFragment.Parent, ActivatePinDialogFragment.Parent {

    private var initialLockType: Int = 0

    private var binding: FragmentPinSettingsOnBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPinSettingsOnBinding.inflate(inflater, container, false)

        initialLockType = Settings.lockType

        binding?.apply {
            refresh()
            toolbar.setNavigationOnClickListener { requireActivity().finish() }
            lockType.setOnIndexChangeListener { onLockTypeChanged(it) }
            switchLockOnRestore.setOnCheckedChangeListener { _, v: Boolean ->
                onLockOnAppRestoreClick(v)
            }
            lockTimer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    Settings.lockTimer = position
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Nothing to do
                }
            }
            textResetPin.setOnClickListener { onResetClick() }
        }
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun refresh() {
        val newLockType = Settings.lockType
        binding?.apply {
            switchLockOnRestore.isVisible = (newLockType > 0)
            textResetPin.isVisible = (1 == newLockType)
            lockType.index = newLockType
            initialLockType = newLockType
            val lockOnAppRestoredEnabled = Settings.lockOnAppRestore
            switchLockOnRestore.isChecked = lockOnAppRestoredEnabled
            lockTimer.isVisible = (newLockType > 0 && lockOnAppRestoredEnabled)
            lockTimer.setSelection(Settings.lockTimer)
        }
    }

    override fun onPinDeactivateSuccess() {
        refresh()
        snack(R.string.app_lock_disabled)
    }

    override fun onPinDeactivateCancel() {
        refresh()
    }

    override fun onPinResetSuccess() {
        snack(R.string.pin_reset_success)
    }

    override fun onPinActivateSuccess() {
        refresh()
        snack(R.string.app_lock_enable)
        HentoidApp.setUnlocked(true) // Now that PIN lock is enabled, the app needs to be marked as currently unlocked to avoid showing an unnecessary PIN dialog at next navigation action
        parentFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, LockSettingsFragment())
            .commit()
    }

    override fun onPinActivateCancel() {
        refresh()
    }

    private fun onBiometricsActivateSuccess(result: Boolean) {
        if (result) {
            Settings.lockType = 2
            Settings.appLockPin = ""
        }
        refresh()
    }

    private fun onBiometricsDeactivateSuccess(result: Boolean) {
        if (result) {
            Settings.lockType = 0
            snack(R.string.app_lock_disabled)
        }
        refresh()
    }

    private fun onLockTypeChanged(index: Int) {
        if (0 == index) { // Off
            if (1 == initialLockType) DeactivatePinDialogFragment().show(childFragmentManager, null)
            else if (2 == initialLockType) {
                val bestBM = BiometricsHelper.detectBestBiometric()
                if (bestBM != null) {
                    activity?.startBiometric(
                        BiometricAuthRequest(bestBM.api, bestBM.type), true,
                        this::onBiometricsDeactivateSuccess
                    )
                }
            }
        } else if (index != initialLockType) {
            if (1 == index) { // PIN
                ActivatePinDialogFragment().show(childFragmentManager, null)
            } else { // Biometrics
                val bestBM = BiometricsHelper.detectBestBiometric()
                if (bestBM != null) {
                    activity?.startBiometric(
                        BiometricAuthRequest(bestBM.api, bestBM.type), true,
                        this::onBiometricsActivateSuccess
                    )
                } else {
                    binding?.lockType?.index = initialLockType
                    snack(R.string.app_lock_biometrics_fail, true)
                }
            }
        }
    }

    private fun onLockOnAppRestoreClick(newValue: Boolean) {
        Settings.lockOnAppRestore = newValue
        binding?.lockTimer?.isVisible = newValue
    }

    private fun onResetClick() {
        val fragment = ResetPinDialogFragment()
        fragment.show(childFragmentManager, null)
    }
}
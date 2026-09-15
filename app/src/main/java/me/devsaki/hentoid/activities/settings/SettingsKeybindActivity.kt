package me.devsaki.hentoid.activities.settings

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.databinding.ActivitySettingsCustomInputBinding
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.PreferencesParser
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.decodeMotionEvent
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.viewholders.TextItem
import timber.log.Timber

/**
 * Activity to edit keybindings (needs to be an Activity to properly capture all onKeyDown and onGenericMotionEvent events)
 */
class SettingsKeybindActivity : BaseActivity() {
    private var binding: ActivitySettingsCustomInputBinding? = null

    private val itemAdapter = ItemAdapter<TextItem<Pair<String, String>>>()
    val fastadapter = FastAdapter.with(itemAdapter)

    // == VARS ==
    private var currentKey: String = ""
    private lateinit var actionLabels: List<String>

    @SuppressLint("NonConstantResourceId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionLabels = resources.getStringArray(R.array.pref_viewer_action_binding_entries).toList()

        binding = ActivitySettingsCustomInputBinding.inflate(layoutInflater)
        binding?.apply {
            setContentView(root)

            toolbar.title = resources.getString(R.string.pref_viewer_key_bindings_title)
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

            action.index = 0
            recordBtn.setOnClickListener { onRecordClick() }

            recyclerview.adapter = fastadapter
            fastadapter.onClickListener =
                { _, _, i, _ -> if (i.isSelectable) onItemClick(i) else false }

            updateList(Settings.readerCustomKeys)
        }

        val prefsParser = PreferencesParser()
        prefsParser.addResourceFile(this, R.xml.preferences)
    }

    fun onInputEvent(category: String, code: String): Boolean {
        if (currentKey.isNotBlank()) {
            val mappings = Settings.readerCustomKeys.toMutableMap()
            mappings[currentKey] = ("$category|$code").lowercase()
            Settings.readerCustomKeys = mappings
            updateList(mappings)
            binding?.apply {
                recordBtn.isVisible = true
                recordTxt.clearAnimation()
                recordTxt.isVisible = false
            }
            currentKey = ""
            return true
        }
        return false
    }

    private fun onRecordClick() {
        binding?.apply {
            currentKey = action.value
            recordBtn.isVisible = false
            recordTxt.startAnimation(BlinkAnimation(500, 250))
            recordTxt.isVisible = true
        }
        Timber.d("Current key : $currentKey")
    }

    private fun deleteEntry(item: Pair<String, String>) {
        val mappings = Settings.readerCustomKeys.toMutableMap()
        if (mappings.containsKey(item.first)) mappings.remove(item.first)
        Settings.readerCustomKeys = mappings
        updateList(mappings)
    }

    private fun updateList(mappings: Map<String, String>) {
        itemAdapter.clear()
        if (mappings.isEmpty()) {
            itemAdapter.add(
                TextItem(
                    resources.getString(R.string.pref_viewer_key_bindings_no_binding),
                    Pair("", "")
                )
            )
        } else {
            for (mapping in mappings) {
                val keyLbl =
                    if (isNumeric(mapping.key)) actionLabels[mapping.key.toInt()]
                    else mapping.key
                val label = "$keyLbl > ${mapping.value}"
                itemAdapter.add(
                    TextItem(
                        label,
                        Pair(mapping.key, mapping.value),
                        selectable = true
                    )
                )
            }
        }
    }

    private fun onItemClick(item: TextItem<Pair<String, String>>): Boolean {
        val value = item.getObject() ?: return false
        val powerMenu = PowerMenu.Builder(this)
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.remove_generic),
                    false,
                    R.drawable.ic_action_delete
                )
            )
            .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
            .setMenuRadius(10f)
            .setLifecycleOwner(this)
            .setTextColor(ContextCompat.getColor(this, R.color.white_opacity_87))
            .setTextTypeface(Typeface.DEFAULT)
            .setMenuColor(this.getThemedColor(R.color.subbar_1_light))
            .setTextSize(dimensAsDp(this, R.dimen.text_subtitle_1))
            .setAutoDismiss(true)
            .build()
        powerMenu.onMenuItemClickListener = OnMenuItemClickListener { p, _ ->
            if (0 == p) {
                deleteEntry(value)
            }
        }
        powerMenu.setIconColor(ContextCompat.getColor(this, R.color.white_opacity_87))
        powerMenu.showAtCenter(binding?.root)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyEvent.ACTION_DOWN == event.action) {
            Timber.v("ACTIVITY key down ${event.keyCode}")
            onInputEvent("key", event.keyCode.toString())
            return true
        } else {
            Timber.v("ACTIVITY unhandled key ${event.keyCode}")
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        decodeMotionEvent(event)?.let { me ->
            onInputEvent(me.first, me.second)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
package me.devsaki.hentoid.activities.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.activities.bundles.SettingsBundle
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.fragments.settings.SettingsFragment
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.useLegacyInsets
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SettingsActivity : BaseActivity(), SearchPreferenceResultListener {

    private lateinit var fragment: SettingsFragment
    private lateinit var site: Site

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useLegacyInsets()
        var rootKey: String? = null
        when {
            isViewerPrefs() -> rootKey = "viewer"
            isBrowserPrefs() -> rootKey = "browser"
            isDownloaderPrefs() -> rootKey = "downloader"
            isStoragePrefs() -> rootKey = "storage"
        }
        site = getSiteFromIntent()
        fragment = SettingsFragment.newInstance(rootKey, site)
        supportFragmentManager.commit {
            replace(android.R.id.content, fragment)
        }

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    private fun isViewerPrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = SettingsBundle(intent.extras!!)
            parser.isViewerSettings
        } else false
    }

    private fun isBrowserPrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = SettingsBundle(intent.extras!!)
            parser.isBrowserSettings
        } else false
    }

    private fun isDownloaderPrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = SettingsBundle(intent.extras!!)
            parser.isDownloaderSettings
        } else false
    }

    private fun isStoragePrefs(): Boolean {
        return if (intent.extras != null) {
            val parser = SettingsBundle(intent.extras!!)
            parser.isStorageSettings
        } else false
    }

    private fun getSiteFromIntent(): Site {
        return if (intent.extras != null) {
            val parser = SettingsBundle(intent.extras!!)
            Site.searchByCode(parser.site)
        } else Site.NONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.SETTINGS || event.type != CommunicationEvent.Type.BROADCAST || event.message.isEmpty()) return
        // Make sure current activity is active (=eligible to display that toast)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        toast(event.message)
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        if (result.screen != null)
            fragment = fragment.navigateToScreen(supportFragmentManager, result.screen)
        fragment.view?.fitsSystemWindows = true
        result.highlight(fragment)
    }
}
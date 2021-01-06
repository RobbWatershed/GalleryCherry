package me.devsaki.hentoid.activities

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.ActivityAboutBinding
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.fragments.about.ChangelogFragment
import me.devsaki.hentoid.fragments.about.LicensesFragment
import me.devsaki.hentoid.util.Consts
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.startBrowserActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AboutActivity : BaseActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        binding.appLogo.setOnClickListener { startBrowserActivity(Consts.URL_GITHUB_WIKI) }
        binding.githubText.setOnClickListener { startBrowserActivity(Consts.URL_GITHUB) }
        binding.discordText.setOnClickListener { startBrowserActivity(Consts.URL_DISCORD) }

        binding.tvVersionName.text = getString(R.string.about_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.tvChromeVersionName.text = getString(R.string.about_chrome_version, HttpHelper.getChromeVersion())

        binding.changelogButton.setOnClickListener { showFragment(ChangelogFragment()) }

        binding.licensesButton.setOnClickListener { showFragment(LicensesFragment()) }

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            add(android.R.id.content, fragment)
            addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.hasNewVersion) binding.changelogButton.setText(R.string.view_changelog_flagged)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
    }
}
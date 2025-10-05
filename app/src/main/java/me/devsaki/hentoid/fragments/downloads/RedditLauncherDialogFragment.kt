package me.devsaki.hentoid.fragments.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import me.devsaki.hentoid.R
import me.devsaki.hentoid.adapters.RedditTabsAdapter
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.OAuthSessionManager
import java.time.Instant

private const val DEFAULT_URL = "/r/nsfw"

class RedditLauncherDialogFragmentK : DialogFragment() {
    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = RedditLauncherDialogFragmentK()
            fragment.show(fragmentManager, null)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_reddit_launcher, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager: ViewPager = ViewCompat.requireViewById(view, R.id.reddit_launcher_pager)
        val tabs: TabLayout = ViewCompat.requireViewById(view, R.id.reddit_launcher_tabs)

        val redditDownloadFragment: Fragment?
        val session = OAuthSessionManager.getSessionBySite(Site.REDDIT)
        redditDownloadFragment =
            if (session != null && session.expiry.isAfter(Instant.now()))
                RedditAuthDownloadFragment.newInstance()
            else RedditNoAuthDownloadFragment.newInstance()

        val redditTabsAdapter = RedditTabsAdapter(getChildFragmentManager())
        redditTabsAdapter.addTabFragment(
            LandingHistoryFragmentK.newInstance(
                requireActivity(),
                Site.REDDIT,
                DEFAULT_URL
            ), "Browse"
        )
        redditTabsAdapter.addTabFragment(redditDownloadFragment, "Download")
        viewPager.setAdapter(redditTabsAdapter)

        tabs.setupWithViewPager(viewPager)

        tabs.getTabAt(0)?.setIcon(R.drawable.ic_chrono)
        tabs.getTabAt(1)?.setIcon(R.drawable.ic_action_download)
    }
}
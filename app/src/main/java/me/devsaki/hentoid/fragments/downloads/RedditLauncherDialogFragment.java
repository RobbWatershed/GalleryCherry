package me.devsaki.hentoid.fragments.downloads;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.RedditTabsAdapter;
import me.devsaki.hentoid.enums.Site;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 09/2019
 * Launcher dialog for landing page history
 */
public class RedditLauncherDialogFragment extends DialogFragment {

    private static final String DEFAULT_URL = "/r/nsfw";


    public static void invoke(FragmentManager fragmentManager) {
        RedditLauncherDialogFragment fragment = new RedditLauncherDialogFragment();
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_reddit_launcher, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewPager viewPager = requireViewById(view, R.id.reddit_launcher_pager);
        TabLayout tabs = requireViewById(view, R.id.reddit_launcher_tabs);

        RedditTabsAdapter redditTabsAdapter = new RedditTabsAdapter(getChildFragmentManager());
        redditTabsAdapter.addTabFragment(LandingHistoryFragment.newInstance(Site.REDDIT, DEFAULT_URL), "Browse");
        redditTabsAdapter.addTabFragment(RedditDownloadFragment.newInstance(), "Download");
        viewPager.setAdapter(redditTabsAdapter);

        tabs.setupWithViewPager(viewPager);

        tabs.getTabAt(0).setIcon(R.drawable.ic_menu_sort_last_read);
        tabs.getTabAt(1).setIcon(R.drawable.ic_action_download);
    }
}

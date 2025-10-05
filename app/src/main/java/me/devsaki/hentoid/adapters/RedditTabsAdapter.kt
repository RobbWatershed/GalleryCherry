package me.devsaki.hentoid.adapters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.List;

public class RedditTabsAdapter extends FragmentPagerAdapter {

    private final List<TabFragmentInfo> tabFragments = new ArrayList<>();


    public RedditTabsAdapter(@NonNull FragmentManager fm) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    public void addTabFragment(Fragment f, String title) {
        tabFragments.add(new TabFragmentInfo(f, title));
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return tabFragments.get(position).getFragment();
    }

    @Override
    public int getCount() {
        return tabFragments.size();
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return tabFragments.get(position).getTitle();
    }

    public class TabFragmentInfo {
        private final Fragment fragment;
        private final String title;

        TabFragmentInfo(Fragment f, String title) {
            this.fragment = f;
            this.title = title;
        }

        public Fragment getFragment() {
            return fragment;
        }

        public String getTitle() {
            return title;
        }
    }
}

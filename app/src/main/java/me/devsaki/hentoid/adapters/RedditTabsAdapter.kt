package me.devsaki.hentoid.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class RedditTabsAdapter(val fm: FragmentManager) :
    FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    private val tabFragments: MutableList<TabFragmentInfo> = ArrayList<TabFragmentInfo>()


    fun addTabFragment(f: Fragment, title: String) {
        tabFragments.add(TabFragmentInfo(f, title))
    }

    override fun getItem(position: Int): Fragment {
        return tabFragments[position].fragment
    }

    override fun getCount(): Int {
        return tabFragments.size
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return tabFragments[position].title
    }

    class TabFragmentInfo internal constructor(val fragment: Fragment, val title: String)
}
package me.devsaki.hentoid.enums;

import androidx.appcompat.app.AppCompatActivity;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.sources.HellpornoActivity;
import me.devsaki.hentoid.activities.sources.JpegworldActivity;
import me.devsaki.hentoid.activities.sources.Link2GalleriesActivity;
import me.devsaki.hentoid.activities.sources.NextpicturezActivity;
import me.devsaki.hentoid.activities.sources.PornPicGalleriesActivity;
import me.devsaki.hentoid.activities.sources.PornPicsActivity;
import me.devsaki.hentoid.activities.sources.XhamsterActivity;
import me.devsaki.hentoid.activities.sources.XnxxActivity;

public enum DrawerItem {

    XHAMSTER("XHAMSTER", R.drawable.ic_menu_xhamster, XhamsterActivity.class),
    XNXX("XNXX", R.drawable.ic_menu_xnxx, XnxxActivity.class),
    PORNPICS("PORNPICS", R.drawable.ic_menu_pornpics, PornPicsActivity.class),
    JPEGWORLD("JPEGWORLD", R.drawable.ic_menu_jpegworld, JpegworldActivity.class),
    NEXTPICTUREZ("NEXTPICTUREZ", R.drawable.ic_menu_nextpicturez, NextpicturezActivity.class),
    HELLPORNO("HELLPORNO", R.drawable.ic_menu_hellporno, HellpornoActivity.class),
    PPG("PORNPICGALLERIES", R.drawable.ic_menu_ppg, PornPicGalleriesActivity.class),
    LINK2GALLERIES("LINK2GALLERIES", R.drawable.ic_menu_l2g, Link2GalleriesActivity.class),
    QUEUE("QUEUE", R.drawable.ic_menu_queue, QueueActivity.class),
    PREFS("PREFERENCES", R.drawable.ic_menu_prefs, PrefsActivity.class),
    ABOUT("ABOUT", R.drawable.ic_menu_about, AboutActivity.class);

    public final String label;
    public final int icon;
    public final Class<? extends AppCompatActivity> activityClass;

    DrawerItem(String label, int icon, Class<? extends AppCompatActivity> activityClass) {
        this.label = label;
        this.icon = icon;
        this.activityClass = activityClass;
    }
}

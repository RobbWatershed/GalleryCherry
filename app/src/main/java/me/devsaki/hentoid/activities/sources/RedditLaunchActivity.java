package me.devsaki.hentoid.activities.sources;

import android.os.Bundle;

import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.fragments.downloads.RedditLauncherDialogFragment;

/**
 * Created by Robb on 09/2019
 * Landing page history launcher for Reddit
 */
public class RedditLaunchActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RedditLauncherDialogFragment.invoke(getSupportFragmentManager());
    }
}
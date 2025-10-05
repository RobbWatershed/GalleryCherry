package me.devsaki.hentoid.activities.sources;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import me.devsaki.hentoid.fragments.downloads.RedditLauncherDialogFragment;

/**
 * Created by Robb on 09/2019
 * Landing page history launcher for Reddit
 */
public class RedditLaunchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RedditLauncherDialogFragment.invoke(getSupportFragmentManager());
    }
}
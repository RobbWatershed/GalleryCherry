package me.devsaki.hentoid.activities.sources

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.devsaki.hentoid.fragments.downloads.RedditLauncherDialogFragmentK

/**
 * Landing page history launcher for Reddit
 */
class RedditLaunchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RedditLauncherDialogFragmentK.invoke(supportFragmentManager)
    }
}
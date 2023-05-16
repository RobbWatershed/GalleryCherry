package me.devsaki.hentoid.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.devsaki.hentoid.core.AppStartup
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.ContentHelper
import timber.log.Timber


/**
 * Resole intents and send them where appropriate
 * <p>
 * Manages how the app receives a "share" intent
 * e.g. Click a link on reddit - it opens in my browser but I wanna download it in Hentoid
 * => tap share in the browser and select hentoid; that's when IntentActivity takes the lead
 * <p>
 * NB : This activity is transparent and not lockable; it should _not_ be a child of BaseActivity
 */
class IntentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStartup().initApp(this,
            { f -> onMainProgress(f) },
            { f -> onSecondaryProgress(f) }
        ) { onInitComplete() }
    }

    private fun onMainProgress(f: Float) {
        Timber.i("Init @ IntentActivity (main) : %s%%", f)
    }

    private fun onSecondaryProgress(f: Float) {
        Timber.i("Init @ IntentActivity (secondary) : %s%%", f)
    }

    private fun onInitComplete() {
        val action = intent.action
        val data = intent.data
        if (Intent.ACTION_VIEW == action && data != null) processIntent(data)
        else if (Intent.ACTION_SEND == action && intent.hasExtra(Intent.EXTRA_TEXT)) {
            processIntent(Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT)))
        } else {
            Timber.d("Unrecognized intent. Cannot process.")
        }
        finish()
    }

    private fun processIntent(data: Uri) {
        Timber.d("Uri: %s", data)
        val site = Site.searchByUrl(data.toString())
        if (site == null) {
            Timber.d("Invalid URL")
            return
        }
        if (site == Site.NONE) {
            Timber.d("Unrecognized host : %s", data.host)
            return
        }
        var parsedPath = parsePath(site, data)
        if (parsedPath == null) {
            Timber.d("Cannot parse path")
            return
        }

        // Cleanup double /'s
        if (site.url.endsWith("/") && parsedPath.startsWith("/") && parsedPath.length > 1)
            parsedPath = parsedPath.substring(1)
        val content = Content()
        content.site = site
        content.url = parsedPath
        ContentHelper.viewContentGalleryPage(this, content, true)
    }

    private fun parsePath(site: Site, data: Uri): String? {
        val toParse: String = data.path ?: return null

        return when (site) {
            Site.XHAMSTER -> {
                val galleryIndex = toParse.indexOf("/gallery/")
                toParse.substring(galleryIndex + 9)
            }

            else -> toParse
        }
    }
}
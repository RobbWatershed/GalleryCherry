package me.devsaki.hentoid.activities

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.reader.ReaderGalleryFragment
import me.devsaki.hentoid.fragments.reader.ReaderPagerFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.checkPermission
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.viewmodels.ReaderViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.ReaderKeyListener
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds


open class ReaderActivity : BaseActivity() {
    private var readerKeyListener: ReaderKeyListener? = null
    private lateinit var viewModel: ReaderViewModel

    private var bookPreferences: Map<String, String> = emptyMap()
    private var bookSite: Site = Site.NONE

    // Ask for permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.i("Read external storage permission granted")
            lifecycleScope.launch(Dispatchers.Main) {
                delay(200.milliseconds)
                recreate()
            }
        } else {
            toast(R.string.storage_permission_denied)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.isReaderKeepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
        viewModel.observeDbImages(this)
        viewModel.getContent().observe(this) {
            bookSite = it?.site ?: Site.NONE
            bookPreferences = it?.bookPreferences ?: emptyMap()
        }

        val intent = intent
        require(!(null == intent || null == intent.extras)) { "Required init arguments not found" }
        val parser = ReaderActivityBundle(intent.extras!!)

        if (parser.isOpenFavPages) {
            // ViewModel hasn't loaded anything yet (fresh start)
            if (null == viewModel.getContent().value) viewModel.loadFavPages()
        } else if (parser.isOpenFolders) {
            parser.folderSearchParams?.let { params ->
                parser.docUri?.let { uri ->
                    viewModel.loadContentFromFolderSearch(uri, params)
                }
            }
        } else {
            val contentId = parser.contentId
            require(0L != contentId) { "Incorrect ContentId" }
            val pageNumber = parser.pageNumber
            // ViewModel hasn't loaded anything yet (fresh start)
            if (null == viewModel.getContent().value) {
                val searchParams = parser.contentSearchParams
                if (searchParams != null) viewModel.loadContentFromContentSearch(
                    contentId,
                    pageNumber,
                    searchParams
                ) else viewModel.loadContentFromId(contentId, pageNumber)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            && !checkPermission(READ_EXTERNAL_STORAGE)
        ) {
            requestPermissionLauncher.launch(READ_EXTERNAL_STORAGE)
            return
        }

        // Allows a full recolor of the status bar with the custom color defined in the activity's theme
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.black_opacity_50)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.black_opacity_50)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        if (!Settings.recentVisibility) window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setRunning(true)

        if (null == savedInstanceState) {
            lifecycleScope.launch(Dispatchers.Main) {
                withContext(Dispatchers.Default) {
                    var remainingIterations = 10 // Timeout 500ms
                    while (Site.NONE == bookSite && remainingIterations-- > 0) pause(50)
                }

                val fragment = if (parser.isForceShowGallery ||
                    Settings.isContentOpenInGalleryMode(bookSite, bookPreferences)
                ) ReaderGalleryFragment()
                else ReaderPagerFragment()

                supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (readerKeyListener != null) readerKeyListener!!.onKey(keyCode, event)
        else super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return if (readerKeyListener != null) readerKeyListener!!.onMotionEvent(event)
        else super.onGenericMotionEvent(event)
    }

    override fun onStop() {
        if (isFinishing) { // i.e. the activity is closing for good; not being paused / backgrounded
            viewModel.onActivityLeave()
            Settings.readerDeleteAskMode = Settings.Value.VIEWER_DELETE_ASK_AGAIN
            Settings.readerCurrentContent = -1
            setRunning(false)
        }
        super.onStop()
    }

    fun registerKeyListener(listener: ReaderKeyListener) {
        takeKeyEvents(true)
        readerKeyListener = listener
    }

    fun unregisterKeyListener() {
        readerKeyListener?.clear()
        readerKeyListener = null
    }

    companion object {
        private var isRunning = false

        @Synchronized
        private fun setRunning(value: Boolean) {
            isRunning = value
        }

        @Synchronized
        fun isRunning(): Boolean {
            return isRunning
        }
    }

    class ReaderActivityMulti : ReaderActivity() {
        // Only exists to be able to launch ReaderActivity without launchMode=singleTask
    }
}
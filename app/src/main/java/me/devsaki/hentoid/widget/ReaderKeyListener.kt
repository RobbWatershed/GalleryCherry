package me.devsaki.hentoid.widget

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlinx.coroutines.CoroutineScope
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.decodeMotionEvent

const val COOLDOWN = 1000
const val TURBO_COOLDOWN = 500

class ReaderKeyListener(scope: CoroutineScope) {

    // Hardcoded (material) mappings
    private var onVolumeDownListener: Consumer<Boolean>? = null
    private var onVolumeUpListener: Consumer<Boolean>? = null
    private var onKeyLeftListener: Consumer<Boolean>? = null
    private var onKeyRightListener: Consumer<Boolean>? = null

    private var onBackListener: Consumer<Boolean>? = null


    // Custom (feature) mappings
    private var onPreviousChapterBook: Consumer<Boolean>? = null
    private var onNextChapterBook: Consumer<Boolean>? = null
    private var onPreviousPage: Consumer<Boolean>? = null
    private var onNextPage: Consumer<Boolean>? = null


    // Internal variables
    private var nextNotifyTime = Long.MAX_VALUE
    private val simpleTapDebouncer: Debouncer<Consumer<Boolean>>
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()

    init {
        simpleTapDebouncer =
            Debouncer(scope, longPressTimeout.toLong()) { consumer: Consumer<Boolean> ->
                consumer.invoke(false)
            }
    }

    fun setOnVolumeDownListener(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onVolumeDownListener = listener
        return this
    }

    fun setOnVolumeUpListener(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onVolumeUpListener = listener
        return this
    }

    fun setOnKeyLeftListener(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onKeyLeftListener = listener
        return this
    }

    fun setOnKeyRightListener(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onKeyRightListener = listener
        return this
    }

    fun setOnBackListener(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onBackListener = listener
        return this
    }


    fun setOnPreviousChapterBook(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onPreviousChapterBook = listener
        return this
    }

    fun setOnNextChapterBook(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onNextChapterBook = listener
        return this
    }

    fun setOnPreviousPage(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onPreviousPage = listener
        return this
    }

    fun setOnNextPage(listener: Consumer<Boolean>?): ReaderKeyListener {
        this.onNextPage = listener
        return this
    }


    private fun isTurboEnabled(): Boolean {
        return !Settings.isReaderVolumeToSwitchBooks
    }

    private fun isDetectLongPress(): Boolean {
        return Settings.isReaderVolumeToSwitchBooks
    }

    private fun isVolumeKey(keyCode: Int, targetKeyCode: Int): Boolean {
        // Ignore volume keys when disabled in preferences
        if (!Settings.isReaderVolumeToTurn) return false
        if (Settings.isReaderInvertVolumeRocker) {
            if (targetKeyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return keyCode == KeyEvent.KEYCODE_VOLUME_UP else if (targetKeyCode == KeyEvent.KEYCODE_VOLUME_UP) return keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        }
        return keyCode == targetKeyCode
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        val me = decodeMotionEvent(event) ?: return false
        val listener = testCustomEvents(me) ?: return false
        listener.invoke(true)
        return true
    }

    private fun testCustomEvents(me: Pair<String, String>): Consumer<Boolean>? {
        val customKeys = Settings.readerCustomKeys
        customKeys.forEach { (key, value) ->
            if (value == "${me.first}|${me.second}") {
                return when (key) {
                    "0" -> onPreviousChapterBook
                    "1" -> onNextChapterBook
                    "2" -> onPreviousPage
                    "3" -> onNextPage
                    else -> null
                }
            }
        }
        return null
    }

    fun onKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        var listener = testHardcodedKeys(keyCode)
        if (null == listener) listener = testCustomKeys(keyCode)

        if (null == listener) return false
        if (event.repeatCount == 0) { // Simple down
            nextNotifyTime = if (isDetectLongPress()) {
                simpleTapDebouncer.submit(listener)
                event.eventTime + longPressTimeout
            } else {
                listener.invoke(false)
                event.eventTime + COOLDOWN
            }
        } else if (event.eventTime >= nextNotifyTime) { // Long down
            simpleTapDebouncer.clear()
            listener.invoke(true)
            nextNotifyTime = event.eventTime + if (isTurboEnabled()) TURBO_COOLDOWN else COOLDOWN
        }
        return true
    }

    private fun testHardcodedKeys(keyCode: Int): Consumer<Boolean>? {
        return if (isVolumeKey(keyCode, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            onVolumeDownListener
        } else if (isVolumeKey(keyCode, KeyEvent.KEYCODE_VOLUME_UP)) {
            onVolumeUpListener
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && Settings.isReaderKeyboardToTurn) {
            onKeyLeftListener
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && Settings.isReaderKeyboardToTurn) {
            onKeyRightListener
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackListener
        } else {
            null
        }
    }

    private fun testCustomKeys(keyCode: Int): Consumer<Boolean>? {
        val customKeys = Settings.readerCustomKeys.filterValues { it.startsWith("key|") }
        if (customKeys.isEmpty()) return null

        customKeys.forEach { (key, value) ->
            val code = value.substringAfter('|')
            if (code == keyCode.toString()) {
                return when (key) {
                    "0" -> onPreviousChapterBook
                    "1" -> onNextChapterBook
                    "2" -> onPreviousPage
                    "3" -> onNextPage
                    else -> null
                }
            }
        }
        return null
    }

    fun clear() {
        onVolumeDownListener = null
        onVolumeUpListener = null
        onKeyLeftListener = null
        onKeyRightListener = null
        onBackListener = null
        onPreviousChapterBook = null
        onNextChapterBook = null
        onPreviousPage = null
        onNextPage = null
        simpleTapDebouncer.clear()
    }
}
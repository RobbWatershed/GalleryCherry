package me.devsaki.hentoid.views

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.lifecycleScope
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.util.Debouncer

/**
 * WebView implementation which allows setting arbitrary thresholds long clicks
 */
open class VariableLongClickWebView : WebView {
    // The minimum duration to hold down a click to register as a long click (in ms).
    // Default is 500ms (This is the same as the android system's default).
    private var longClickThreshold = 500

    private var onLongClickListener: BiConsumer<Int, Int>? = null

    private lateinit var longTapDebouncer: Debouncer<Point>

    constructor(context: Context) : super(context) {
        init(longClickThreshold, context as AppCompatActivity)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(longClickThreshold, context as AppCompatActivity)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(longClickThreshold, context as AppCompatActivity)
    }

    private fun init(longTapDebouncerThreshold: Int, activity: AppCompatActivity) {
        longTapDebouncer =
            Debouncer(activity.lifecycleScope, longTapDebouncerThreshold.toLong())
            { onLongClickListener?.invoke(it.x, it.y) }
    }

    fun setOnLongTapListener(onLongClickListener: BiConsumer<Int, Int>?) {
        this.onLongClickListener = onLongClickListener
    }

    fun setLongClickThreshold(threshold: Int) {
        longClickThreshold = threshold
        init(threshold, context as AppCompatActivity)
    }

    // Non-mouse events
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                longTapDebouncer.submit(Point(event.x.toInt(), event.y.toInt()))

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP ->
                longTapDebouncer.clear()

            else -> {  /* Nothing */
            }
        }
        return super.onTouchEvent(event)
    }

    // Mouse events
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                // Right click
                if (MotionEvent.BUTTON_SECONDARY == event.actionButton)
                    onLongClickListener?.invoke(event.x.toInt(), event.y.toInt())
            }

            else -> { /* Nothing */
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onCreateInputConnection(info: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(info)
        info.imeOptions = info.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        return connection
    }
}
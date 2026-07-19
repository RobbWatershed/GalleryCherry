package me.devsaki.hentoid.util.video

import android.content.Context
import com.github.penfeizhou.animation.decode.Frame
import java.io.Closeable

interface FrameStreamer : Closeable {
    fun streamFrames(
        onFrameFound: (Frame<*, *>) -> Unit,
        isCanceled: () -> Boolean
    )
}
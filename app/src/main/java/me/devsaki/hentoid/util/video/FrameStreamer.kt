package me.devsaki.hentoid.util.video

import android.graphics.Bitmap
import android.graphics.Point
import java.io.Closeable

interface FrameStreamer : Closeable {
    suspend fun streamFrames(
        isCanceled: () -> Boolean,
        onFrameFound: suspend (Pair<Bitmap, Int>) -> Unit
    )

    val dims: Point
    val totalFrames: Int
    val durationMs: Int
}
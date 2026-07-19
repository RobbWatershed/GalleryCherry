package me.devsaki.hentoid.util.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.Closeable

interface AnimationEncoder : Closeable {
    suspend fun init(context: Context, outUri: Uri)

    suspend fun encode(
        context: Context,
        frames: List<Pair<Uri, Int>>,
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)? = null
    )

    suspend fun addFrame(
        bitmap: Bitmap,
        durationMs: Int
    )
}
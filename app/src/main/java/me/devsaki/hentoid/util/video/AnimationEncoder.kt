package me.devsaki.hentoid.util.video

import android.content.Context
import android.net.Uri
import java.io.Closeable

interface AnimationEncoder : Closeable {
    suspend fun encode(
        context: Context,
        outUri: Uri,
        frames: List<Pair<Uri, Int>>,
        quality: Float,
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)? = null)
}
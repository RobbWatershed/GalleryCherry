package me.devsaki.hentoid.util.video

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.DelicateCoroutinesApi
import me.devsaki.hentoid.util.image.loadBitmap
import me.devsaki.hentoid.webp_encoder.WebpBitmapEncoder
import timber.log.Timber
import kotlin.math.roundToInt
import kotlin.use

class WebpEncoder : AnimationEncoder {

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun encode(
        context: Context,
        outUri: Uri,
        frames: List<Pair<Uri, Int>>,
        quality: Float,
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)?
    ) {
        require(frames.isNotEmpty()) { "No frames given" }
        require(!isCanceled.invoke())
        WebpBitmapEncoder(outUri, context.contentResolver).use { encoder ->
            encoder.setLoops(0)
            frames.forEachIndexed { index, frame ->
                if (isCanceled.invoke()) return@forEachIndexed
                encoder.setDuration(frame.second)
                loadBitmap(context, frame.first)?.let { bitmap ->
                    try {
                        encoder.writeFrame(bitmap, (quality * 100).roundToInt())
                    } finally {
                        bitmap.recycle()
                    }
                } ?: run {
                    Timber.w("Cannot open ${frame.first}")
                }
                if (0 == index % 10) {
                    onProgress?.invoke(index * 1f / frames.size)
                }
            }
        }
    }

    override fun close() {
        // Nothing here
    }
}
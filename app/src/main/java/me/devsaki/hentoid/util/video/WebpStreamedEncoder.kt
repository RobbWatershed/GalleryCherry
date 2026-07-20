package me.devsaki.hentoid.util.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.image.loadBitmap
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.webp_encoder.WebpBitmapEncoder
import timber.log.Timber
import java.io.OutputStream
import kotlin.math.roundToInt

class WebpStreamedEncoder(val quality: Float, val frameDurationMs: Int) : AnimationEncoder {

    lateinit var outputStream: OutputStream
    lateinit var encoder: WebpBitmapEncoder

    override suspend fun init(context: Context, outUri: Uri) {
        outputStream =
            getOutputStream(context, outUri) ?: throw RuntimeException("Can't open $outUri")
        encoder = WebpBitmapEncoder(outUri, context.contentResolver)
        encoder.setLoops(0) // Infinite looping
        encoder.setDuration(frameDurationMs)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun encode(
        context: Context,
        frames: List<Pair<Uri, Int>>,
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)?
    ) {
        frames.forEachIndexed { index, frame ->
            if (isCanceled.invoke()) return@forEachIndexed
            loadBitmap(context, frame.first)?.let { bitmap ->
                try {
                    addFrame(bitmap, 0)
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

    override suspend fun addFrame(bitmap: Bitmap, durationMs: Int) = withContext(Dispatchers.IO) {
        encoder.writeFrame(bitmap, (quality * 100).roundToInt())
    }

    override fun close() {
        // Use extra second to finalize all that might be still happening on other threads
        pause(1000)
        Timber.d("Closing WebpStreamedEncoder")
        encoder.close()
        outputStream.close()
    }
}
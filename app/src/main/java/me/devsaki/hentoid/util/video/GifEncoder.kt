package me.devsaki.hentoid.util.video

import android.content.Context
import android.net.Uri
import com.shakster.gifkt.GifEncoder
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.image.getMediaDimensions
import me.devsaki.hentoid.util.image.loadBitmap
import timber.log.Timber
import java.io.Closeable
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.use

class GifEncoder : Closeable {
    /**
     * Create GIF file by assembling the given files into frames
     *
     * @param frames Frames : first = Frame file Uri; second = Frame duration (ms)
     */
    suspend fun encode(
        context: Context,
        outUri: Uri,
        frames: List<Pair<Uri, Int>>,
        quality: Float, // Unused for GIF
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)?
    ) {
        require(frames.isNotEmpty()) { "No frames given" }
        require(!isCanceled.invoke())
        val dims = getMediaDimensions(context, frames[0].first.toString())
        val buffer = IntArray(dims.x * dims.y)

        getOutputStream(context, outUri)?.use { out ->
            val gifEncoderBuilder = GifEncoder.builder(out)
            gifEncoderBuilder.minimumFrameDurationCentiseconds = 1

            val gifEncoder = gifEncoderBuilder.build { framesWritten, writtenDuration ->
                Timber.d("framesWritten=$framesWritten writtenDuration=$writtenDuration")
            }
            gifEncoder.use {
                frames.forEachIndexed { idx, frame ->
                    if (isCanceled.invoke()) return@forEachIndexed
                    Timber.d("encoding frame $idx [duration ${frame.second} ms]")
                    loadBitmap(context, frame.first)?.let { bitmap ->
                        bitmap.getPixels(buffer, 0, dims.x, 0, 0, dims.x, dims.y)
                        try {
                            gifEncoder.writeFrame(
                                buffer,
                                dims.x,
                                dims.y,
                                // Warning : if frame.second is <= 1ms, GIFs will be read slower on most readers
                                // (see https://android.googlesource.com/platform/frameworks/base/+/2be87bb707e2c6d75f668c4aff6697b85fbf5b15)
                                frame.second.toDuration(DurationUnit.MILLISECONDS)
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    onProgress?.invoke(idx / frames.size.toFloat())
                }
            }
        }
    }

    override fun close() {
        // Nothing to do here
    }
}
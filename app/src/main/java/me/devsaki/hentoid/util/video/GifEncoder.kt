package me.devsaki.hentoid.util.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import com.shakster.gifkt.GifEncoder
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.image.loadBitmap
import me.devsaki.hentoid.util.pause
import timber.log.Timber
import java.io.OutputStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class GifEncoder(val dims: Point) : AnimationEncoder {

    private var buffer = IntArray(dims.x * dims.y)
    private lateinit var outputStream: OutputStream
    private lateinit var encoder: GifEncoder

    override suspend fun init(context: Context, outUri: Uri) {
        outputStream =
            getOutputStream(context, outUri) ?: throw RuntimeException("Can't open $outUri")
        val gifEncoderBuilder = GifEncoder.builder(outputStream)
        gifEncoderBuilder.minimumFrameDurationCentiseconds = 1
        encoder = gifEncoderBuilder.build { framesWritten, writtenDuration ->
            Timber.d("framesWritten=$framesWritten writtenDuration=$writtenDuration")
        }
    }

    override fun addFrame(bitmap: Bitmap, durationMs: Int) {
        synchronized(buffer) {
            bitmap.getPixels(buffer, 0, dims.x, 0, 0, dims.x, dims.y)
            encoder.writeFrame(
                buffer,
                dims.x,
                dims.y,
                // Warning : if frame.second is <= 1ms, GIFs will be read slower on most readers
                // (see https://android.googlesource.com/platform/frameworks/base/+/2be87bb707e2c6d75f668c4aff6697b85fbf5b15)
                durationMs.toDuration(DurationUnit.MILLISECONDS)
            )
        }
    }

    /**
     * Create GIF file by assembling the given files into frames
     *
     * @param frames Frames : first = Frame file Uri; second = Frame duration (ms)
     */
    override suspend fun encode(
        context: Context,
        frames: List<Pair<Uri, Int>>,
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)?
    ) {
        frames.forEachIndexed { idx, frame ->
            if (isCanceled.invoke()) return@forEachIndexed
            Timber.d("encoding frame $idx [duration ${frame.second} ms]")
            loadBitmap(context, frame.first)?.let { bitmap ->
                try {
                    addFrame(bitmap, frame.second)
                } finally {
                    bitmap.recycle()
                }
            }
            if (0 == idx % 10) {
                onProgress?.invoke(idx * 1f / frames.size)
            }
        }
    }

    override fun close() {
        Timber.d("Closing GifEncoder")
        encoder.close()
        outputStream.close()
    }
}
package me.devsaki.hentoid.util.video

import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import androidx.core.graphics.createBitmap
import com.github.penfeizhou.animation.apng.decode.APNGDecoder
import com.github.penfeizhou.animation.avif.decode.AVIFDecoder
import com.github.penfeizhou.animation.decode.FrameSeqDecoder
import com.github.penfeizhou.animation.gif.decode.GifDecoder
import com.github.penfeizhou.animation.io.FilterReader
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.StreamReader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.loader.Loader
import com.github.penfeizhou.animation.webp.decode.WebPDecoder
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.util.file.fileExists
import me.devsaki.hentoid.util.image.MIME_IMAGE_APNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_AVIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_GIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_PNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_WEBP
import me.devsaki.hentoid.util.pause
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.roundToInt


fun getPenfeiFrameStreamer(uri: Uri, mime: String): PenfeiFrameStreamer<*, *>? {
    val frameSeqDecoder = when (mime) {
        MIME_IMAGE_APNG, MIME_IMAGE_PNG -> APNGDecoder(ImgLoader(uri), null)
        MIME_IMAGE_WEBP -> WebPDecoder(ImgLoader(uri), null)
        MIME_IMAGE_GIF -> GifDecoder(ImgLoader(uri), null)
        MIME_IMAGE_AVIF -> AVIFDecoder(ImgLoader(uri), null)
        else -> null
    } ?: return null
    return PenfeiFrameStreamer(frameSeqDecoder)
}

class PenfeiFrameStreamer<R : Reader, W : Writer>(val decoder: FrameSeqDecoder<R, W>) :
    FrameSeqDecoder.RenderListener {

    val dims: Point
    val sampleSize: Int
    val fps: Float
    val totalFrames: Int
    val durationMs: Int

    var framesRendered = 0
    var onFrameFound: ((Pair<Bitmap, Int>) -> Unit)? = null

    init {
        decoder.setLoopLimit(0) // Won't start if we don't do that
        decoder.addRenderListener(this)

        // Fetch constant metrics
        val rect = decoder.bounds
        dims = Point(rect.width(), rect.height())
        totalFrames = decoder.frameCount
        sampleSize = decoder.sampleSize
        var res = 0
        for (i in 0..<totalFrames) {
            res += decoder.getFrame(i).frameDuration
        }
        durationMs = res
        fps = totalFrames * 1000f / durationMs
    }

    fun streamFramesBlocking(
        isCanceled: () -> Boolean,
        onFrameFound: (Pair<Bitmap, Int>) -> Unit
    ) {
        this.onFrameFound = onFrameFound
        try {
            decoder.start()
            while (framesRendered < totalFrames && !isCanceled.invoke()) {
                pause(250)
            }
        } finally {
            decoder.stop()
        }
    }

    override fun onStart() {
        Timber.v("onStart")
        framesRendered = 0
    }

    override fun onRender(byteBuffer: ByteBuffer) {
        Timber.v("onFrame $framesRendered")
        val bitmap = createBitmap(dims.x / sampleSize, dims.y / sampleSize)
        byteBuffer.position(0) // Go back to the beginning to read it
        bitmap.copyPixelsFromBuffer(byteBuffer)
        onFrameFound?.invoke(Pair(bitmap, (durationMs * 1f / totalFrames).roundToInt()))
        framesRendered++
    }

    override fun onEnd() {
        Timber.v("onEnd")
    }
}

/**
 * Custom image loaders for APNG4Android to work with files located in SAF area
 *
 * https://github.com/penfeizhou/APNG4Android/issues/58
 */
class ImgLoader internal constructor(private val uri: Uri) : Loader {
    @Synchronized
    @Throws(IOException::class)
    override fun obtain(): Reader? {
        if (!fileExists(HentoidApp.getInstance().applicationContext, uri)) return null
        return ImgReader(uri)
    }
}

class ImgReader internal constructor(private val uri: Uri) : FilterReader(
    StreamReader(HentoidApp.getInstance().contentResolver.openInputStream(uri))
) {
    @Throws(IOException::class)
    override fun reset() {
        reader.close()
        reader = StreamReader(HentoidApp.getInstance().contentResolver.openInputStream(uri))
    }
}

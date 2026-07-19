package me.devsaki.hentoid.util.video

import android.graphics.Bitmap
import com.github.penfeizhou.animation.apng.decode.APNGDecoder
import com.github.penfeizhou.animation.avif.decode.AVIFDecoder
import com.github.penfeizhou.animation.decode.FrameSeqDecoder
import com.github.penfeizhou.animation.gif.decode.GifDecoder
import com.github.penfeizhou.animation.io.Reader
import com.github.penfeizhou.animation.io.Writer
import com.github.penfeizhou.animation.webp.decode.WebPDecoder
import me.devsaki.hentoid.util.image.MIME_IMAGE_APNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_AVIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_GIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_PNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_WEBP
import java.io.InputStream

fun getPenfeiFrameStreamer(mime: String, input: InputStream): PenfeiFrameStreamer<*, *>? {
    val frameSeqDecoder = when (mime) {
        MIME_IMAGE_APNG, MIME_IMAGE_PNG -> APNGDecoder(PenfeiStreamLoader(input), null)
        MIME_IMAGE_WEBP -> WebPDecoder(PenfeiStreamLoader(input), null)
        MIME_IMAGE_GIF -> GifDecoder(PenfeiStreamLoader(input), null)
        MIME_IMAGE_AVIF -> AVIFDecoder(PenfeiStreamLoader(input), null)
        else -> null
    } ?: return null
    return PenfeiFrameStreamer(frameSeqDecoder)
}

class PenfeiFrameStreamer<R : Reader, W : Writer>(val decoder: FrameSeqDecoder<R, W>) {
    fun streamFrames(
        isCanceled: () -> Boolean,
        onFrameFound: (Pair<Bitmap, Int>) -> Unit
    ) {
        try {
            val nbFrames = decoder.frameCount
            for (i in 0..<nbFrames) {
                if (isCanceled.invoke()) break
                onFrameFound.invoke(
                    Pair(
                        decoder.getFrameBitmap(i),
                        decoder.getFrame(i).frameDuration
                    )
                )
            }
        } finally {
            decoder.stop()
        }
    }
}
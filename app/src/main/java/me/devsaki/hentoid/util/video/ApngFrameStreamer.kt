package me.devsaki.hentoid.util.video

import android.content.Context
import android.net.Uri
import com.github.penfeizhou.animation.apng.decode.APNGDecoder
import com.github.penfeizhou.animation.apng.decode.APNGParser
import com.github.penfeizhou.animation.apng.io.APNGReader
import com.github.penfeizhou.animation.decode.Frame
import me.devsaki.hentoid.util.file.getInputStream
import java.io.Closeable

class ApngFrameStreamer(context: Context, val uri: Uri) : Closeable, FrameStreamer {

    val inputStream = getInputStream(context, uri)

    override fun streamFrames(
        onFrameFound: (Frame<*, *>) -> Unit,
        isCanceled: () -> Boolean
    ) {
        val decoder = APNGDecoder(
            PenfeiStreamLoader(inputStream), null
        )
        try {
            val nbFrames = decoder.frameCount
            for (i in 0..<nbFrames) {
                onFrameFound.invoke(decoder.getFrame(i))
            }
        } finally {
            decoder.stop()
        }
    }

    override fun close() {
        inputStream.close()
    }
}
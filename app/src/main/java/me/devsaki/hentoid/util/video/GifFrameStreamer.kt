package me.devsaki.hentoid.util.video

import android.content.Context
import android.net.Uri
import com.github.penfeizhou.animation.decode.Frame
import com.github.penfeizhou.animation.gif.decode.GifDecoder
import com.github.penfeizhou.animation.loader.StreamLoader
import me.devsaki.hentoid.util.file.getInputStream
import java.io.Closeable
import java.io.InputStream

class GifFrameStreamer(context: Context, val uri: Uri) : Closeable, FrameStreamer {

    val inputStream = getInputStream(context, uri)

    override fun streamFrames(
        onFrameFound: (Frame<*, *>) -> Unit,
        isCanceled: () -> Boolean
    ) {
        val decoder = GifDecoder(
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

class PenfeiStreamLoader(val input: InputStream) : StreamLoader() {
    override fun getInputStream(): InputStream {
        return input
    }
}
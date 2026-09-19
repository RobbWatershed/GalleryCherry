package me.devsaki.hentoid.webp_encoder

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Point
import android.net.Uri
import android.os.Build
import me.devsaki.hentoid.webp_encoder.stream.FileSeekableOutputStream
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class WebpBitmapEncoder(uri: Uri, resolver: ContentResolver) : Closeable {
    private val _outputStream = FileSeekableOutputStream(uri, resolver)
    private val _writer = WebpContainerWriter(_outputStream)
    private val _muxer: WebpMuxer = WebpMuxer(_writer)

    /**
     * Sets the loops
     *   -1 : Static
     *   0 : Endless animation
     *   >0 : X loops
     * @param loops
     */
    fun setLoops(loops: Int) {
        _muxer.setLoops(loops)
    }

    fun setDuration(duration: Int) {
        _muxer.setDuration(duration)
    }

    fun setDims(dims: Point) {
        _muxer.setWidth(dims.x)
        _muxer.setHeight(dims.y)
        Timber.i("W ->${dims.x}, H->${dims.y}")
    }

    fun setBg(bg: Int) {
        _muxer.setBg(bg)
    }


    /**
     * @param compress 100 for lossless; <100 for lossy
     */
    @Throws(IOException::class)
    fun writeFrame(frame: Bitmap, compress: Int) {
        ByteArrayOutputStream().use { outBuffer ->
            val format = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                if (compress == 100) CompressFormat.WEBP_LOSSLESS
                else CompressFormat.WEBP_LOSSY
            } else {
                CompressFormat.WEBP
            }

            frame.compress(format, compress, outBuffer)
            ByteArrayInputStream(outBuffer.toByteArray()).use { inBuffer ->
                _muxer.writeFirstFrameFromWebm(inBuffer)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        _muxer.close()
        //        _writer.close();
        _outputStream.close()
    }
}
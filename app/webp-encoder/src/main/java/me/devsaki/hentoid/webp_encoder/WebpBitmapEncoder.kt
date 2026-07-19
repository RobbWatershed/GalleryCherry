package me.devsaki.hentoid.webp_encoder

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
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
    private var _isFirstFrame = true

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

    fun setBg(bg: Int) {
        _muxer.setBg(bg)
    }

    /**
     * @param compress 100 for lossless; <100 for lossy
     */
    @Throws(IOException::class)
    fun writeFrame(frame: Bitmap, compress: Int) {
        if (_isFirstFrame) {
            _isFirstFrame = false
            _muxer.setWidth(frame.getWidth())
            _muxer.setHeight(frame.getHeight())
            Timber.i("W ->${frame.width}, H->${frame.height}")
        }

        val outBuffer = ByteArrayOutputStream()

        val format = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            if (compress == 100) CompressFormat.WEBP_LOSSLESS
            else CompressFormat.WEBP_LOSSY
        } else {
            CompressFormat.WEBP
        }


        frame.compress(format, compress, outBuffer)
        val inBuffer = ByteArrayInputStream(outBuffer.toByteArray())
        _muxer.writeFirstFrameFromWebm(inBuffer)
        outBuffer.close()
        inBuffer.close()
    }

    @Throws(IOException::class)
    override fun close() {
        _muxer.close()
        //        _writer.close();
        _outputStream.close()
    }
}
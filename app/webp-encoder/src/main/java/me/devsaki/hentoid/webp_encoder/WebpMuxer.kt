package me.devsaki.hentoid.webp_encoder

import android.graphics.Color
import me.devsaki.hentoid.webp_encoder.utils.WebpChunk
import me.devsaki.hentoid.webp_encoder.utils.WebpChunkType
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class WebpMuxer(val writer: WebpContainerWriter) {
    private var _isFirstFrame = true
    private var _loops = -1
    private var _duration = -1
    private var _width = 0
    private var _height = 0

    private var _cWidth = 0
    private var _cHeight = 0
    private var _alphaData: ByteArray = ByteArray(0)
    private var _hasAlpha = false
    private var _blending = true
    private var _dispose = true
    private var _bg = Color.TRANSPARENT

    fun setWidth(width: Int) {
        _width = width
    }

    fun setHeight(height: Int) {
        _height = height
    }

    fun setLoops(loops: Int) {
        _loops = loops
    }

    fun setDuration(duration: Int) {
        _duration = duration
    }

    fun setBg(bg: Int) {
        _bg = bg
    }

    @Throws(IOException::class)
    fun writeFirstFrameFromWebm(inputStream: InputStream) {
        WebpContainerReader(inputStream, false).use { reader ->
            reader.readHeader()
            val chunk: WebpChunk = readFirstChunkWithPayload(reader)
            writeFrame(chunk, chunk.payload, chunk.isLossless)
        }
    }

    @Throws(IOException::class)
    fun writeFrame(chunk: WebpChunk, payload: ByteArray, isLossless: Boolean) {
        if (_isFirstFrame) {
            _isFirstFrame = false
            writeHeader(chunk)
            Timber.d("is First Frame")
        }

        //        Log.i(TAG,"check if has anim?");
        if (hasAnim()) {
//            Log.i(TAG, "hasANIM then writeANMF");
            writeAnmf(chunk, payload, isLossless)
        } else {
//            Log.w(TAG, "!hasANIM then writeVP8");
            writeVp8(payload, isLossless)
        }
    }

    @Throws(IOException::class)
    fun close() {
        writer.close()
    }


    //
    private fun hasAnim(): Boolean {
//        Timber.i( "_loops "+_loops+", _duration "+_duration+" _width "+_width+" _height ");
        return _loops >= 0 && _duration > 0
    }

    @Throws(IOException::class)
    private fun readFirstChunkWithPayload(reader: WebpContainerReader): WebpChunk {
        var chunk: WebpChunk?
        chunk = reader.read()
        while (chunk != null) {
            if (chunk.type === WebpChunkType.ALPH) _alphaData = chunk.alphaData

            if (chunk.type === WebpChunkType.VP8X) {
                _hasAlpha = chunk.hasAlpha
                _cWidth = chunk.canvasWidth
                _cHeight = chunk.canvasHeight
            }

            if (chunk.type === WebpChunkType.ANIM) {
                Timber.i("hasANIM ")
            }

            if (chunk.type === WebpChunkType.ANMF) {
                _blending = chunk.useAlphaBlending
                _dispose = chunk.disposeToBackgroundColor
            }

            if (chunk.payload.isNotEmpty()) return chunk
            chunk = reader.read()
        }
        throw IOException("Can not find chunk with payload.")
    }

    @Throws(IOException::class)
    private fun writeHeader(chunk: WebpChunk) {
        writer.writeHeader()

        val vp8x = WebpChunk(WebpChunkType.VP8X)
        vp8x.hasAnim = true // TODO: make it dyanamic
        vp8x.hasAlpha = false // TODO: make it dyanamic
        vp8x.hasXmp = false
        vp8x.hasExif = false
        vp8x.hasIccp = false
        vp8x.width = _width
        vp8x.height = _height
        writer.write(vp8x)

        if (vp8x.hasAnim) {
            Timber.i("this.hasAnim then writeANIM ${chunk.type}")
            val anim = WebpChunk(WebpChunkType.ANIM)
            anim.background = _bg // TODO: make it dyanamic
            anim.loops = _loops
            writer.write(anim)
        }
    }

    @Throws(IOException::class)
    private fun writeAnmf(chunk: WebpChunk, payload: ByteArray, isLossless: Boolean) {
        Timber.i("writeAnmf ${chunk.type}")
        val anmf = WebpChunk(WebpChunkType.ANMF)
        anmf.x = 0 // it was 0
        anmf.y = 0 // it was 0
        anmf.width = _width - 1
        anmf.height = _height - 1
        anmf.duration = _duration

        anmf.isLossless = isLossless
        anmf.payload = payload

        //        Timber.i("ANMF🚧 _blending "+String.valueOf(_blending)+", _dispose "+String.valueOf(_dispose));
        anmf.useAlphaBlending = _blending
        anmf.disposeToBackgroundColor = _dispose

        anmf.alphaData = _alphaData
        //        Timber.i("_alphaData "+_alphaData.length);
//        Timber.i("_blending "+_blending);
//        Timber.i("_dispose "+_dispose);
        writer.write(anmf)
    }

    @Throws(IOException::class)
    private fun writeVp8(payload: ByteArray, isLossless: Boolean) {
        val vp8 = WebpChunk(
            if (isLossless)
                WebpChunkType.VP8L
            else
                WebpChunkType.VP8
        )
        vp8.isLossless = isLossless
        vp8.payload = payload

        writer.write(vp8)
    }
}
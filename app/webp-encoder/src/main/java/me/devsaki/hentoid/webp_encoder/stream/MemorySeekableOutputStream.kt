package me.devsaki.hentoid.webp_encoder.stream

import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class MemorySeekableOutputStream(bos: ByteArrayOutputStream) : SeekableOutputStream {
    private var _buffer = bos.toByteArray()
    private var _pos = 0

    @Throws(IOException::class)
    override fun setPosition(position: Int) {
        _pos = position
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray, length: Int) {
        val min = _pos + length
        if (_buffer == null || _buffer.size < min) {
            val b = ByteArray(min)
            if (_buffer != null) System.arraycopy(_buffer, 0, b, 0, _buffer.size)
            _buffer = b
        }
        for (i in 0..<length) _buffer[_pos++] = bytes[i]

        Timber.d("Buffer size is: %s", _buffer.size)
    }

    @Throws(IOException::class)
    override fun close() {
    }

    @Throws(IOException::class)
    override fun getBytes(): ByteArray {
        return _buffer
    }
}
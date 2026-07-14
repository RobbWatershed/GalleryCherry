package me.devsaki.hentoid.webp_encoder.stream

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class FileSeekableOutputStream(file: File) : SeekableOutputStream {
    private val _outputStream = FileOutputStream(file)

    @Throws(IOException::class)
    override fun setPosition(position: Int) {
        _outputStream.getChannel().position(position.toLong())
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray, length: Int) {
        _outputStream.write(bytes, 0, length)
    }

    @Throws(IOException::class)
    override fun close() {
        _outputStream.close()
    }

    @Throws(IOException::class)
    override fun getBytes(): ByteArray {
        return ByteArray(0)
    }
}
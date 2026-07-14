package me.devsaki.hentoid.webp_encoder.stream

import java.io.IOException

// Credits go to https://github.com/KishorJena/Webp_Transcoder
interface SeekableOutputStream {
    @Throws(IOException::class)
    fun setPosition(position: Int)

    @Throws(IOException::class)
    fun write(bytes: ByteArray, length: Int)

    @Throws(IOException::class)
    fun close()

    @Throws(IOException::class)
    fun getBytes(): ByteArray
}
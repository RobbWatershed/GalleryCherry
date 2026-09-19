package me.devsaki.hentoid.webp_encoder.stream

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import java.io.IOException

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class FileSeekableOutputStream(uri: Uri, resolver: ContentResolver) : SeekableOutputStream {
    private var outFileDescriptor: ParcelFileDescriptor? = null
    private var outputStream: FileOutputStream? = null

    init {
        outFileDescriptor = resolver.openFileDescriptor(uri, "wt")
        outFileDescriptor?.let {
            outputStream = FileOutputStream(it.fileDescriptor)
        } ?: throw RuntimeException("Couldn't find file descriptor for $uri")
    }

    @Throws(IOException::class)
    override fun setPosition(position: Int) {
        outputStream?.getChannel()?.position(position.toLong())
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray, length: Int) {
        outputStream?.write(bytes, 0, length)
    }

    @Throws(IOException::class)
    override fun close() {
        outputStream?.close()
        outputStream = null
        outFileDescriptor?.close()
        outFileDescriptor = null
    }

    @Throws(IOException::class)
    override fun getBytes(): ByteArray {
        return ByteArray(0)
    }
}
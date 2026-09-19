package me.devsaki.hentoid.util.network

import android.content.Context
import android.net.Uri
import me.devsaki.hentoid.util.file.DEFAULT_MIME_TYPE
import me.devsaki.hentoid.util.file.fileSizeFromUri
import me.devsaki.hentoid.util.file.getInputStream
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import timber.log.Timber
import java.io.InputStream


class UriFileRequestBody(context: Context, uri: Uri) : RequestBody() {
    val mediaType: MediaType?
    val size: Long
    val input: InputStream

    init {
        context.contentResolver.let { res ->
            val mime = res.getType(uri) ?: DEFAULT_MIME_TYPE
            mediaType = mime.toMediaTypeOrNull()
        }
        size = fileSizeFromUri(context, uri)
        input = getInputStream(context, uri)
    }

    override fun contentType(): MediaType? {
        return mediaType
    }

    // Stolen from FileDescriptor.toRequestBody
    override fun isOneShot(): Boolean = true

    override fun contentLength(): Long = size

    // Inspired from https://github.com/krunalpatel3/Multipart-File-Upload/blob/master/app/src/main/java/com/krunal/example/FileUploader.java
    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var uploaded = 0L

        try {
            var read: Int
            while ((input.read(buffer).also { read = it }) != -1) {
//                handler.post(ProgressUpdater(uploaded, fileLength)) TODO
                uploaded += read.toLong()
                sink.write(buffer, 0, read)
            }
        } catch (e: Exception) {
            Timber.w(e)
        } finally {
            input.close()
        }
    }
}
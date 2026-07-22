package me.devsaki.hentoid.util.video

import android.graphics.Bitmap
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.DelicateCoroutinesApi
import timber.log.Timber
import java.io.FileDescriptor
import kotlin.math.roundToInt


@RequiresApi(Build.VERSION_CODES.P)
class MediaFrameStreamer(fd: FileDescriptor) : FrameStreamer {
    val retriever = MediaMetadataRetriever()

    override val dims: Point
    override val totalFrames: Int
    override val durationMs: Int

    init {
        retriever.setDataSource(fd)
        val width =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toInt() ?: 0
        val height =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toInt() ?: 0
        dims = Point(width, height)
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toInt() ?: 0
        totalFrames =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toInt() ?: 0
    }

    fun getFirstFrame(): Bitmap? {
        return retriever.getFrameAtIndex(0)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun streamFrames(
        isCanceled: () -> Boolean,
        onFrameFound: suspend (Pair<Bitmap, Int>) -> Unit
    ) {
        for (i in 0..<totalFrames) {
            if (isCanceled.invoke()) break
            retriever.getFrameAtIndex(i)?.let { bmp ->
                onFrameFound(Pair(bmp, (durationMs.toFloat() / totalFrames).roundToInt()))
            } ?: run {
                Timber.i("Couldn't find frame bitmap at $i")
            }
        }
    }

    override fun close() {
        retriever.close()
    }
}
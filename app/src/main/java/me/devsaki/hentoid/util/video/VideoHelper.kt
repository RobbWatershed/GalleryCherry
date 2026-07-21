package me.devsaki.hentoid.util.video

import android.content.Context
import android.media.MediaCodec
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Size
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import me.devsaki.hentoid.core.CHARSET_LATIN_1
import me.devsaki.hentoid.core.HentoidApp
import kotlin.math.ceil

const val MIME_VIDEO_MP4 = "video/mp4"

val MP4_SIGNATURE = "ftyp".toByteArray(CHARSET_LATIN_1)

const val MULTIPLE = 4

fun instanciateFrameStreamer(context: Context, uri: Uri, mime: String): FrameStreamer? {
    return if (mime.startsWith("video/") && Build.VERSION.SDK_INT >= 28)
        MediaFrameStreamer(context, uri)
    else getPenfeiFrameStreamer(uri, mime)
}

@androidx.media3.common.util.UnstableApi
val videoOnlyRenderersFactory =
    RenderersFactory { handler: Handler,
                       videoListener: VideoRendererEventListener,
                       audioListener: AudioRendererEventListener,
                       textOutput: TextOutput,
                       metadataOutput: MetadataOutput ->
        arrayOf<Renderer>(
            MediaCodecVideoRenderer.Builder(HentoidApp.getInstance())
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                .setEventListener(videoListener)
                .setEventHandler(handler)
                .build()
        )
    }

// Original idea from https://github.com/sixo/vid-proc/blob/master/app/src/main/java/eu/sisik/vidproc/Utils.kt
fun getBestSupportedResolution(
    mediaCodec: MediaCodec,
    videoMime: String,
    preferredResolution: Size
): Size {
    val capabilities = mediaCodec.codecInfo.getCapabilitiesForType(videoMime).videoCapabilities
        ?: throw RuntimeException("Unsupported size for MIME $videoMime : ${preferredResolution.width}x${preferredResolution.height}")

    // First check if exact combination supported
    if (capabilities.isSizeSupported(preferredResolution.width, preferredResolution.height))
        return preferredResolution

    // Return adjusted size with every dimension a multiple of MULTIPLE (H264 constraints)
    return Size(
        ceil(preferredResolution.width.toFloat() / MULTIPLE.toFloat()).toInt() * MULTIPLE,
        ceil(preferredResolution.height.toFloat() / MULTIPLE.toFloat()).toInt() * MULTIPLE
    )

}
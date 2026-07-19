package me.devsaki.hentoid.util.video

import android.media.MediaCodec
import android.util.Size
import me.devsaki.hentoid.core.CHARSET_LATIN_1
import me.devsaki.hentoid.enums.PictureEncoder
import kotlin.math.absoluteValue

const val MIME_VIDEO_MP4 = "video/mp4" // MediaFormat.MIMETYPE_VIDEO_AVC ?

val MP4_SIGNATURE = "ftyp".toByteArray(CHARSET_LATIN_1)


fun getAnimationEncoder(format: Int): AnimationEncoder {
    return when (format) {
        PictureEncoder.WEBP_LOSSLESS.value, PictureEncoder.WEBP_LOSSY.value -> WebpEncoder()
        PictureEncoder.AVC.value -> VideoEncoder()
        else -> GifEncoder()
    }
}

// From https://github.com/sixo/vid-proc/blob/master/app/src/main/java/eu/sisik/vidproc/Utils.kt
fun getBestSupportedResolution(
    mediaCodec: MediaCodec,
    mime: String,
    preferredResolution: Size
): Size {
    val capabilities = mediaCodec.codecInfo.getCapabilitiesForType(mime).videoCapabilities
        ?: throw RuntimeException("Unsupported size for MIME $mime : ${preferredResolution.width}x${preferredResolution.height}")

    // First check if exact combination supported
    if (capabilities.isSizeSupported(preferredResolution.width, preferredResolution.height))
        return preferredResolution

    // I try the resolutions suggested by docs for H.264 and VP8
    // https://developer.android.com/guide/topics/media/media-formats#video-encoding
    // TODO: find more supported resolutions
    val resolutions = arrayListOf(
        Size(176, 144),
        Size(320, 240),
        Size(320, 180),
        Size(640, 360),
        Size(720, 480),
        Size(1280, 720),
        Size(1920, 1080)
    )

    // I prefer similar resolution with similar aspect
    val pix = preferredResolution.width * preferredResolution.height
    val preferredAspect = preferredResolution.width.toFloat() / preferredResolution.height.toFloat()

    val nearestToFurthest = resolutions.sortedWith(
        compareBy(
            {
                pix - it.width * it.height
            },
            // First compare by aspect
            {
                val aspect = if (it.width < it.height) it.width.toFloat() / it.height.toFloat()
                else it.height.toFloat() / it.width.toFloat()
                (preferredAspect - aspect).absoluteValue
            })
    )

    for (size in nearestToFurthest)
        if (capabilities.isSizeSupported(size.width, size.height)) return size

    throw RuntimeException("Couldn't find supported resolution")
}
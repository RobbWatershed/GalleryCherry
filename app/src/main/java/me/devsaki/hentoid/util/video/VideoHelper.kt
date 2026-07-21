package me.devsaki.hentoid.util.video

import android.media.MediaCodec
import android.util.Size
import me.devsaki.hentoid.core.CHARSET_LATIN_1
import kotlin.math.ceil

const val MIME_VIDEO_MP4 = "video/mp4"

val MP4_SIGNATURE = "ftyp".toByteArray(CHARSET_LATIN_1)

const val MULTIPLE = 4

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
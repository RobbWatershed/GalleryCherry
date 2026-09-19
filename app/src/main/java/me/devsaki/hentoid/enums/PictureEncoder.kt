package me.devsaki.hentoid.enums

import androidx.annotation.StringRes
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.util.image.MIME_IMAGE_AVIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_GIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_JPEG
import me.devsaki.hentoid.util.image.MIME_IMAGE_JXL
import me.devsaki.hentoid.util.image.MIME_IMAGE_PNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_WEBP
import me.devsaki.hentoid.util.losslessStr
import me.devsaki.hentoid.util.lossyStr
import me.devsaki.hentoid.util.video.MIME_VIDEO_MP4

enum class PictureEncoder(
    val value: Int,
    val mimeType: String,
    @StringRes private val descriptionRes: Int,
    val isLossless: Boolean = false,
    val isImage: Boolean = true,
    val isAnimation: Boolean = false
) {
    WEBP_LOSSLESS(0, MIME_IMAGE_WEBP, R.string.transcode_encoder_webp, true, isAnimation = true),
    WEBP_LOSSY(1, MIME_IMAGE_WEBP, R.string.transcode_encoder_webp, isAnimation = true),
    PNG(2, MIME_IMAGE_PNG, R.string.transcode_encoder_png, true),
    JPEG(3, MIME_IMAGE_JPEG, R.string.transcode_encoder_jpeg),
    JXL_LOSSY(4, MIME_IMAGE_JXL, R.string.transcode_encoder_jxl),
    JXL_LOSSLESS(5, MIME_IMAGE_JXL, R.string.transcode_encoder_jxl, true),
    JPEGLI(6, MIME_IMAGE_JPEG, R.string.transcode_encoder_jpegli),
    AVIF(7, MIME_IMAGE_AVIF, R.string.transcode_encoder_avif),
    GIF(8, MIME_IMAGE_GIF, R.string.transcode_encoder_gif, isImage = false, isAnimation = true),
    AVC(9, MIME_VIDEO_MP4, R.string.transcode_encoder_avc, isImage = false, isAnimation = true);

    val description: String
        get() {
            val descStr = HentoidApp.getInstance().resources.getString(descriptionRes)
            return if (descStr.contains("%s")) {
                descStr.replace("%s", if (isLossless) losslessStr else lossyStr)
            } else descStr
        }

    companion object {
        fun fromValue(data: Int): PictureEncoder? {
            return entries.firstOrNull { data == it.value }
        }
    }
}
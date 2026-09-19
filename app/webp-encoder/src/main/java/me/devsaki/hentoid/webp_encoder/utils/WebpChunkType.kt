package me.devsaki.hentoid.webp_encoder.utils

// Credits go to https://github.com/KishorJena/Webp_Transcoder
enum class WebpChunkType {
    VP8X,
    VP8,
    VP8L,
    ANIM,
    ANMF,
    ICCP,
    ALPH,
    XMP,
    EXIF,

    UNKNOWN,
}
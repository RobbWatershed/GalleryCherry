package me.devsaki.hentoid.webp_encoder.utils

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class WebpChunk(t: WebpChunkType) {
    val type: WebpChunkType = t

    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
    var loops: Int = 0
    var duration: Int = 0
    var background: Int = 0

    var canvasWidth: Int = 0
    var canvasHeight: Int = 0

    var payload = ByteArray(0)
    var alphaData = ByteArray(0)
    var bitStream = ByteArray(0)
    var isLossless: Boolean = false

    // chunks existance
    var hasALPHchunk: Boolean = false
    var hasVP8chunk: Boolean = false
    var hasVP8Lchunk: Boolean = false

    var hasAnim: Boolean = false
    var hasXmp: Boolean = false
    var hasExif: Boolean = false
    var hasAlpha: Boolean = false
    var hasIccp: Boolean = false

    var useAlphaBlending: Boolean = false
    var disposeToBackgroundColor: Boolean = false
    var flags = ByteArray(0)
}
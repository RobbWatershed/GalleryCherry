package me.devsaki.hentoid.webp_encoder.data

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class FrameData {
    // ANMF
    var height: Int = 0
    var width: Int = 0
    var x: Int = 0
    var y: Int = 0
    var duration: Int = 0
    var useAlphaBlending: Boolean = false
    var disposeToBackgroundColor: Boolean = false

    // VP8(L)
    var bitStream: ByteArray = ByteArray(0)

    // ALPH
    var alphaData: ByteArray = ByteArray(0)

    // flags
    var isLoseLess: Boolean = false
    var hasAlpha: Boolean = false
    var hasALPHchunk: Boolean = false
    var hasVP8chunk: Boolean = false
    var hasVP8Lchunk: Boolean = false
}
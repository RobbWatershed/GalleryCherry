package me.devsaki.hentoid.webp_encoder.data

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class AnimationData {
    var hasAlpha: Boolean = false
    var canvasHeight: Int = 0
    var canvasWidth: Int = 0
    var background: Int = 0
    var loops: Int = 0
    var framesData = ArrayList<FrameData>()
    var flags: ByteArray = ByteArray(0)

    fun add(frameData: FrameData) {
        this.framesData.add(frameData)
    }
}
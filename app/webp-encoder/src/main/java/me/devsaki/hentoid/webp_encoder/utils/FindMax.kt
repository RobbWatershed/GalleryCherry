package me.devsaki.hentoid.webp_encoder.utils

import me.devsaki.hentoid.webp_encoder.data.FrameData

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class FindMax {
    fun getHeight(framesData: MutableList<FrameData>): Int {
        var maxHeight = 0
        for (frame in framesData) {
            maxHeight = frame.height.coerceAtLeast(maxHeight)
        }
        return maxHeight
    }

    fun getWidth(frames: MutableList<FrameData>): Int {
        var maxWidth = 0
        for (frame in frames) {
            maxWidth = frame.width.coerceAtLeast(maxWidth)
        }
        return maxWidth
    }
}
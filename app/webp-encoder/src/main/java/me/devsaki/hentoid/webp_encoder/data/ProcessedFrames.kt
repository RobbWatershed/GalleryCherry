package me.devsaki.hentoid.webp_encoder.data

import android.graphics.Bitmap

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class ProcessedFrames {
    var bitmap: Bitmap? = null
    var delay: Int = 0
    var dispose: Boolean = false // Not required
    var blending: Boolean = false // Not required
    var bg: Int = 0 // Not required
    var x: Int = 0 // Not required
    var y: Int = 0 // Not required
    // as bitmaps are created in such a way that it does ont
    // need to deal with dispose and blend;
    // bitmaps are combined togather to to show image according to dispose and blend
    // also bitmaps are already aligned according to x y offset and canvas scale.
}
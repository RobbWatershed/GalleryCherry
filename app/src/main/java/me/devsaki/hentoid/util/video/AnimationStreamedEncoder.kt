package me.devsaki.hentoid.util.video

import android.graphics.Bitmap
import java.io.Closeable

interface AnimationStreamedEncoder : Closeable {
    suspend fun addFrame(
        bitmap: Bitmap,
        duration: Int
    )
}
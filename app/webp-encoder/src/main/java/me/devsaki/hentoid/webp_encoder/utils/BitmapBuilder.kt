package me.devsaki.hentoid.webp_encoder.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import me.devsaki.hentoid.webp_encoder.data.AnimationData
import me.devsaki.hentoid.webp_encoder.data.FrameData
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.BitSet

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class BitmapBuilder {
    @Throws(IOException::class)
    fun getBitmap(frameData: FrameData, animationData: AnimationData): Bitmap? {
        val bytes = encodeToStillWebP(frameData, animationData)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Throws(IOException::class)
    fun encodeToStillWebP(frameData: FrameData, animationData: AnimationData): ByteArray {
        ByteArrayOutputStream().use { baos ->
            baos.write(
                byteArrayOf(
                    'R'.code.toByte(),
                    'I'.code.toByte(),
                    'F'.code.toByte(),
                    'F'.code.toByte()
                )
            )
            baos.write(intToByteArray(getFileSize(frameData))) // File Size
            baos.write(
                byteArrayOf(
                    'W'.code.toByte(),
                    'E'.code.toByte(),
                    'B'.code.toByte(),
                    'P'.code.toByte()
                )
            ) // 4
            baos.write(
                byteArrayOf(
                    'V'.code.toByte(),
                    'P'.code.toByte(),
                    '8'.code.toByte(),
                    'X'.code.toByte()
                )
            ) // 4
            baos.write(intToByteArray(10)) // 4

            val bs = BitSet(32)
            bs.set(1, false) // A hasAnim
            bs.set(2, false) // X hasXmp
            bs.set(3, false) // E hasExif
            bs.set(4, animationData.hasAlpha) // L hasAlpha
            bs.set(5, false) // I hasIccp

            baos.write(bitSetToBytes(bs, 4)) // 4
            baos.write(intTo3ByteArray(frameData.width)) // 3
            baos.write(intTo3ByteArray(frameData.height)) // 3

            // alpha, bitStream
            if (frameData.hasVP8chunk && !frameData.hasVP8Lchunk) {
                if (frameData.hasALPHchunk) {
//                Logs.e(this,"writeByte[] hasALPHchunk "+frame.alphaData.length);
                    val chunkSize: Int = frameData.alphaData.size
                    baos.write(
                        byteArrayOf(
                            'A'.code.toByte(),
                            'L'.code.toByte(),
                            'P'.code.toByte(),
                            'H'.code.toByte()
                        )
                    ) // 4
                    baos.write(intToByteArray(chunkSize)) // 4
                    baos.write(frameData.alphaData)
                }
                baos.write(
                    byteArrayOf(
                        'V'.code.toByte(),
                        'P'.code.toByte(),
                        '8'.code.toByte(),
                        ' '.code.toByte()
                    )
                ) // 4
                //            Logs.e(this,"writeByte[] hasVP8chunk "+frame.bitStream.length);
                val chunkSize: Int = frameData.bitStream.size
                baos.write(intToByteArray(chunkSize)) // 4
                baos.write(frameData.bitStream)
                if (((chunkSize and 1) == 1)) {
                    baos.write(0)
                    Timber.i(" padded VP8 ")
                }
            } else {
//            Logs.i(this,"write byte[] -> VP8L "+frame.bitStream.length+" | baso - "+baos.size());
                baos.write(
                    byteArrayOf(
                        'V'.code.toByte(),
                        'P'.code.toByte(),
                        '8'.code.toByte(),
                        'L'.code.toByte()
                    )
                ) // 4
                val chunkSize: Int = frameData.bitStream.size
                baos.write(intToByteArray(chunkSize)) // 4
                baos.write(frameData.bitStream)

                if (((chunkSize and 1) == 1)) {
                    baos.write(0)
                    Timber.i(" vp8L padded %s", baos.size())
                }
            }
            return baos.toByteArray()
        }
    }

    fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte(),
            (value ushr 24).toByte()
        )
    }

    private fun getFileSize(frameData: FrameData): Int {
        val webp = 4
        val vp8x = 10 + 8 // fourc(4) + chunkSize(4) + parameters(10)
        val alph = 8 + frameData.alphaData.size
        val vp8: Int = frameData.bitStream.size + 8
        return 26 + alph + vp8
    }

    private fun bitSetToBytes(bs: BitSet, bytes: Int): ByteArray {
        val b = ByteArray(bytes)
        val a = bs.toByteArray()
        for (i in a.indices) b[i] = a[i]
        return b
    }

    fun intTo3ByteArray(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte()
        )
    }
}
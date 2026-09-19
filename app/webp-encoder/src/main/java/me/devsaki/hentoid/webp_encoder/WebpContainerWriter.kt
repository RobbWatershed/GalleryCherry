package me.devsaki.hentoid.webp_encoder

import me.devsaki.hentoid.webp_encoder.stream.SeekableOutputStream
import me.devsaki.hentoid.webp_encoder.utils.WebpChunk
import me.devsaki.hentoid.webp_encoder.utils.WebpChunkType
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.BitSet

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class WebpContainerWriter(val outputStream: SeekableOutputStream) {

    private var _offset = 0

    @Throws(IOException::class)
    fun writeHeader() {
        write(
            byteArrayOf(
                'R'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                'F'.code.toByte()
            )
        )
        writeUInt32(0)
        write(
            byteArrayOf(
                'W'.code.toByte(),
                'E'.code.toByte(),
                'B'.code.toByte(),
                'P'.code.toByte()
            )
        )
    }

    @Throws(IOException::class)
    fun close() {
        val fileSize = _offset - 8
        outputStream.setPosition(4)
        writeUInt32(fileSize)
//		_outputStream.close();
    }

    @Throws(IOException::class)
    fun write(chunk: WebpChunk) {
        Timber.v("Writting type ${chunk.type}")
        when (chunk.type) {
            WebpChunkType.VP8L -> writePayloadChunk(
                chunk,
                byteArrayOf(
                    'V'.code.toByte(),
                    'P'.code.toByte(),
                    '8'.code.toByte(),
                    'L'.code.toByte()
                )
            )

            WebpChunkType.VP8X -> writeVp8x(chunk)
            WebpChunkType.ANIM -> writeAnim(chunk)
            WebpChunkType.ANMF -> writeAnmf(chunk)
            else -> throw IOException("Not supported chunk type.")
        }
    }

    @Throws(IOException::class)
    private fun writePayloadChunk(chunk: WebpChunk, fourCc: ByteArray) {
        write(fourCc, 4)
        writeUInt32(chunk.payload.size)
        write(chunk.payload)
    }

    @Throws(IOException::class)
    private fun writeVp8x(chunk: WebpChunk) {
        write(
            byteArrayOf(
                'V'.code.toByte(),
                'P'.code.toByte(),
                '8'.code.toByte(),
                'X'.code.toByte()
            )
        )
        writeUInt32(10)

        val bs = BitSet(32)

        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // |Rsv|I|L|E|X|A|R|                   Reserved                    |
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // |7|6|5|4|3|2|1|0|
        Timber.d(" writeVp8x() hasAnim ${chunk.hasAnim}, hasAlpha ${chunk.hasAlpha}")
        // 0 R (reserved)
        bs.set(1, chunk.hasAnim) // A hasAnim
        bs.set(2, chunk.hasXmp) // X hasXmp
        bs.set(3, chunk.hasExif) // E hasExif
        bs.set(4, true) // L hasalpha
        bs.set(5, chunk.hasIccp) // I hasIccp
        // 6 Rsv
        // 7 Rsv
        // TODO: canvas is 0 as not its not coming from encoder callee
        // so using height or width +1. +1 to maintain original scale.
        // // till now it worked with few animatedimages
        Timber.d("chunk 🏁🏁🏁🏁::::::::: chunk.canvasHeight %s", chunk.canvasHeight)
        Timber.d("chunk 🏁🏁🏁🏁::::::::: chunk.canvasWidth %s", chunk.canvasWidth)
        write(bitSetToBytes(bs, 4))
        writeUInt24(chunk.width)
        writeUInt24(chunk.height)
    }

    @Throws(IOException::class)
    private fun writeAnim(chunk: WebpChunk) {
        write(
            byteArrayOf(
                'A'.code.toByte(),
                'N'.code.toByte(),
                'I'.code.toByte(),
                'M'.code.toByte()
            )
        )
        writeUInt32(6)
        //		Logs.e(this,"writeAnim "+chunk.background);
        writeUInt32(chunk.background)
        writeUInt16(chunk.loops)
    }

    @Throws(IOException::class)
    private fun writeAnmf(chunk: WebpChunk) {
        Timber.d("writeAnmf() ")
        write(
            byteArrayOf(
                'A'.code.toByte(),
                'N'.code.toByte(),
                'M'.code.toByte(),
                'F'.code.toByte()
            )
        )

        var alphSize = 0
        if (chunk.alphaData.isNotEmpty()) alphSize = 8 + chunk.alphaData.size

        // FourC + Size Holder + Payload Length
        writeUInt32(chunk.payload.size + 24 + alphSize)

        writeUInt24(chunk.x) // 3 bytes (3)
        writeUInt24(chunk.y) // 3 bytes (6)
        writeUInt24(chunk.width) // 3 bytes (9)
        writeUInt24(chunk.height) // 3 bytes (12)
        writeUInt24(chunk.duration) // 3 bytes (15)

        val bs = BitSet(8)
        bs.set(1, chunk.useAlphaBlending) // blend
        bs.set(0, chunk.disposeToBackgroundColor) // dispose
        write(bitSetToBytes(bs, 1)) // 1 byte (16)


        // Insert ALPH chunk
        if (chunk.alphaData.isNotEmpty()) {
            writeAlph(chunk.alphaData)
            Timber.v(" alpha data %s", chunk.alphaData.size)
        } else {
            Timber.w("no alpha data")
        }

        if (chunk.isLossless) write(
            byteArrayOf(
                'V'.code.toByte(),
                'P'.code.toByte(),
                '8'.code.toByte(),
                'L'.code.toByte()
            )
        ) // 4 bytes (20)
        else write(
            byteArrayOf(
                'V'.code.toByte(),
                'P'.code.toByte(),
                '8'.code.toByte(),
                ' '.code.toByte()
            )
        )
        writeUInt32(chunk.payload.size) // 4 bytes (24)
        write(chunk.payload)
        addPaddingZero(chunk.payload)
    }


    @Throws(IOException::class)
    fun writeAlph(alphaData: ByteArray) {
        write(
            byteArrayOf(
                'A'.code.toByte(),
                'L'.code.toByte(),
                'P'.code.toByte(),
                'H'.code.toByte()
            )
        ) // 4
        writeUInt32(alphaData.size) // 4
        write(alphaData) // x
        addPaddingZero(alphaData)
    }

    @Throws(IOException::class)
    private fun addPaddingZero(payload: ByteArray) {
        if (payload.size % 2 != 0) {
            write(byteArrayOf(0))
        }
    }


    @Throws(IOException::class)
    private fun write(bytes: ByteArray) {
        write(bytes, bytes.size)
    }

    @Throws(IOException::class)
    private fun write(bytes: ByteArray, length: Int) {
        outputStream.write(bytes, length)
        _offset += length
    }

    @Throws(IOException::class)
    private fun writeUInt(value: Int, bytes: Int) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        write(b, bytes)
    }

    @Throws(IOException::class)
    private fun writeUInt16(value: Int) {
        writeUInt(value, 2)
    }

    @Throws(IOException::class)
    private fun writeUInt24(value: Int) {
        writeUInt(value, 3)
    }

    @Throws(IOException::class)
    private fun writeUInt32(value: Int) {
        writeUInt(value, 4)
    }

    private fun bitSetToBytes(bs: BitSet, bytes: Int): ByteArray {
        val b = ByteArray(bytes)
        val a = bs.toByteArray()
        System.arraycopy(a, 0, b, 0, a.size)
        return b
    }
}
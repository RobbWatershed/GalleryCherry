package me.devsaki.hentoid.webp_encoder

import android.graphics.Color
import me.devsaki.hentoid.webp_encoder.utils.WebpChunk
import me.devsaki.hentoid.webp_encoder.utils.WebpChunkType
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.BitSet

// Credits go to https://github.com/KishorJena/Webp_Transcoder
class WebpContainerReader(val inputStream: InputStream, val debug: Boolean) {
    private val _inputStream: InputStream? = null
    private var _fileSize = 0
    private var _offset = 0

    @Throws(IOException::class)
    fun close() {
//		_inputStream.close();
    }

    @Throws(IOException::class)
    fun readHeader() {
        val fcc = ByteArray(4)
        read(fcc, 4)
        if (!isFourCc(fcc, 'R', 'I', 'F', 'F')) throw IOException("Expected RIFF file.")

        _fileSize = readUInt32() + 8 - 1

        //		Logs.i(this,"_fileSize:"+_fileSize);
        read(fcc, 4)
        if (!isFourCc(fcc, 'W', 'E', 'B', 'P')) throw IOException("Expected Webp file.")
    }

    @Throws(IOException::class)
    fun read(): WebpChunk? {
        val fcc = ByteArray(4)

        if (read(fcc, 4) > 0) {
            Timber.d("read() 4C - " + String(fcc))
            if (isFourCc(fcc, 'V', 'P', '8', 'X')) {
                return readVp8x()
            }
            if (isFourCc(fcc, 'A', 'N', 'I', 'M')) {
                return readAnim()
            }

            if (isFourCc(fcc, 'A', 'N', 'M', 'F')) {
                return readAnmf()
            }

            if (isFourCc(fcc, 'V', 'P', '8', ' ')) {
                return readVp8()
            }

            if (isFourCc(fcc, 'V', 'P', '8', 'L')) {
                return readVp8l()
            }
            //
            if (isFourCc(fcc, 'I', 'C', 'C', 'P')) return readIccp()
            if (isFourCc(fcc, 'A', 'L', 'P', 'H')) return readAlph()
            if (isFourCc(fcc, 'X', 'M', 'P', ' ')) return readXmp()

            if (isFourCc(fcc, 'E', 'X', 'I', 'F')) return readExif()

            try {
                readUnknown(fcc)
                //				Logs.w(this,"readUnknown() - "+new String(fcc));
            } catch (e: Exception) {
                throw IOException(
                    String.format(
                        "Not supported FourCC: %c.%c.%c.%c.",
                        fcc[0], fcc[1], fcc[2], fcc[3]
                    )
                )
            }
        }

        if (_fileSize != _offset) throw IOException(
            String.format(
                "Header has wrong file size: %d, expected: %d",
                _fileSize, _offset
            )
        )
        return null
    }


    @Throws(IOException::class)
    private fun readUnknown(fcc: ByteArray?): WebpChunk {
        val chunkSize = readUInt32()
        val payload = readPayload(chunkSize)

        if (payload.size < 0) {
            throw IOException("Invalid chunk size")
        }

        return WebpChunk(WebpChunkType.UNKNOWN)
    }

    @Throws(IOException::class)
    private fun readVp8x(): WebpChunk {
        val chunkSize = readUInt32()
        if (chunkSize != 10) throw IOException("Expected 10 bytes for VP8X.")

        val chunk: WebpChunk = WebpChunk(WebpChunkType.VP8X)

        val flags = ByteArray(4)
        read(flags, 4)
        val bs = BitSet.valueOf(flags)

        //		bs.get(0); 					 // R reserved
        chunk.hasAnim = bs.get(1) // A Animation
        chunk.hasXmp = bs.get(2) // X XMP
        chunk.hasExif = bs.get(3) // E Exif
        chunk.hasAlpha = bs.get(4) // L Alpha
        chunk.hasIccp = bs.get(5) // I ICCP

        Timber.i("vp8x-bs: %s", bs)

        chunk.canvasWidth = readUInt24()
        chunk.canvasHeight = readUInt24()
        chunk.flags = flags

        //		Logs.enable(this);
        Timber.i(
            "canvasWidth " + chunk.canvasWidth + " chunk.canvasHeight " + chunk.canvasHeight
        )

        Timber.d(String.format("VP8X: size = %dx%d", chunk.width, chunk.height))
        return chunk
    }

    @Throws(IOException::class)
    private fun readAnim(): WebpChunk {
//		Logs.e(this," ANIM- ");
        val chunkSize = readUInt32()
        if (chunkSize != 6) throw IOException("Expected 6 bytes for ANIM.")

        val chunk: WebpChunk = WebpChunk(WebpChunkType.ANIM)
        chunk.background = readUInt32()
        chunk.loops = readUInt16()

        Timber.i("anim-bg: " + chunk.background + ", color.trans->" + Color.TRANSPARENT)
        Timber.d(String.format("ANIM: loops = %d", chunk.loops))
        return chunk
    }

    @Throws(IOException::class)
    private fun readAnmf(): WebpChunk {
        val chunkSize = readUInt32()
        //		Logs.v(this,"chunkSize "+(chunkSize-16));
        val chunk: WebpChunk = WebpChunk(WebpChunkType.ANMF)

        // 15 bytes
        chunk.x = readUInt24()
        chunk.y = readUInt24()
        chunk.width = readUInt24()
        chunk.height = readUInt24()
        val duration = readUInt24()
        //		Logs.enable(this);
//		Logs.i(this, "duration: " + duration);
//		Logs.i(this, "width: " + chunk.width + ", height " + chunk.height);
//		Logs.i(this, "x: " + chunk.x + ", y " + chunk.y);
        chunk.duration = duration

        // +1 = 16
        val flags = ByteArray(1)
        read(flags, 1)
        val bs = BitSet.valueOf(flags)
        chunk.useAlphaBlending = bs.get(1)
        chunk.disposeToBackgroundColor = bs.get(0)

        // log bs with each index from 0 to end

//		Logs.i(this,"ANMF:: - blend, dispose "+bs);

        // +4 = 20
        val cch = ByteArray(4)
        read(cch, 4)

        var bitStream: ByteArray? = null
        val alphaData: ByteArray? = null
        //		Logs.enable(this);
//		Logs.d(this,"ANMF payload size "+chunkSize);
        if (isFourCc(cch, 'A', 'L', 'P', 'H')) {
            chunk.isLossless = false
            chunk.hasALPHchunk = true
            var AlphaSize = readUInt32()


            if ((AlphaSize and 1) == 1) {
                AlphaSize += 1
                Timber.w("ANMF/ALPH payload size :" + (AlphaSize % 4) + " | " + AlphaSize)
            }

            chunk.alphaData = readPayload(AlphaSize)

            val cc = ByteArray(4)
            read(cc, 4)

            if (isFourCc(cc, 'V', 'P', '8', ' ')) {
                chunk.hasVP8chunk = true
                var vp8Size = readUInt32()
                if ((vp8Size and 1) == 1) {
                    vp8Size += 1
                    Timber.e("ANMF/ALPH/VP8 payload size " + vp8Size)
                }
                chunk.bitStream = readPayload(vp8Size)
            }
        } else if (isFourCc(cch, 'V', 'P', '8', ' ')) {
            chunk.isLossless = false
            chunk.hasVP8chunk = true
            var vp8Size = readUInt32()
            if ((vp8Size and 1) == 1) {
                vp8Size += 1
                Timber.e("ANMF/VP8 payload size " + vp8Size)
            }
            chunk.bitStream = readPayload(vp8Size)
        } else if (isFourCc(cch, 'V', 'P', '8', 'L')) {
            chunk.isLossless = true
            chunk.hasVP8Lchunk = true
            var vp8lSize = readUInt32()
            if ((vp8lSize and 1) == 1) {
                vp8lSize += 1
                Timber.v("ANMF/VP8L | payload size " + vp8lSize)
            }

            chunk.bitStream = readPayload(vp8lSize)
            bitStream = chunk.bitStream

            //			int align = padding(vp8lSize);
//			Logs.i(this,"padding "+align);

//			if((vp8lSize&1)==1){
//				readPayload(1);
//			}
        } else {
            throw IOException("Not supported ANMF payload.")
        }


        return chunk
    }


    fun concatenateByteArrays(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }


    @Throws(IOException::class)
    private fun readVp8(): WebpChunk {
        val chunkSize = readUInt32()

        val chunk: WebpChunk = WebpChunk(WebpChunkType.VP8)
        chunk.isLossless = false
        chunk.payload = readPayload(chunkSize)

        Timber.d(String.format("VP8: bytes = %d", chunkSize))
        return chunk
    }

    @Throws(IOException::class)
    private fun readVp8l(): WebpChunk {
        val chunkSize = readUInt32()

        val chunk: WebpChunk = WebpChunk(WebpChunkType.VP8L)
        chunk.isLossless = true
        //		chunkSize is not telling the correct size of payload to read.
//		chunk.payload = readPayload(chunkSize);
        chunk.payload = readAllBytes()
        Timber.d(String.format("VP8L: bytes = %d", chunkSize))
        return chunk
    }


    @Throws(IOException::class)
    private fun readAlph(): WebpChunk {
        var chunkSize = readUInt32() // 4
        val chunk: WebpChunk = WebpChunk(WebpChunkType.ALPH)
        Timber.i("chunkSize of alph " + chunkSize)

        if ((chunkSize and 1) == 1) chunkSize += 1
        val payload = readPayload(chunkSize)
        chunk.alphaData = payload
        chunk.hasAlpha = true


        return chunk
    }

    @Throws(IOException::class)
    private fun readIccp(): WebpChunk {
        val chunkSize = readUInt32()
        val chunk: WebpChunk = WebpChunk(WebpChunkType.ICCP)

        readPayload(chunkSize)

        // no need to store the payload to this chunk as Animated Webp does not require ICCP
        return chunk
    }

    @Throws(IOException::class)
    private fun readExif(): WebpChunk {
        val chunkSize = readUInt32()
        val chunk: WebpChunk = WebpChunk(WebpChunkType.EXIF)
        val payload = readPayload(chunkSize)
        chunk.payload = payload
        return chunk
    }

    @Throws(IOException::class)
    private fun readXmp(): WebpChunk {
        val chunkSize = readUInt32()
        val chunk: WebpChunk = WebpChunk(WebpChunkType.XMP)
        val payload = readPayload(chunkSize)
        chunk.payload = payload
        return chunk
    }

    //
    @Throws(IOException::class)
    private fun readPayload(bytes: Int): ByteArray {
//		Logs.i(this,"readPayload() "+bytes);
        val payload = ByteArray(bytes)
        if (read(payload, bytes) != bytes) throw IOException("Can not read all bytes.")
        return payload
    }

    @Throws(IOException::class)
    private fun read(buffer: ByteArray, bytes: Int): Int {
        val count = _inputStream!!.read(buffer, 0, bytes)
        _offset += count
        return count
    }

    @Throws(IOException::class)
    private fun readUint(bytes: Int): Int {
        val b = byteArrayOf(0, 0, 0, 0)
        read(b, bytes)
        return ByteBuffer.wrap(b, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
    }

    @Throws(IOException::class)
    private fun readUInt32(): Int {
        return readUint(4)
    }

    @Throws(IOException::class)
    private fun readUInt24(): Int {
        return readUint(3)
    }

    @Throws(IOException::class)
    private fun readUInt16(): Int {
        return readUint(2)
    }

    private fun isFourCc(h: ByteArray, a: Char, b: Char, c: Char, d: Char): Boolean {
        return h[0] == a.code.toByte() && h[1] == b.code.toByte() && h[2] == c.code.toByte() && h[3] == d.code.toByte()
    }

    @Throws(IOException::class)
    private fun readAllBytes(): ByteArray {
        ByteArrayOutputStream().use { outputStream ->
            val buffer = ByteArray(1024)
            var numRead: Int
            while ((_inputStream!!.read(buffer).also { numRead = it }) != -1) {
                outputStream.write(buffer, 0, numRead)
                _offset += numRead
            }
            return outputStream.toByteArray()
        }
    }
}
package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readLongLe
import kotlinx.io.readString
import kotlinx.io.readUIntLe
import kotlinx.io.readUShortLe
import kotlinx.io.writeUIntLe
import kotlinx.io.writeULongLe
import kotlinx.io.writeUShortLe
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.util.byteArrayOfInts
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Date


private val FILE = byteArrayOfInts(0x50, 0x4B, 0x03, 0x04)
private val TOCFILE = byteArrayOfInts(0x50, 0x4B, 0x01, 0x02)
private val TOCEND64 = byteArrayOfInts(0x50, 0x4B, 0x06, 0x06)
private val TOCEND64_LOCATOR = byteArrayOfInts(0x50, 0x4B, 0x06, 0x07)
private val TOCEND = byteArrayOfInts(0x50, 0x4B, 0x05, 0x06)
private val FILE_EXTRA64 = byteArrayOfInts(0x01, 0x00)
private const val FLAG_ENCRYPTED: UShort = 0x01u
private const val FLAG_UTF8_ENCODE: UShort = 0x800u

/**
 * DOS time constant for representing timestamps before 1980.
 */
private const val DOSTIME_BEFORE_1980 = (1 shl 21) or (1 shl 16)

class ZipReader(context: Context, archiveUri: Uri, failOnUnsupported: Boolean = false) {
    val records = ArrayList<ArchiveEntry>()
    val allNotCompressed: Boolean

    // Aligned with sevenzipjbinding when charset is exotic (e.g. Shift_JIS)
    // (should actually be IBM Code Page 437)
    // see also https://github.com/omicronapps/7-Zip-JBinding-4Android/issues/15
    private val defaultCharset = Charsets.ISO_8859_1
    var charset = defaultCharset

    init {
        // Read Zip file; build index
        var tocOffset: Long

        Timber.d("Reading archive $archiveUri")
        val fileSize = fileSizeFromUri(context, archiveUri)

        if (0L == fileSize) { // Brand-new file
            tocOffset = 0
            allNotCompressed = false
        } else { // Existing file
            val footerData = getInputStream(context, archiveUri).use { fis ->
                readFooter(fis, fileSize, failOnUnsupported)
            }
            tocOffset = footerData.tocOffset
            var hasOneCompressed = false
            var hasError = footerData.error
            if (!hasError) {
                Timber.d("footerData ${footerData.cdrCount} ${footerData.cdrSize} $tocOffset")

                // Start with a brand-new file stream to avoid mark/reset nightmares
                val tocInfo = getInputStream(context, archiveUri).use { fis ->
                    readToC(fis, tocOffset, footerData.cdrCount, failOnUnsupported)
                } // FileInputStream
                hasOneCompressed = tocInfo.hasOneCompressed
                hasError = tocInfo.isCorrupted
            }

            // Try parsing all file entries if table of contents is corrupted
            // NB : Fails when ToC is the only place where compressed size appears
            if (hasError) {
                Timber.d("Errors in footer; trying to parse file entries")
                records.clear()
                getInputStream(context, archiveUri).asSource().buffered().use {
                    var offset = 0L
                    var id = it.readByteArray(4)
                    try {
                        do {
                            it.skip(2) // Version (viewer)
                            val flags = it.readUShortLe()
                            if (FLAG_ENCRYPTED == (flags and FLAG_ENCRYPTED) && failOnUnsupported)
                                throw UnsupportedOperationException("Encrypted ZIP entries are not supported")
                            if (FLAG_UTF8_ENCODE == (flags and FLAG_UTF8_ENCODE)) { // Bit 11 : Language encoding flag (EFS)
                                charset = Charsets.UTF_8
                            }
                            val compressionMode = it.readUShortLe()
                            if (compressionMode > 0u) hasOneCompressed = true
                            val datetime = dosToJavaTime(it.readUIntLe().toLong())
                            val crc = it.readUIntLe().toLong() // CRC32
                            var uncompressedSize = it.readUIntLe().toLong()
                            var compressedSize = it.readUIntLe().toLong()
                            val nameLength = it.readUShortLe().toLong()
                            val extraDataLength = it.readUShortLe().toInt()
                            var name = it.readString(nameLength, charset)
                            // Extra data
                            if (extraDataLength > 0) {
                                var read = 0L
                                do {
                                    val extraId = it.readUShortLe().toInt()
                                    val size = it.readUShortLe().toLong()
                                    when (extraId) {
                                        0x0001 -> { // ZIP64 extended information
                                            uncompressedSize = it.readLongLe()
                                            if (size > 8) compressedSize = it.readLongLe()
                                            if (size > 16) it.skip(8)
                                            if (size > 24) it.skip(4)
                                        }
                                        /*
                                    0x0008 -> {
                                        Timber.v("has PSF $size")
                                        it.skip(size)
                                    }
                                     */
                                        0x7075 -> { // Info-ZIP Unicode Path Extra Field
                                            it.skip(5) // Version and CRC32
                                            name = it.readString(size - 5, Charsets.UTF_8)
                                        }

                                        else -> {
                                            it.skip(size)
                                        }
                                    }
                                    read += (4 + size)
                                } while (read < extraDataLength)
                            }
                            offset += 30 + nameLength + extraDataLength
                            records.add(
                                ArchiveEntry(
                                    name.endsWith("/"),
                                    name,
                                    uncompressedSize,
                                    compressedSize,
                                    offset,
                                    compressionMode > 0u,
                                    time = datetime,
                                    crc = crc
                                )
                            )
                            it.skip(compressedSize)
                            offset += compressedSize

                            id = it.readByteArray(4)
                        } while (id.contentEquals(FILE))
                    } catch (e: EOFException) {
                        Timber.w(e)
                    }
                    Timber.d("Ended @ $offset")

                    if (id.contentEquals(TOCFILE)) tocOffset = offset
                }
            } // hasError

            allNotCompressed = !hasOneCompressed
        } // File size

        if (BuildConfig.DEBUG) {
            Timber.d("Read completed; ${records.size} records found")
            records.forEachIndexed { i, e ->
                Timber.d("RECORD $i ${e.path} (${e.isFolder}) ${e.size} @${e.offset}")
            }
        }
    } // init

    private fun readFooter(
        fis: InputStream,
        fileSize: Long,
        failOnUnsupported: Boolean
    ): ZipStream.FooterInformation {
        val result = ZipStream.FooterInformation()
        // Find central directory footer
        val footerData = ByteArray(242) // 114 + 128 for optional comments
        val footerDataOffset = fileSize - footerData.size
        // Fast ugly skip
        if (fis is FileInputStream) {
            fis.channel.position(footerDataOffset)
        } else fis.skip(footerDataOffset)
        fis.read(footerData)
        var end = findSequencePosition(footerData, 0, TOCEND64)
        if (-1 == end) {
            end = findSequencePosition(footerData, 0, TOCEND)
            if (-1 == end) {
                Timber.i("Invalid ZIP file : END not found")
                result.error = true
            }
        } else {
            result.is64 = true
        }
        val cdrEndOffset = footerDataOffset + end
        Timber.d("$cdrEndOffset = $footerDataOffset + $end (${result.is64})")
        if (result.error) return result

        // Read central directory footer
        ByteArrayInputStream(footerData, end, footerData.size - end).use { bais ->
            bais.asSource().buffered().use {
                it.skip(4) // Header
                if (result.is64) {
                    // Zip64 EOCDR
                    it.skip(8) // ECDR size
                    it.skip(2) // Version (creator)
                    val versionViewer = it.readUShortLe()
                    if (versionViewer > 50u && failOnUnsupported)
                        throw UnsupportedOperationException("ZIP version not supported : $versionViewer")
                    it.skip(8) // Disk info
                    it.skip(8) // Number of CDRs on disk
                    result.cdrCount = it.readLongLe()
                    result.cdrSize = it.readLongLe().toInt()
                    result.tocOffset = it.readLongLe()
                    // EOCD locator (not useful here)
                } else {
                    // Non-64 EOCDR
                    it.skip(6) // Disk and count info
                    result.cdrCount = it.readUShortLe().toLong()
                    result.cdrSize = it.readUIntLe().toInt()
                    result.tocOffset = it.readUIntLe().toLong()
                }
            }
        }
        return result
    }

    private fun readToC(
        fis: InputStream,
        tocOffset: Long,
        cdrCount: Long,
        failOnUnsupported: Boolean
    ): ZipStream.ToCInformation {
        val result = ZipStream.ToCInformation()

        // Fast ugly skip
        if (fis is FileInputStream) {
            fis.channel.position(tocOffset)
        } else fis.skip(tocOffset)
        fis.asSource().buffered().use {
            // Read central directory
            for (i in 1..cdrCount) {
                val id = it.readByteArray(4)
                if (!id.contentEquals(TOCFILE)) {
                    Timber.d("Corrupted ToC @ $i (${records.size} records in)")
                    result.isCorrupted = true
                    break
                }
                it.skip(4) // Version (creator and viewer)
                val flags = it.readUShortLe()
                if (FLAG_ENCRYPTED == (flags and FLAG_ENCRYPTED) && failOnUnsupported)
                    throw UnsupportedOperationException("Encrypted ZIP entries are not supported")
                if (FLAG_UTF8_ENCODE == (flags and FLAG_UTF8_ENCODE)) { // Bit 11 : Language encoding flag (EFS)
                    charset = Charsets.UTF_8
                }
                val compressionMode = it.readUShortLe()
                if (compressionMode > 0u) result.hasOneCompressed = true
                val datetime = dosToJavaTime(it.readUIntLe().toLong())
                val crc = it.readUIntLe().toLong() // CRC32
                var uncompressedSize = it.readUIntLe().toLong()
                var compressedSize = it.readUIntLe().toLong()
                val nameLength = it.readUShortLe().toLong()
                val extraDataLength = it.readUShortLe().toInt()
                val commentLength = it.readUShortLe().toLong()
                it.skip(2) // Disk number
                it.skip(6) // Internal and external attributes
                var lfhOffset = it.readUIntLe().toLong()
                var name = it.readString(nameLength, charset)
                var zip64hack = 0
                // Extra data
                if (extraDataLength > 0) {
                    var read = 0L
                    do {
                        val id = it.readUShortLe().toInt()
                        val size = it.readUShortLe().toLong()
                        //Timber.v("extra data $id ($size)")
                        when (id) {
                            0x0001 -> { // ZIP64 extended information
                                //Timber.v("zip64 extra data $id ($size)")
                                zip64hack = -8 // extra data is usually shorter on the local header
                                uncompressedSize = it.readLongLe()
                                if (size > 8) compressedSize = it.readLongLe()
                                if (size > 16) lfhOffset = it.readLongLe()
                                if (size > 24) it.skip(4) // Number of the disk on which this file starts
                            }
                            /*
                            0x0008 -> {
                                Timber.v("has PSF $size")
                                it.skip(size)
                            }
                             */
                            0x7075 -> { // Info-ZIP Unicode Path Extra Field
                                it.skip(5) // Version and CRC32
                                name = it.readString(size - 5, Charsets.UTF_8)
                            }

                            else -> {
                                it.skip(size)
                            }
                        }
                        read += (4 + size)
                    } while (read < extraDataLength)
                }
                it.skip(commentLength)

                // Assuming the local header stores the very same extra data as the central header ^^"
                // (minus the zip64hack)
                val offset = lfhOffset + 30 + nameLength + extraDataLength + zip64hack
                records.add(
                    ArchiveEntry(
                        name.endsWith("/"),
                        name,
                        uncompressedSize,
                        compressedSize,
                        offset,
                        compressionMode > 0u,
                        time = datetime,
                        crc = crc
                    )
                )
            } // cdrCount
        } // asSource.buffered
        return result
    }

    /**
     * Converts DOS time to Java time (number of milliseconds since epoch).
     */
    @Suppress("DEPRECATION")
    private fun dosToJavaTime(dtime: Long): Long {
        // Use of date constructor
        val d = Date(
            (((dtime shr 25) and 0x7fL) + 80).toInt(),
            (((dtime shr 21) and 0x0fL) - 1).toInt(),
            ((dtime shr 16) and 0x1fL).toInt(),
            ((dtime shr 11) and 0x1fL).toInt(),
            ((dtime shr 5) and 0x3fL).toInt(),
            ((dtime shl 1) and 0x3eL).toInt()
        )
        return d.time
    }
}

/**
 * Assume and force Zip64
 * Assume not multiple files ("disks")
 */
class ZipStream(context: Context, archiveUri: Uri, append: Boolean) : Closeable {
    val records = ArrayList<ArchiveEntry>()

    var currentRecord: ArchiveEntry? = null
    var currentOffset = 0L

    val sink: Sink

    init {
        records.addAll(ZipReader(context, archiveUri, true).records)

        val fileSize = fileSizeFromUri(context, archiveUri)
        val outStream = getOutputStream(context, archiveUri, append)
            ?: throw IOException("Couldn't open for output : $archiveUri")

        if (append) currentOffset = fileSize

        sink = outStream.asSink().buffered()
    }

    /**
     * Write file record descriptor using STORED mode and ZIP64 structure
     */
    fun putStoredRecord(path: String, size: Long, crc: Long) {
        sink.write(FILE)
        sink.writeUShortLe(45u) // Version for ZIP64
        sink.writeUShortLe(FLAG_UTF8_ENCODE) // Flag : UTF-8 encoded name
        sink.writeUShortLe(0u) // STORED mode
        val time = java.time.Instant.now().toEpochMilli()
        sink.writeUIntLe(javaToExtendedDosTime(time).first.toUInt()) // Time & Date
        sink.writeUIntLe(crc.toUInt())
        sink.writeUIntLe(UInt.MAX_VALUE) // Uncompressed size for ZIP64
        sink.writeUIntLe(UInt.MAX_VALUE) // Compressed size for ZIP64
        val nameBuffer = Charsets.UTF_8.encode(path)
        val nameData = ByteArray(nameBuffer.limit())
        nameBuffer.get(nameData)
        sink.writeUShortLe(nameData.size.toUShort())
        sink.writeUShortLe(20u) // Size of ZIP64 extra data
        sink.write(nameData)
        // ZIP64 extra data
        sink.write(FILE_EXTRA64)
        sink.writeUShortLe(16u) // Size of ZIP64 extra data (without headers)
        sink.writeULongLe(size.toULong())
        sink.writeULongLe(size.toULong()) // Compressed size is identical to uncompressed size as we use STORED mode
        currentOffset += (30 + nameData.size + 20)

        currentRecord = ArchiveEntry(
            path.endsWith("/"),
            path,
            size,
            size,
            currentOffset,
            false,
            time = time,
            crc = crc
        )
    }

    /**
     * Write file record data without any compression / encryption (STORED mode)
     */
    fun transferData(s: InputStream) {
        currentRecord?.let {
            val size = sink.transferFrom(s.asSource())
            if (size != it.size) throw Exception("Transferred size ($size) is different than declared size (${it.size})")
            currentOffset += size
        }
    }

    /**
     * Close file record
     */
    fun closeRecord() {
        currentRecord?.let { e ->
            records.add(e)
            Timber.d("NEW RECORD ${e.path} (${e.isFolder}) ${e.size} @${e.offset}")
        }
        currentRecord = null
    }

    /**
     * Close the stream, writing the entire table of contents using STORED mode and ZIP64 structure
     */
    override fun close() {
        Timber.d("ZipStream : Closing")
        // Write brand new ToC
        val tocOffset = currentOffset
        var tocSize = 0L
        Timber.d("ZipStream : Writing ToC for ${records.size} records")
        records.forEach {
            sink.write(TOCFILE)
            sink.writeUShortLe(45u) // Version for ZIP64
            sink.writeUShortLe(45u) // Version for ZIP64
            sink.writeUShortLe(0u) // Flag
            sink.writeUShortLe(0u) // STORED mode
            sink.writeUIntLe(javaToExtendedDosTime(it.time).first.toUInt()) // Time & Date
            sink.writeUIntLe(it.crc.toUInt())
            sink.writeUIntLe(UInt.MAX_VALUE) // Uncompressed size for ZIP64
            sink.writeUIntLe(UInt.MAX_VALUE) // Compressed size for ZIP64
            val nameBuffer = Charsets.UTF_8.encode(it.path)
            val nameData = ByteArray(nameBuffer.limit())
            nameBuffer.get(nameData)
            sink.writeUShortLe(nameData.size.toUShort())
            sink.writeUShortLe(28u) // Size of ZIP64 extra data
            sink.writeUShortLe(0u) // Comment length
            sink.writeUShortLe(0u) // Disk number
            sink.writeUShortLe(0u) // Internal attrs
            sink.writeUIntLe(0u) // External attrs
            sink.writeUIntLe(UInt.MAX_VALUE) // Offset for ZIP64
            sink.write(nameData)
            // ZIP64 extra data
            sink.write(FILE_EXTRA64)
            sink.writeUShortLe(24u) // Size of ZIP64 extra data (without headers)
            sink.writeULongLe(it.size.toULong())
            sink.writeULongLe(it.size.toULong()) // Compressed size is identical to uncompressed size as we use STORED mode
            sink.writeULongLe(it.offset.toULong())
            tocSize += (46 + nameData.size + 28)
        }

        // Write ToC footer
        // Footer for ZIP64
        sink.write(TOCEND64)
        sink.writeULongLe(44u)
        sink.writeUShortLe(45u) // Version for ZIP64
        sink.writeUShortLe(45u) // Version for ZIP64
        sink.writeUIntLe(0u) // Disk number
        sink.writeUIntLe(0u) // Disk with central directory
        sink.writeULongLe(records.size.toULong()) // Number of records on this disk
        sink.writeULongLe(records.size.toULong()) // Total number of records
        sink.writeULongLe(tocSize.toULong()) // Size of ToC
        sink.writeULongLe(tocOffset.toULong()) // Offset of ToC

        // ZIP64 locator
        val eocdOffset = tocOffset + tocSize
        sink.write(TOCEND64_LOCATOR)
        sink.writeUIntLe(0u) // Disk with EOCD record
        sink.writeULongLe(eocdOffset.toULong()) // Offset of EOCD
        sink.writeUIntLe(1u) // Number of disks

        // Classic footer
        sink.write(TOCEND)
        sink.writeUShortLe(0u) // Disc number
        sink.writeUShortLe(0u) // Disc with central directory
        sink.writeUShortLe(UShort.MAX_VALUE) // Entries on disk for ZIP64
        sink.writeUShortLe(UShort.MAX_VALUE) // Total entries for ZIP64
        sink.writeUIntLe(UInt.MAX_VALUE) // Size of ToC for ZIP64
        sink.writeUIntLe(UInt.MAX_VALUE) // Offset of ToC for ZIP64
        sink.writeUShortLe(0u) // Comment length

        sink.flush()
        sink.close()
        currentOffset = 0
    }

    /**
     * Converts Java time to DOS time, encoding any milliseconds lost
     * in the conversion into the upper half of the returned long
     *
     * Adapted from java.util.zip.ZipUtils
     *
     * @param time milliseconds since epoch
     * @return
     *   first : DOS time
     *   second : 2s remainder
     */
    private fun javaToExtendedDosTime(time: Long): Pair<Int, Int> {
        if (time < 0) return Pair(DOSTIME_BEFORE_1980, 0)

        val dostime = javaToDosTime(time)
        return if (dostime != DOSTIME_BEFORE_1980) Pair(dostime, (time % 2000).toInt())
        else Pair(DOSTIME_BEFORE_1980, 0)
    }

    /**
     * Converts Java time to DOS time
     * Adapted from java.util.zip.ZipUtils
     */
    @Suppress("deprecation") // Use of date methods
    private fun javaToDosTime(time: Long): Int {
        val d = Date(time)
        val year = d.year + 1900
        if (year < 1980) return DOSTIME_BEFORE_1980
        return (year - 1980) shl 25 or ((d.month + 1) shl 21) or (d.date shl 16) or (d.hours shl 11) or (d.minutes shl 5) or (d.seconds shr 1)
    }

    data class FooterInformation(
        var cdrCount: Long = 0L,
        var is64: Boolean = false,
        var cdrSize: Int = 0,
        var tocOffset: Long = 0,
        var error: Boolean = false
    )

    data class ToCInformation(
        var hasOneCompressed: Boolean = false,
        var isCorrupted: Boolean = false
    )
}
package me.devsaki.hentoid.util.file

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns
import androidx.core.net.toUri


// main content provider URI
private const val CONTENT_PREFIX = "content://"

// must match what is declared in the Zip content provider in
// the AndroidManifest.xml file
private const val ZFP_AUTHORITY = "me.devsaki.hentoid.util.file"

val FILEID: String = BaseColumns._ID
private const val PATH: String = "ZFIL"
private const val MODIFICATION: String = "ZMOD"
private const val CRC32: String = "ZCRC"
private const val UNCOMPRESSEDLEN: String = "ZUNL"

private const val FILEID_IDX: Int = 0 // Relevant?
private const val PATH_IDX: Int = 2
private const val MOD_IDX: Int = 3
private const val CRC_IDX: Int = 4
private const val UNCOMPLEN_IDX: Int = 6


// Inspired by https://github.com/googlearchive/play-apk-expansion/tree/master
// and https://github.com/Babay88/AndroidCodeSamplesB/blob/master/ShareZipped/src/main/java/ru/babay/codesamples/sharezip/ZipFilesProvider.java
class ZipFileProvider : ContentProvider() {

    val ALL_FIELDS: Array<String?> = arrayOf(
        FILEID,
        PATH,
        MODIFICATION,
        CRC32,
        UNCOMPRESSEDLEN
    )

    val ALL_FIELDS_INT: IntArray = intArrayOf(
        FILEID_IDX,
        PATH_IDX,
        MOD_IDX,
        CRC_IDX,
        UNCOMPLEN_IDX
    )

    val ASSET_URI: Uri = (CONTENT_PREFIX + ZFP_AUTHORITY).toUri()

    fun getAuthority(): String {
        return ZFP_AUTHORITY
    }

    override fun delete(
        p0: Uri,
        p1: String?,
        p2: Array<out String>?
    ): Int {
        // Unsupported
        return 0
    }

    override fun getType(p0: Uri): String {
        return "vnd.android.cursor.item/asset"
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        // Unsupported
        return null
    }

    override fun update(
        p0: Uri,
        p1: ContentValues?,
        p2: String?,
        p3: Array<out String?>?
    ): Int {
        // Unsupported
        return 0
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val usedProjection = projection ?: ALL_FIELDS

        val zipReader = ZipReader(ctx, uri)
        val zipEntries = zipReader.records

        val intProjection: IntArray
        if (null == projection) {
            intProjection = ALL_FIELDS_INT
        } else {
            val len = usedProjection.size
            intProjection = IntArray(len)
            for (i in 0..<len) {
                if (usedProjection[i] == FILEID) {
                    intProjection[i] = FILEID_IDX
                } else if (usedProjection[i] == PATH) {
                    intProjection[i] = PATH_IDX
                } else if (usedProjection[i] == MODIFICATION) {
                    intProjection[i] = MOD_IDX
                } else if (usedProjection[i] == CRC32) {
                    intProjection[i] = CRC_IDX
                } else if (usedProjection[i] == UNCOMPRESSEDLEN) {
                    intProjection[i] = UNCOMPLEN_IDX
                } else {
                    throw RuntimeException()
                }
            }
        }
        val mc = MatrixCursor(usedProjection, zipEntries.size)
        for (entry in zipEntries) {
            val rb = mc.newRow()
            intProjection.forEachIndexed { idx, intP ->
                when (intP) {
                    FILEID_IDX -> rb.add(idx)
                    PATH_IDX -> rb.add(entry.path)
                    MOD_IDX -> rb.add(entry.time)
                    CRC_IDX -> rb.add(entry.crc)
                    UNCOMPLEN_IDX -> rb.add(entry.size)
                }
            }
        }
        return mc
    }

    override fun openAssetFile(
        uri: Uri,
        mode: String
    ): AssetFileDescriptor? {
        var path: String = uri.encodedPath!!
        if (path.startsWith("/")) path = path.substring(1)

        //return .getAssetFileDescriptor(path)
        return null
    }

    override fun openFile(
        uri: Uri,
        mode: String
    ): ParcelFileDescriptor? {
        return openAssetFile(uri, mode)?.parcelFileDescriptor
    }
}
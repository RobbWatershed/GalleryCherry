package me.devsaki.hentoid.util.file

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import me.devsaki.hentoid.util.network.UriParts
import timber.log.Timber
import java.io.FileNotFoundException


// must match what is declared in the Zip content provider in
// the AndroidManifest.xml file
const val CFP_AUTHORITY = "me.violet.chunk"

// Inspired by https://github.com/googlearchive/play-apk-expansion/tree/master
// and https://github.com/Babay88/AndroidCodeSamplesB/blob/master/ShareZipped/src/main/java/ru/babay/codesamples/sharezip/ZipFilesProvider.java
class ChunkFileProvider : ContentProvider() {

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
    ): Cursor {
        val info = ChunkFileInfo.fromUri(uri)
        val usedProjection =
            projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        val cols: Array<String?> = arrayOfNulls(usedProjection.size)
        val values: Array<Any?> = arrayOfNulls(usedProjection.size)
        var i = 0
        for (col in usedProjection) {
            if (OpenableColumns.DISPLAY_NAME == col) {
                cols[i] = OpenableColumns.DISPLAY_NAME
                values[i++] = info.displayName
            } else if (OpenableColumns.SIZE == col) {
                // return size of original file; zip-file might differ
                cols[i] = OpenableColumns.SIZE
                values[i++] = info.chunkSize
            }
        }

        val cursor = MatrixCursor(cols, 1)
        cursor.addRow(values)
        return cursor
    }

    override fun openAssetFile(
        uri: Uri,
        mode: String
    ): AssetFileDescriptor? {
        return context?.let { return ChunkFileInfo.fromUri(uri).getAssetFileDescriptor(it) }
    }

    override fun openFile(
        uri: Uri,
        mode: String
    ): ParcelFileDescriptor? {
        return openAssetFile(uri, mode)?.parcelFileDescriptor
    }

    class ChunkFileInfo(
        val mainFileUri: Uri,
        val displayName: String,
        val chunkOffset: Long,
        val chunkSize: Long
    ) {
        companion object {
            fun fromUri(uri: Uri): ChunkFileInfo {
                val parts = UriParts(uri)
                val archiveUri = parts.pathFull
                    .substring(parts.host.length + 1)
                    .replace("content:/com", "content://com")
                val queryArgs = parts.queryArgs
                val offset = queryArgs["o"]?.toLong() ?: 0
                val size = queryArgs["s"]?.toLong() ?: 0

                return ChunkFileInfo(
                    archiveUri.toUri(),
                    "${parts.fileNameFull}@$offset",
                    offset,
                    size
                )
            }
        }

        fun toUri(): Uri {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(CFP_AUTHORITY)
                .path(mainFileUri.toString())
                .appendQueryParameter("o", chunkOffset.toString())
                .appendQueryParameter("s", chunkSize.toString())
                .build()
        }

        fun getAssetFileDescriptor(context: Context): AssetFileDescriptor? {
            try {
                val pfd = context.contentResolver.openFileDescriptor(mainFileUri, "r")
                // Thank God AssetFileDescriptor has that kind of constructor, the entire hack relies on it ^^"
                return AssetFileDescriptor(pfd, chunkOffset, chunkSize)
            } catch (e: FileNotFoundException) {
                Timber.w(e)
            }
            return null
        }
    }
}
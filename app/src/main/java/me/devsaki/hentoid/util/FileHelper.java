package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;

import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by avluis on 08/05/2016.
 * Generic file-related utility class
 */
public class FileHelper {

    private FileHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final String AUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.FileProvider";

    private static final String PRIMARY_VOLUME_NAME = "primary";
    private static final String NOMEDIA_FILE_NAME = ".nomedia";


    public static String getFileProviderAuthority() {
        return AUTHORITY;
    }

    /**
     * Build a DocumentFile from the given Uri string
     * @param context Context to use for the conversion
     * @param uriStr Uri string to use
     * @return DocumentFile built from the given Uri string; null if the DocumentFile couldn't be built
     */
    @Nullable
    public static DocumentFile getFileFromUriString(@NonNull final Context context, @NonNull final String uriStr) {
        Uri fileUri = Uri.parse(uriStr);
        return DocumentFile.fromSingleUri(context, fileUri);
    }

    /**
     * Get the full, human-readable access path from the given Uri
     *
     * Credits go to https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri/36162691#36162691
     *
     * @param context Context to use for the conversion
     * @param uri Uri to get the full path from
     * @param isFolder true if the given Uri represents a folder; false if it represents a file
     * @return Full, human-readable access path from the given Uri
     */
    public static String getFullPathFromTreeUri(@NonNull final Context context, @NonNull final Uri uri, boolean isFolder) {
        if (uri.toString().isEmpty()) return "";

        String volumePath = getVolumePath(context, getVolumeIdFromUri(uri, isFolder));
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromUri(uri, isFolder);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        } else return volumePath;
    }

    /**
     * Get the human-readable access path for the given volume ID
     * @param context Context to use
     * @param volumeId Volume ID to get the path from
     * @return Human-readable access path of the given volume ID
     */
    @SuppressLint("ObsoleteSdkInt")
    private static String getVolumePath(@NonNull Context context, final String volumeId) {
        try {
            // StorageVolume exist since API21, but only visible since API24
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);
            if (null == result) return null;

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

                // primary volume?
                if (primary != null && primary && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);

                // other volumes?
                if (uuid != null && uuid.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);
            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Get the volume ID of the given Uri
     * @param uri Uri to get the volume ID for
     * @param isFolder true if the given Uri represents a folder; false if it represents a file
     * @return Volume ID of the given Uri
     */
    private static String getVolumeIdFromUri(final Uri uri, boolean isFolder) {
        final String docId;
        if (isFolder) docId = DocumentsContract.getTreeDocumentId(uri);
        else docId = DocumentsContract.getDocumentId(uri);

        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    /**
     * Get the human-readable document path of the given Uri
     * @param uri Uri to get the path for
     * @param isFolder true if the given Uri represents a folder; false if it represents a file
     * @return Human-readable document path of the given Uri
     */
    private static String getDocumentPathFromUri(final Uri uri, boolean isFolder) {
        final String docId;
        if (isFolder) docId = DocumentsContract.getTreeDocumentId(uri);
        else docId = DocumentsContract.getDocumentId(uri);

        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }

    /**
     * Ensure file creation from stream.
     *
     * @param stream - OutputStream
     * @return true if all OK.
     */
    static boolean sync(@NonNull final OutputStream stream) {
        return (stream instanceof FileOutputStream) && FileUtil.sync((FileOutputStream) stream);
    }

    /**
     * Create an OutputStream opened the given file
     * NB : File length will be truncated to the length of the written data
     * @param target File to open the OutputStream on
     * @return New OutputStream opened on the given file
     */
    public static OutputStream getOutputStream(@NonNull final File target) throws IOException {
        return FileUtils.openOutputStream(target);
    }

    /**
     * Create an OutputStream opened the given file
     * NB : File length will be truncated to the length of the written data
     * @param context Context to use
     * @param target File to open the OutputStream on
     * @return New OutputStream opened on the given file
     * @throws IOException In case something horrible happens during I/O
     */
    public static OutputStream getOutputStream(@NonNull final Context context, @NonNull final DocumentFile target) throws IOException {
        return context.getContentResolver().openOutputStream(target.getUri(), "rwt"); // Always truncate file to whatever data needs to be written
    }

    /**
     * Create an InputStream opened the given file
     * @param context Context to use
     * @param target File to open the InputStream on
     * @return New InputStream opened on the given file
     * @throws IOException In case something horrible happens during I/O
     */
    public static InputStream getInputStream(@NonNull final Context context, @NonNull final DocumentFile target) throws IOException {
        return context.getContentResolver().openInputStream(target.getUri());
    }

    public static InputStream getInputStream(@NonNull final File target) throws IOException {
        return FileUtils.openInputStream(target);
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful or the folder already exists
     */
    public static boolean createDirectory(@NonNull File file) {
        return FileUtil.makeDir(file);
    }

    /**
     * Delete a file.
     *
     * @param target The file.
     */
    public static void removeFile(File target) {
        FileUtil.deleteFile(target);
    }

    /**
     * Delete files in a target directory.
     *
     * @param target The folder.
     * @return true if cleaned successfully.
     */
    public static boolean cleanDirectory(@NonNull File target) {
        try {
            return FileUtil.tryCleanDirectory(target);
        } catch (Exception e) {
            Timber.e(e, "Failed to clean directory");
            return false;
        }
    }

    /**
     * Return the DocumentFile with the given display name located in the given folder
     * If it doesn't exist, create a new one and return it
     *
     * @param context     Context to use
     * @param folder      Containing folder
     * @param mimeType    Mime-type to use if the document has to be created
     * @param displayName Display name of the document
     * @return Usable DocumentFile; null if creation failed
     */
    @Nullable
    public static DocumentFile findOrCreateDocumentFile(@NonNull final Context context, @NonNull final DocumentFile folder, @Nullable String mimeType, @NonNull final String displayName) {
        // Look for it first
        DocumentFile file = findFile(context, folder, displayName);
        if (null == file) { // Create it
            if (null == mimeType) mimeType = "application/octet-steam";
            return folder.createFile(mimeType, displayName);
        } else return file;
    }

    /**
     * Check if the given folder is valid; if it is, set it as the app's root folder
     * @param context Context to use
     * @param folder Folder to check and set
     * @param notify true if the method is allowed to create a toast in case of any error -- TODO this parameter is a joke
     * @return true if the given folder is valid and has been set; false if not
     */
    public static boolean checkAndSetRootFolder(@NonNull final Context context, @NonNull final DocumentFile folder, boolean notify) {
        // Validate folder
        if (!folder.exists() && !folder.isDirectory()) {
            if (notify)
                ToastUtil.toast(context, R.string.error_creating_folder);
            return false;
        }

        // Remove and add back the nomedia file to test if the user has the I/O rights to the selected folder
        DocumentFile nomedia = findFile(context, folder, NOMEDIA_FILE_NAME);
        if (nomedia != null) nomedia.delete();

        nomedia = folder.createFile("application/octet-steam", NOMEDIA_FILE_NAME);
        if (null == nomedia || !nomedia.exists()) {
            if (notify)
                ToastUtil.toast(context, R.string.error_write_permission);
            return false;
        }

        Preferences.setStorageUri(folder.getUri().toString());
        return true;
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context to use
     * @param aFile   File to be opened
     */
    public static void openFile(@NonNull Context context, @NonNull File aFile) {
        File file = new File(aFile.getAbsolutePath());
        Uri dataUri = FileProvider.getUriForFile(context, AUTHORITY, file);
        tryOpenFile(context, dataUri, aFile.getName(), aFile.isDirectory());
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context to use
     * @param aFile   File to be opened
     */
    public static void openFile(@NonNull Context context, @NonNull DocumentFile aFile) {
        String fileName = (null == aFile.getName()) ? "" : aFile.getName();
        tryOpenFile(context, aFile.getUri(), fileName, aFile.isDirectory());
    }

    /**
     * Attempt to open the file or folder at the given Uri using the device's app(s) of choice
     * @param context Context to use
     * @param uri Uri of the file or folder to be opened
     * @param fileName Display name of the file or folder to be opened
     * @param isDirectory true if the given Uri represents a folder; false if it represents a file
     */
    private static void tryOpenFile(@NonNull Context context, @NonNull Uri uri, @NonNull String fileName, boolean isDirectory) {
        try {
            if (isDirectory) {
                try {
                    openFileWithIntent(context, uri, DocumentsContract.Document.MIME_TYPE_DIR);
                } catch (ActivityNotFoundException e1) {
                    try {
                        openFileWithIntent(context, uri, "resource/folder");
                    } catch (ActivityNotFoundException e2) {
                        ToastUtil.toast(R.string.select_file_manager);
                        openFileWithIntent(context, uri, "*/*");
                    }
                }
            } else
                openFileWithIntent(context, uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(getExtension(fileName)));
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to open %s", uri.toString());
            ToastUtil.toast(context, R.string.error_open, Toast.LENGTH_LONG);
        }
    }

    /**
     * Opens the given Uri using the device's app(s) of choice
     * @param context Context to use
     * @param uri Uri of the file or folder to be opened
     * @param mimeType Mime-type to use (determines the apps the device will suggest for opening the resource)
     */
    private static void openFileWithIntent(@NonNull Context context, @NonNull Uri uri, @Nullable String mimeType) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        myIntent.setDataAndTypeAndNormalize(uri, mimeType);
        myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(myIntent);
    }

    /**
     * Returns the extension of the given filename, without the "."
     *
     * @param fileName Filename
     * @return Extension of the given filename, without the "."
     */
    public static String getExtension(@NonNull final String fileName) {
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.US) : "";
    }

    /**
     * Returns the name of the given filename, without the extension
     *
     * @param fileName Filename
     * @return Name of the given filename, without the extension
     */
    public static String getFileNameWithoutExtension(@NonNull final String fileName) {
        return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
    }

    /**
     * Save the given binary data in the given file, truncating the file length to the given data
     * @param context Context to use
     * @param file File to write to
     * @param binaryData Data to write
     * @throws IOException In case something horrible happens during I/O
     */
    public static void saveBinaryInFile(@NonNull final Context context, @NonNull final DocumentFile file, byte[] binaryData) throws IOException {
        byte[] buffer = new byte[1024];
        int count;

        try (InputStream input = new ByteArrayInputStream(binaryData)) {
            try (BufferedOutputStream output = new BufferedOutputStream(FileHelper.getOutputStream(context, file))) {

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                output.flush();
            }
        }
    }

    /**
     * Get the relevant file extension (without the ".") from the given mime-type
     * @param mimeType Mime-type to get a file extension from
     * @return Most relevant file extension (without the ".") corresponding to the given mime-type; null if none has been found
     */
    @Nullable
    public static String getExtensionFromMimeType(@NonNull String mimeType) {
        if (mimeType.isEmpty()) return null;

        String result = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        // Exceptions that MimeTypeMap does not support
        if (null == result) {
            if (mimeType.equals("image/apng") || mimeType.equals("image/vnd.mozilla.apng"))
                return "png";
        }
        return result;
    }

    /**
     * Get the most relevant mime-type for the given file extension
     * @param extension File extension to get the mime-type for (without the ".")
     * @return Most relevant mime-type for the given file extension; generic mime-type if none found
     */
    public static String getMimeTypeFromExtension(@NonNull String extension) {
        if (extension.isEmpty()) return "application/octet-stream";
        String result = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (null == result) return "application/octet-stream";
        else return result;
    }

    /**
     * Share the given file using the device's app(s) of choice
     * @param context Context to use
     * @param f File to share
     * @param title Title of the user dialog
     */
    public static void shareFile(final @NonNull Context context, final @NonNull DocumentFile f, final @NonNull String title) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/*");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, f.getUri());
        context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)));
    }

    // see https://stackoverflow.com/questions/5084896/using-contentproviderclient-vs-contentresolver-to-access-content-provider
    public static List<DocumentFile> listFolders(@NonNull Context context, @NonNull DocumentFile parent) {
        return listFoldersFilter(context, parent, null);
    }

    public static List<DocumentFile> listFolders(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client) {
        return FileUtil.listDocumentFiles(context, parent, client, null, true, false);
    }

    public static List<DocumentFile> listFoldersFilter(@NonNull Context context, @NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(parent.getUri());
        if (null == client) return Collections.emptyList();
        try {
            return FileUtil.listDocumentFiles(context, parent, client, filter, true, false);
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }
    }

    public static List<DocumentFile> listFiles(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client, final FileHelper.NameFilter filter) {
        return FileUtil.listDocumentFiles(context, parent, client, filter, false, true);
    }

    public static int countFiles(@NonNull DocumentFile parent, @NonNull ContentProviderClient client, final FileHelper.NameFilter filter) {
        return FileUtil.countDocumentFiles(parent, client, filter, false, true);
    }

    public static List<DocumentFile> listFiles(@NonNull Context context, @NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(parent.getUri());
        if (null == client) return Collections.emptyList();
        try {
            return FileUtil.listDocumentFiles(context, parent, client, filter, false, true);
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }
    }

    @Nullable
    public static DocumentFile findFolder(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client, @NonNull String subfolderName) {
        List<DocumentFile> result = FileUtil.listDocumentFiles(context, parent, client, FileHelper.createNameFilterEquals(subfolderName), true, false);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client, @NonNull String fileName) {
        List<DocumentFile> result = FileUtil.listDocumentFiles(context, parent, client, FileHelper.createNameFilterEquals(fileName), false, true);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findFolder(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String subfolderName) {
        List<DocumentFile> result = listDocumentFiles(context, parent, subfolderName, true, false);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String fileName) {
        List<DocumentFile> result = listDocumentFiles(context, parent, fileName, false, true);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    public static List<DocumentFile> listDocumentFiles(@NonNull final Context context,
                                                       @NonNull final DocumentFile parent,
                                                       @NonNull ContentProviderClient client) {
        return FileUtil.listDocumentFiles(context, parent, client, null, true, true);
    }

    private static List<DocumentFile> listDocumentFiles(@NonNull final Context context,
                                                        @NonNull final DocumentFile parent,
                                                        final String nameFilter,
                                                        boolean listFolders,
                                                        boolean listFiles) {
        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(parent.getUri());
        if (null == client) return Collections.emptyList();
        try {
            return FileUtil.listDocumentFiles(context, parent, client, FileHelper.createNameFilterEquals(nameFilter), listFolders, listFiles);
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }
    }

    static int findSequencePosition(byte[] data, int initialPos, byte[] sequence, int limit) {
//        int BUFFER_SIZE = 64;
//        byte[] readBuffer = new byte[BUFFER_SIZE];

        int remainingBytes;
//        int bytesToRead;
//        int dataPos = 0;
        int iSequence = 0;

        if (initialPos < 0 || initialPos > data.length) return -1;

        remainingBytes = (limit > 0) ? Math.min(data.length - initialPos, limit) : data.length;

//        while (remainingBytes > 0) {
//            bytesToRead = Math.min(remainingBytes, BUFFER_SIZE);
//            System.arraycopy(data, dataPos, readBuffer, 0, bytesToRead);
//            dataPos += bytesToRead;

//            stream.Read(readBuffer, 0, bytesToRead);

        for (int i = initialPos; i < remainingBytes; i++) {
            if (sequence[iSequence] == data[i]) iSequence++;
            else if (iSequence > 0) iSequence = 0;

            if (sequence.length == iSequence) return i - sequence.length;
        }

//            remainingBytes -= bytesToRead;
//        }

        // Target sequence not found
        return -1;
    }

    public static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    public static File getDownloadsFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static OutputStream openNewDownloadOutputStream(
            @NonNull final Context context,
            @NonNull final String fileName,
            @NonNull final String mimeType
    ) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openNewDownloadOutputStreamQ(context, fileName, mimeType);
        } else {
            return openNewDownloadOutputStreamLegacy(fileName);
        }
    }

    private static OutputStream openNewDownloadOutputStreamLegacy(@NonNull final String fileName) throws IOException {
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (null == downloadsFolder) throw new IOException("Downloads folder not found");

        File target = new File(downloadsFolder, fileName);
        if (!target.exists() && !target.createNewFile())
            throw new IOException("Could not create new file in downloads folder");

        return getOutputStream(target);
    }

    // https://gitlab.com/commonsguy/download-wrangler/blob/master/app/src/main/java/com/commonsware/android/download/DownloadRepository.kt
    @TargetApi(29)
    private static OutputStream openNewDownloadOutputStreamQ(
            @NonNull final Context context,
            @NonNull final String fileName,
            @NonNull final String mimeType) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

        ContentResolver resolver = context.getContentResolver();
        Uri targetFileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (null == targetFileUri) throw new IOException("Target URI could not be formed");

        return resolver.openOutputStream(targetFileUri);
    }

    public static class MemoryUsageFigures {
        private final long freeMemBytes;
        private final long totalMemBytes;

        // Check https://stackoverflow.com/questions/56663624/how-to-get-free-and-total-size-of-each-storagevolume
        // to see if a better solution compatible with API21 has been found
        // TODO - encapsulate the reflection trick used by getVolumePath
        public MemoryUsageFigures(@NonNull Context context, @NonNull DocumentFile f) {
            String fullPath = getFullPathFromTreeUri(context, f.getUri(), true); // Oh so dirty !!
            if (fullPath != null) {
                File file = new File(fullPath);
                this.freeMemBytes = file.getFreeSpace();
                this.totalMemBytes = file.getTotalSpace();
            } else {
                this.freeMemBytes = 0;
                this.totalMemBytes = 0;
            }
        }

        public double getFreeUsageRatio100() {
            return freeMemBytes * 100.0 / totalMemBytes;
        }

        public double getTotalSpaceMb() {
            return totalMemBytes * 1.0 / (1024 * 1024);
        }

        public double getfreeUsageMb() {
            return freeMemBytes * 1.0 / (1024 * 1024);
        }

        public String formatFreeUsageMb() {
            return Math.round(getfreeUsageMb()) + "/" + Math.round(getfreeUsageMb());
        }
    }

    /**
     * Reset the app's persisted I/O permissions :
     * - persist I/O permissions for the given new Uri
     * - keep existing persisted I/O permissions for the given optional Uri
     * <p>
     * NB : if the optional Uri has no persisted permissions, this call won't create them
     *
     * @param context Context to use
     * @param newUri  New Uri to add to the persisted I/O permission
     * @param keepUri Uri to keep in the persisted I/O permissions, if already set
     */
    public static void persistNewUriPermission(@NonNull final Context context, @NonNull final Uri newUri, @Nullable final Uri keepUri) {
        ContentResolver contentResolver = context.getContentResolver();
        if (!isUriPermissionPersisted(contentResolver, newUri)) {
            Timber.d("Persisting Uri permission for %s", newUri);
            // Release previous access permissions, if different than the new one
            revokePreviousPermissions(contentResolver, newUri, keepUri);
            // Persist new access permission
            contentResolver.takePersistableUriPermission(newUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    /**
     * Check if the given Uri has persisted I/O permissions
     *
     * @param resolver ContentResolver to use
     * @param uri      Uri to check
     * @return true if the given Uri has persisted I/O permissions
     */
    private static boolean isUriPermissionPersisted(@NonNull final ContentResolver resolver, @NonNull final Uri uri) {
        String treeUriId = DocumentsContract.getTreeDocumentId(uri);
        for (UriPermission p : resolver.getPersistedUriPermissions()) {
            if (DocumentsContract.getTreeDocumentId(p.getUri()).equals(treeUriId)) {
                Timber.d("Uri permission already persisted for %s", uri);
                return true;
            }
        }
        return false;
    }

    /**
     * Revoke persisted Uri I/O permissions to the exception of given Uri's
     *
     * @param resolver   ContentResolver to use
     * @param exceptions Uri's whose permissions won't be revoked
     */
    private static void revokePreviousPermissions(@NonNull final ContentResolver resolver, @NonNull final Uri... exceptions) {
        // Unfortunately, the content Uri of the selected resource is not exactly the same as the one stored by ContentResolver
        // -> solution is to compare their TreeDocumentId instead
        List<String> exceptionIds = Stream.of(exceptions).withoutNulls().map(DocumentsContract::getTreeDocumentId).toList();
        for (UriPermission p : resolver.getPersistedUriPermissions())
            if (!exceptionIds.contains(DocumentsContract.getTreeDocumentId(p.getUri())))
                resolver.releasePersistableUriPermission(p.getUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (resolver.getPersistedUriPermissions().size() <= exceptionIds.size()) {
            Timber.d("Permissions revoked successfully");
        } else {
            Timber.d("Failed to revoke permissions");
        }
    }

    private static NameFilter createNameFilterEquals(@NonNull final String name) {
        return displayName -> displayName.equalsIgnoreCase(name);
    }

    @FunctionalInterface
    public interface NameFilter {

        /**
         * Tests whether or not the specified abstract display name should be included in a pathname list.
         *
         * @param displayName The abstract display name to be tested
         * @return <code>true</code> if and only if <code>displayName</code> should be included
         */
        boolean accept(@NonNull String displayName);
    }
}

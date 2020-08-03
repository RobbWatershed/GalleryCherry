package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImportActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.events.ServiceDestroyedEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportProgressNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ImageHelper;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

/**
 * Service responsible for importing an existing Hentoid library.
 *
 * @see UpdateCheckService
 */
public class ImportService extends IntentService {

    private static final int NOTIFICATION_ID = 1;

    private static boolean running;
    private ServiceNotificationManager notificationManager;


    public ImportService() {
        super(ImportService.class.getName());
    }

    public static Intent makeIntent(@NonNull Context context) {
        return new Intent(context, ImportService.class);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;
        notificationManager = new ServiceNotificationManager(this, NOTIFICATION_ID);
        notificationManager.cancel();
        notificationManager.startForeground(new ImportStartNotification());

        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        running = false;
        if (notificationManager != null) notificationManager.cancel();
        EventBus.getDefault().post(new ServiceDestroyedEvent(ServiceDestroyedEvent.Service.IMPORT));
        Timber.w("Service destroyed");

        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        // True if the user has asked for a cleanup when calling import from Preferences
        boolean doRename = false;
        boolean doCleanAbsent = false;
        boolean doCleanNoImages = false;

        if (intent != null && intent.getExtras() != null) {
            ImportActivityBundle.Parser parser = new ImportActivityBundle.Parser(intent.getExtras());
            doRename = parser.getRefreshRename();
            doCleanAbsent = parser.getRefreshCleanAbsent();
            doCleanNoImages = parser.getRefreshCleanNoImages();
        }
        startImport(doRename, doCleanAbsent, doCleanNoImages);
    }

    private void eventProgress(int step, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, step, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int step, int nbBooks, int booksOK, int booksKO, DocumentFile cleanupLogFile) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, step, booksOK, booksKO, nbBooks, cleanupLogFile));
    }

    private void trace(int priority, int chapter, List<LogUtil.LogEntry> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        boolean isError = (priority > Log.INFO);
        if (null != memoryLog) memoryLog.add(new LogUtil.LogEntry(s, chapter, isError));
    }


    /**
     * Import books from known source folders
     *
     * @param rename        True if the user has asked for a folder renaming when calling import from Preferences
     * @param cleanNoJSON   True if the user has asked for a cleanup of folders with no JSONs when calling import from Preferences
     * @param cleanNoImages True if the user has asked for a cleanup of folders with no images when calling import from Preferences
     */
    private void startImport(boolean rename, boolean cleanNoJSON, boolean cleanNoImages) {
        int booksOK = 0;                        // Number of books imported
        int booksKO = 0;                        // Number of folders found with no valid book inside
        int nbFolders = 0;                      // Number of folders found with no content but subfolders
        Content content = null;
        List<LogUtil.LogEntry> log = new ArrayList<>();

        final FileHelper.NameFilter imageNames = displayName -> ImageHelper.isImageExtensionSupported(FileHelper.getExtension(displayName));

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(this, Preferences.getStorageUri());
        if (null == rootFolder) {
            Timber.e("Root folder is not defined (%s)", Preferences.getStorageUri());
            return;
        }

        ContentProviderClient client = this.getContentResolver().acquireContentProviderClient(Uri.parse(Preferences.getStorageUri()));
        if (null == client) return;

        List<DocumentFile> bookFolders = new ArrayList<>();
        CollectionDAO dao = new ObjectBoxDAO(this);

        try {
            // 1st pass : count subfolders of every site folder
            List<DocumentFile> siteFolders = FileHelper.listFolders(this, rootFolder, client);
            int foldersProcessed = 1;
            for (DocumentFile f : siteFolders) {
                bookFolders.addAll(FileHelper.listFolders(this, f, client));
                eventProgress(2, siteFolders.size(), foldersProcessed++, 0);
            }
            eventComplete(2, siteFolders.size(), siteFolders.size(), 0, null);
            notificationManager.startForeground(new ImportProgressNotification(this.getResources().getString(R.string.starting_import), 0, 0));

            // 2nd pass : scan every folder for a JSON file or subdirectories
            String enabled = getApplication().getResources().getString(R.string.enabled);
            String disabled = getApplication().getResources().getString(R.string.disabled);
            trace(Log.DEBUG, 0, log, "Import books starting - initial detected count : %s", bookFolders.size() + "");
            trace(Log.INFO, 0, log, "Rename folders %s", (rename ? enabled : disabled));
            trace(Log.INFO, 0, log, "Remove folders with no JSONs %s", (cleanNoJSON ? enabled : disabled));
            trace(Log.INFO, 0, log, "Remove folders with no images %s", (cleanNoImages ? enabled : disabled));

            // Flag DB content for cleanup
            dao.flagAllInternalBooks();
            dao.flagAllErrorBooksWithJson();

            for (int i = 0; i < bookFolders.size(); i++) {
                DocumentFile bookFolder = bookFolders.get(i);

                // Detect the presence of images if the corresponding cleanup option has been enabled
                if (cleanNoImages) {
                    List<DocumentFile> imageFiles = FileHelper.listFiles(this, bookFolder, client, imageNames);
                    List<DocumentFile> subfolders = FileHelper.listFolders(this, bookFolder, client);
                    if (imageFiles.isEmpty() && subfolders.isEmpty()) { // No supported images nor subfolders
                        booksKO++;
                        boolean success = bookFolder.delete();
                        trace(Log.INFO, 1, log, "[Remove no image %s] Folder %s", success ? "OK" : "KO", bookFolder.getUri().toString());
                        continue;
                    }
                }

                // Find the corresponding flagged book in the library
                Content existingFlaggedContent = dao.selectContentByFolderUri(bookFolder.getUri().toString(), true);

                // Detect JSON and try to parse it
                try {
                    content = importJson(bookFolder, client);
                    if (content != null) {
                        // If the book exists and is flagged for deletion, delete it to make way for a new import (as intended)
                        if (existingFlaggedContent != null)
                            dao.deleteContent(existingFlaggedContent);

                        // If the very same book still exists in the DB at this point, it means it's present in the queue
                        // => don't import it even though it has a JSON file; it has been re-queued after being downloaded or viewed once
                        Content existingDuplicate = dao.selectContentBySourceAndUrl(content.getSite(), content.getUrl());
                        if (existingDuplicate != null && !existingDuplicate.isFlaggedForDeletion()) {
                            booksKO++;
                            trace(Log.INFO, 2, log, "Import book KO! (already in queue) : %s", bookFolder.getUri().toString());
                            continue;
                        }

                        List<ImageFile> contentImages;
                        if (content.getImageFiles() != null)
                            contentImages = content.getImageFiles();
                        else contentImages = new ArrayList<>();

                        if (rename) {
                            String canonicalBookFolderName = ContentHelper.formatBookFolderName(content);

                            List<String> currentPathParts = bookFolder.getUri().getPathSegments();
                            String[] bookUriParts = currentPathParts.get(currentPathParts.size() - 1).split(":");
                            String[] bookPathParts = bookUriParts[bookUriParts.length - 1].split("/");
                            String bookFolderName = bookPathParts[bookPathParts.length - 1];

                            if (!canonicalBookFolderName.equalsIgnoreCase(bookFolderName)) {
                                if (renameFolder(bookFolder, content, client, canonicalBookFolderName)) {
                                    trace(Log.INFO, 2, log, "[Rename OK] Folder %s renamed to %s", bookFolderName, canonicalBookFolderName);
                                } else {
                                    trace(Log.WARN, 2, log, "[Rename KO] Could not rename file %s to %s", bookFolderName, canonicalBookFolderName);
                                }
                            }
                        }

                        // Attach file Uri's to the book's images
                        List<DocumentFile> imageFiles = FileHelper.listFiles(this, bookFolder, client, imageNames);
                        if (!imageFiles.isEmpty()) { // No images described in the JSON -> recreate them
                            if (contentImages.isEmpty()) {
                                contentImages = ContentHelper.createImageListFromFiles(imageFiles);
                                content.setImageFiles(contentImages);
                                content.getCover().setUrl(content.getCoverImageUrl());
                            } else { // Existing images described in the JSON -> map them
                                contentImages = ContentHelper.matchFilesToImageList(imageFiles, contentImages);
                                // If no cover is defined, get it too
                                if (StatusContent.UNHANDLED_ERROR == content.getCover().getStatus()) {
                                    Optional<DocumentFile> file = Stream.of(imageFiles).filter(f -> f.getName() != null && f.getName().startsWith(Consts.THUMB_FILE_NAME)).findFirst();
                                    if (file.isPresent()) {
                                        ImageFile cover = new ImageFile(0, content.getCoverImageUrl(), StatusContent.DOWNLOADED, content.getQtyPages());
                                        cover.setName(Consts.THUMB_FILE_NAME);
                                        cover.setFileUri(file.get().getUri().toString());
                                        cover.setIsCover(true);
                                        contentImages.add(0, cover);
                                    }
                                }
                                content.setImageFiles(contentImages);
                            }
                        }
                        content.computeSize();
                        dao.insertContent(content);
                        trace(Log.INFO, 2, log, "Import book OK : %s", bookFolder.getUri().toString());
                    } else { // JSON not found
                        List<DocumentFile> subfolders = FileHelper.listFolders(this, bookFolder, client);
                        if (!subfolders.isEmpty()) // Folder doesn't contain books but contains subdirectories
                        {
                            bookFolders.addAll(subfolders);
                            trace(Log.INFO, 2, log, "Subfolders found in : %s", bookFolder.getUri().toString());
                            nbFolders++;
                            continue;
                        } else { // No JSON nor any subdirectory
                            trace(Log.WARN, 2, log, "Import book KO! (no JSON found) : %s", bookFolder.getUri().toString());
                            // Deletes the folder if cleanup is active
                            if (cleanNoJSON) {
                                boolean success = bookFolder.delete();
                                trace(Log.INFO, 2, log, "[Remove no JSON %s] Folder %s", success ? "OK" : "KO", bookFolder.getUri().toString());
                            }
                        }
                    }

                    if (null == content) booksKO++;
                    else booksOK++;
                } catch (ParseException jse) {
                    // If the book is still present in the DB, regenerate the JSON and unflag the book
                    if (existingFlaggedContent != null) {
                        try {
                            DocumentFile newJson = JsonHelper.jsonToFile(this, JsonContent.fromEntity(existingFlaggedContent), JsonContent.class, bookFolder);
                            existingFlaggedContent.setJsonUri(newJson.getUri().toString());
                            existingFlaggedContent.setFlaggedForDeletion(false);
                            dao.insertContent(existingFlaggedContent);
                            trace(Log.INFO, 2, log, "Import book OK (JSON regenerated) : %s", bookFolder.getUri().toString());
                            booksOK++;
                        } catch (IOException ioe) {
                            Timber.w(ioe);
                            trace(Log.ERROR, 2, log, "Import book ERROR while regenerating JSON : %s for Folder %s", jse.getMessage(), bookFolder.getUri().toString());
                            booksKO++;
                        }
                    } else { // If not, rebuild the book and regenerate the JSON according to stored data
                        try {
                            List<String> parentFolder = new ArrayList<>();
                            // Try and detect the site according to the parent folder
                            String[] parents = bookFolder.getUri().getPath().split("/"); // _not_ File.separator but the universal Uri separator
                            if (parents.length > 1) {
                                for (Site s : Site.values())
                                    if (parents[parents.length - 2].equalsIgnoreCase(s.getFolder())) {
                                        parentFolder.add(s.getFolder());
                                        break;
                                    }
                            }
                            // Scan the folder
                            Content storedContent = ImportHelper.scanBookFolder(this, bookFolder, client, parentFolder, StatusContent.DOWNLOADED, null, null);
                            DocumentFile newJson = JsonHelper.jsonToFile(this, JsonContent.fromEntity(storedContent), JsonContent.class, bookFolder);
                            storedContent.setJsonUri(newJson.getUri().toString());
                            dao.insertContent(storedContent);
                            trace(Log.INFO, 2, log, "Import book OK (Content regenerated) : %s", bookFolder.getUri().toString());
                            booksOK++;
                        } catch (IOException ioe) {
                            Timber.w(ioe);
                            trace(Log.ERROR, 2, log, "Import book ERROR while regenerating Content : %s for Folder %s", jse.getMessage(), bookFolder.getUri().toString());
                            booksKO++;
                        }
                    }
                } catch (Exception e) {
                    Timber.w(e);
                    if (null == content)
                        content = new Content().setTitle("none").setSite(Site.NONE).setUrl("");
                    booksKO++;
                    trace(Log.ERROR, 2, log, "Import book ERROR : %s for Folder %s", e.getMessage(), bookFolder.getUri().toString());
                }
                String bookName = (null == bookFolder.getName()) ? "" : bookFolder.getName();
                notificationManager.notify(new ImportProgressNotification(bookName, booksOK + booksKO, bookFolders.size() - nbFolders));
                eventProgress(3, bookFolders.size() - nbFolders, booksOK, booksKO);
            }
            trace(Log.INFO, 3, log, "Import books complete - %s OK; %s KO; %s final count", booksOK + "", booksKO + "", bookFolders.size() - nbFolders + "");
            eventComplete(3, bookFolders.size(), booksOK, booksKO, null);

            // 3rd pass : Import queue JSON
            DocumentFile queueFile = FileHelper.findFile(this, rootFolder, client, Consts.QUEUE_JSON_FILE_NAME);
            if (queueFile != null) importQueue(queueFile, dao, log);
            else trace(Log.INFO, 4, log, "No queue file found");
        } finally {
            // Write log in root folder
            DocumentFile logFile = LogUtil.writeLog(this, buildLogInfo(rename || cleanNoJSON || cleanNoImages, log));

            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();

            dao.deleteAllFlaggedBooks(true);
            dao.cleanup();

            eventComplete(4, bookFolders.size(), booksOK, booksKO, logFile);
            notificationManager.notify(new ImportCompleteNotification(booksOK, booksKO));
        }

        stopForeground(true);
        stopSelf();
    }

    private LogUtil.LogInfo buildLogInfo(boolean cleanup, @NonNull List<LogUtil.LogEntry> log) {
        LogUtil.LogInfo logInfo = new LogUtil.LogInfo();
        logInfo.setLogName(cleanup ? "Cleanup" : "Import");
        logInfo.setFileName(cleanup ? "cleanup_log" : "import_log");
        logInfo.setNoDataMessage("No content detected.");
        logInfo.setLog(log);
        return logInfo;
    }

    private boolean renameFolder(@NonNull DocumentFile folder, @NonNull final Content content, @NonNull ContentProviderClient client, @NonNull final String newName) {
        try {
            if (folder.renameTo(newName)) {
                // 1- Update the book folder's URI
                content.setStorageUri(folder.getUri().toString());
                // 2- Update the JSON's URI
                DocumentFile jsonFile = FileHelper.findFile(this, folder, client, Consts.JSON_FILE_NAME_V2);
                if (jsonFile != null) content.setJsonUri(jsonFile.getUri().toString());
                // 3- Update the image's URIs -> will be done by the next block back in startImport
                return true;
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return false;
    }

    private void importQueue(@NonNull DocumentFile queueFile, @NonNull CollectionDAO dao, @NonNull List<LogUtil.LogEntry> log) {
        trace(Log.INFO, 4, log, "Queue JSON found");
        eventProgress(4, -1, 0, 0);
        JsonContentCollection contentCollection = deserialiseQueueJson(queueFile);
        if (null != contentCollection) {
            int queueSize = (int) dao.countAllQueueBooks();
            eventProgress(4, queueSize, 0, 0);
            List<Content> queuedContent = contentCollection.getQueue();
            trace(Log.INFO, 4, log, "Queue JSON deserialized : %s books detected", queuedContent.size() + "");
            List<QueueRecord> lst = new ArrayList<>();
            int count = 1;
            for (Content c : queuedContent) {
                Content duplicate = dao.selectContentBySourceAndUrl(c.getSite(), c.getUrl());
                if (null == duplicate) {
                    long newContentId = dao.insertContent(c);
                    lst.add(new QueueRecord(newContentId, queueSize++));
                }
                eventProgress(4, queueSize, count++, 0);
            }
            dao.updateQueue(lst);
            trace(Log.INFO, 4, log, "Import queue succeeded");
        } else {
            trace(Log.INFO, 4, log, "Import queue failed : Queue JSON unreadable");
        }
    }

    private JsonContentCollection deserialiseQueueJson(@NonNull DocumentFile jsonFile) {
        JsonContentCollection result;
        try {
            result = JsonHelper.jsonToObject(this, jsonFile, JsonContentCollection.class);
        } catch (IOException e) {
            Timber.w(e);
            return null;
        }
        return result;
    }

    @Nullable
    private Content importJson(@NonNull DocumentFile folder, @NonNull ContentProviderClient client) throws ParseException {
        DocumentFile file = FileHelper.findFile(this, folder, client, Consts.JSON_FILE_NAME_V2);
        if (file != null) return importJsonV2(file, folder);

        Timber.w("Book folder %s : no JSON file found !", folder.getUri());

        return null;
    }

    @CheckResult
    private Content importJsonV2(@NonNull final DocumentFile json, @NonNull final DocumentFile parentFolder) throws ParseException {
        try {
            JsonContent content = JsonHelper.jsonToObject(this, json, JsonContent.class);
            Content result = content.toEntity();
            result.setJsonUri(json.getUri().toString());
            result.setStorageUri(parentFolder.getUri().toString());

            if (result.getStatus() != StatusContent.DOWNLOADED
                    && result.getStatus() != StatusContent.ERROR) {
                result.setStatus(StatusContent.MIGRATED);
            }

            return result;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (v2) file");
            throw new ParseException("Error reading JSON (v2) file : " + e.getMessage(), e);
        }
    }
}

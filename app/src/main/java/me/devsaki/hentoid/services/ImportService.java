package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;

/**
 * Service responsible for importing an existing library.
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

    public static Intent makeIntent(Context context) {
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

        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        running = false;
        Timber.w("Service destroyed");

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        // True if the user has asked for a cleanup when calling import from Preferences
        boolean doCleanup = false;

        if (intent != null) {
            doCleanup = intent.getBooleanExtra("cleanup", false);
        }
        startImport(doCleanup);
    }

    private void eventProgress(Content content, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_PROGRESS, content, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int nbBooks, int booksOK, int booksKO, File cleanupLogFile) {
        EventBus.getDefault().postSticky(new ImportEvent(ImportEvent.EV_COMPLETE, booksOK, booksKO, nbBooks, cleanupLogFile));
    }

    /**
     * Import books from known source folders
     *
     * @param cleanup True if the user has asked for a cleanup when calling import from Preferences
     */
    private void startImport(boolean cleanup) {
        int booksOK = 0;
        int booksKO = 0;
        String message;
        List<String> cleanupLog = cleanup ? new ArrayList<>() : null;

        notificationManager.startForeground(new ImportStartNotification());

        List<File> files = Stream.of(Site.values())
                .map(site -> FileHelper.getSiteDownloadDir(this, site))
                .map(File::listFiles)
                .flatMap(Stream::of)
                .filter(File::isDirectory)
                .distinct() // Since there are two ASM Hentai sites ("ASM classic" and "ASM comics"), ASM values are duplicated => deduplicate list
                .collect(toList());

        Timber.i("Import books starting : %s books total", files.size());
        Timber.i("Cleanup %s", (cleanup ? "ENABLED" : "DISABLED"));
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);

            Content content = importJson(file);
            if (content != null) {
                if (cleanup) {
                    String canonicalBookDir = FileHelper.formatDirPath(content);

                    String[] currentPathParts = file.getAbsolutePath().split("/");
                    String currentBookDir = "/" + currentPathParts[currentPathParts.length - 2] + "/" + currentPathParts[currentPathParts.length - 1];

                    if (!canonicalBookDir.equals(currentBookDir)) {
                        String settingDir = Preferences.getRootFolderName();
                        if (settingDir.isEmpty()) {
                            settingDir = FileHelper.getDefaultDir(this, canonicalBookDir).getAbsolutePath();
                        }

                        if (FileHelper.renameDirectory(file, new File(settingDir, canonicalBookDir))) {
                            content.setStorageFolder(canonicalBookDir);
                            message = String.format("[Rename OK] Folder %s renamed to %s", currentBookDir, canonicalBookDir);
                        } else {
                            message = String.format("[Rename KO] Could not rename file %s to %s", currentBookDir, canonicalBookDir);
                        }
                        cleanupLog.add(message);
                        Timber.i(message);
                    }
                }
                HentoidDB.getInstance(this).insertContent(content);
                booksOK++;
                Timber.d("Import book OK : %s", file.getAbsolutePath());
            } else {
                booksKO++;
                Timber.w("Import book KO : %s", file.getAbsolutePath());
                // Deletes the folder if cleanup is active
                if (cleanup) {
                    boolean success = FileHelper.removeFile(file);
                    message = String.format("[Remove %s] Folder %s", success ? "OK" : "KO", file.getAbsolutePath());
                    cleanupLog.add(message);
                    Timber.i(message);
                }
            }

            eventProgress(content, files.size(), booksOK, booksKO);
        }
        Timber.i("Import books complete : %s OK; %s KO", booksOK, booksKO);

        // Write cleanup log in root folder
        File cleanupLogFile = null;
        if (cleanup) cleanupLogFile = writeCleanupLog(cleanupLog);

        eventComplete(files.size(), booksOK, booksKO, cleanupLogFile);
        notificationManager.notify(new ImportCompleteNotification(booksOK, booksKO));

        stopForeground(true);
        stopSelf();
    }

    private File writeCleanupLog(List<String> log) {
        // Create the log
        StringBuilder logStr = new StringBuilder();
        logStr.append("Cleanup log : begin").append(System.getProperty("line.separator"));
        if (log.isEmpty())
            logStr.append("No activity to report - All folder names are formatted as expected.");
        else for (String line : log)
            logStr.append(line).append(System.getProperty("line.separator"));
        logStr.append("Cleanup log : end");

        // Save it
        File root;
        try {

            String settingDir = Preferences.getRootFolderName();
            if (settingDir.isEmpty()) {
                root = FileHelper.getDefaultDir(this, "");
            } else {
                root = new File(settingDir);
            }
            File cleanupLogFile = new File(root, "cleanup_log.txt");
            FileHelper.saveBinaryInFile(cleanupLogFile, logStr.toString().getBytes());
            return cleanupLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }


    private static Content importJson(File folder) {
        File json = new File(folder, Consts.JSON_FILE_NAME_V2); // (v2) JSON file format
        if (json.exists()) return importJsonV2(json);

        Timber.w("Book folder %s : no JSON file found !", folder.getAbsolutePath());

        return null;
    }

    @Nullable
    @CheckResult
    private static Content importJsonV2(File json) {
        try {
            Content content = JsonHelper.jsonToObject(json, Content.class);

            if (null == content.getAuthor()) content.populateAuthor();

            String fileRoot = Preferences.getRootFolderName();
            content.setStorageFolder(json.getAbsoluteFile().getParent().substring(fileRoot.length()));

            if (content.getStatus() != StatusContent.DOWNLOADED
                    && content.getStatus() != StatusContent.ERROR) {
                content.setStatus(StatusContent.MIGRATED);
            }

            return content;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (v2) file");
        }
        return null;
    }
}

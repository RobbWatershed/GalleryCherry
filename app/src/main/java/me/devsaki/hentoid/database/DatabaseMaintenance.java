package me.devsaki.hentoid.database;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import timber.log.Timber;

public class DatabaseMaintenance {

    private DatabaseMaintenance() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Clean up and upgrade database
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    public static void performDatabaseHousekeeping(@NonNull Context context) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);

        Timber.d("Content item(s) count: %s", db.countContentEntries());

        // Perform functional data updates
        performDatabaseCleanups(db);
    }

    private static void performDatabaseCleanups(@NonNull ObjectBoxDB db) {
        // Set items that were being downloaded in previous session as paused
        Timber.i("Updating queue status : start");
        db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
        Timber.i("Updating queue status : done");

        // Add back in the queue isolated DOWNLOADING or PAUSED books that aren't in the queue (since version code 106 / v1.8.0)
        Timber.i("Moving back isolated items to queue : start");
        List<Content> contents = db.selectContentByStatus(StatusContent.PAUSED);
        List<Content> queueContents = db.selectQueueContents();
        contents.removeAll(queueContents);
        if (!contents.isEmpty()) {
            int queueMaxPos = (int) db.selectMaxQueueOrder();
            for (Content c : contents) db.insertQueue(c.getId(), ++queueMaxPos);
        }
        Timber.i("Moving back isolated items to queue : done");

        // Clear temporary books created from browsing a book page without downloading it (since versionCode 60 / v1.3.7)
        Timber.i("Clearing temporary books : start");
        contents = db.selectContentByStatus(StatusContent.SAVED);
        Timber.i("Clearing temporary books : %s books detected", contents.size());
        for (Content c : contents) db.deleteContent(c);
        Timber.i("Clearing temporary books : done");

    }
}

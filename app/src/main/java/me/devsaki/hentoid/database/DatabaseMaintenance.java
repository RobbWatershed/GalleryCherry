package me.devsaki.hentoid.database;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.functions.BiConsumer;
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
    public static List<Observable<Float>> getCleanupTasks(@NonNull final Context context) {
        List<Observable<Float>> result = new ArrayList<>();
        result.add(createObservableFrom(context, DatabaseMaintenance::cleanContent));
        result.add(createObservableFrom(context, DatabaseMaintenance::clearTempContent));
        result.add(createObservableFrom(context, DatabaseMaintenance::computeContentSize));
        return result;
    }

    private static Observable<Float> createObservableFrom(@NonNull final Context context, BiConsumer<Context, ObservableEmitter<Float>> function) {
        return Observable.create(emitter -> function.accept(context, emitter));
    }

    private static void cleanContent(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Set items that were being downloaded in previous session as paused
            Timber.i("Updating queue status : start");
            db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
            Timber.i("Updating queue status : done");

            // Unflag all books marked for deletion
            Timber.i("Unflag books : start");
            db.flagContentById(db.selectAllFlaggedBooksQ().findIds(), false);
            Timber.i("Unflag books : done");

            // Add back in the queue isolated DOWNLOADING or PAUSED books that aren't in the queue (since version code 106 / v1.8.0)
            Timber.i("Moving back isolated items to queue : start");
            List<Content> contents = db.selectContentByStatus(StatusContent.PAUSED);
            List<Content> queueContents = db.selectQueueContents();
            contents.removeAll(queueContents);
            if (!contents.isEmpty()) {
                int queueMaxPos = (int) db.selectMaxQueueOrder();
                int max = contents.size();
                float pos = 1;
                for (Content c : contents) {
                    db.insertQueue(c.getId(), ++queueMaxPos);
                    emitter.onNext(pos++ / max);
                }
            }
            Timber.i("Moving back isolated items to queue : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void clearTempContent(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Clear temporary books created from browsing a book page without downloading it (since versionCode 60 / v1.3.7)
            Timber.i("Clearing temporary books : start");
            List<Content> contents = db.selectContentByStatus(StatusContent.SAVED);
            Timber.i("Clearing temporary books : %s books detected", contents.size());
            int max = contents.size();
            float pos = 1;
            for (Content c : contents) {
                db.deleteContent(c);
                emitter.onNext(pos++ / max);
            }
            Timber.i("Clearing temporary books : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }

    private static void computeContentSize(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Computing downloaded content size : start");
            List<Content> contents = db.selectDownloadedContentWithNoSize();
            Timber.i("Computing downloaded content size : %s books detected", contents.size());
            int max = contents.size();
            float pos = 1;
            for (Content c : contents) {
                c.computeSize();
                db.insertContent(c);
                emitter.onNext(pos++ / max);
            }
            Timber.i("Computing downloaded content size : done");
        } finally {
            db.closeThreadResources();
            emitter.onComplete();
        }
    }
}

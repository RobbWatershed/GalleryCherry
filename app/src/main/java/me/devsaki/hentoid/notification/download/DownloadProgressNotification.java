package me.devsaki.hentoid.notification.download;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import java.util.Locale;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.util.notification.Notification;

public class DownloadProgressNotification implements Notification {

    private final String title;

    private final int progress;

    private final int max;

    public DownloadProgressNotification(String title, int progress, int max) {
        this.title = title;
        this.progress = progress;
        this.max = max;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_cherry_icon)
                .setContentTitle(context.getString(R.string.downloading))
                .setContentText(title)
                .setContentInfo(getProgressString())
                .setProgress(max, progress, false)
                .setColor(ContextCompat.getColor(context, R.color.accent))
                .setContentIntent(getDefaultIntent(context))
                .setLocalOnly(true)
                .setOngoing(true)
                .build();
    }

    private PendingIntent getDefaultIntent(Context context) {
        Intent resultIntent = new Intent(context, QueueActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private String getProgressString() {
        return String.format(Locale.US, " %.2f%%", progress * 100.0 / max);
    }
}

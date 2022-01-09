package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import me.devsaki.hentoid.R;

public class DeleteWorker extends BaseDeleteWorker {
    public DeleteWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, R.id.delete_service_delete, parameters);
    }
}

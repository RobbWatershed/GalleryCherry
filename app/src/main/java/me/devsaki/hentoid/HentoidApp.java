package me.devsaki.hentoid;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.jakewharton.threetenabp.AndroidThreeTen;

import io.fabric.sdk.android.Fabric;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.notification.download.DownloadNotificationChannel;
import me.devsaki.hentoid.notification.maintenance.MaintenanceNotificationChannel;
import me.devsaki.hentoid.notification.update.UpdateNotificationChannel;
import me.devsaki.hentoid.services.DatabaseMaintenanceService;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.timber.CrashlyticsTree;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ShortcutHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastUtil;
import timber.log.Timber;

/**
 * Created by DevSaki on 20/05/2015.
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 */
public class HentoidApp extends Application {

    private static boolean beginImport;

    private static Application instance;

    public static Application getInstance() {
        return instance;
    }

    public static boolean isImportComplete() {
        return !beginImport;
    }

    public static void setBeginImport(boolean started) {
        HentoidApp.beginImport = started;
    }


    public static void trackDownloadEvent(String tag) {
        Bundle bundle = new Bundle();
        bundle.putString("tag", tag);
        FirebaseAnalytics.getInstance(instance).logEvent("Download", bundle);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Fabric.with(this, new Crashlytics());

        // Fix the SSLHandshake error with okhttp on Android 4.1-4.4 when server only supports TLS1.2
        // see https://github.com/square/okhttp/issues/2372 for more information
        try {
            ProviderInstaller.installIfNeeded(getApplicationContext());
        } catch (Exception e) {
            Timber.e(e, "Google Play ProviderInstaller exception");
        }

        // Init datetime
        AndroidThreeTen.init(this);

        // Timber
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());
        Timber.plant(new CrashlyticsTree());

        // Prefs
        Preferences.init(this);
        Preferences.performHousekeeping();

        // Init version number on first run
        if (0 == Preferences.getLastKnownAppVersionCode())
            Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);

        // Firebase
        boolean isAnalyticsEnabled = Preferences.isAnalyticsEnabled();
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isAnalyticsEnabled);

        // This code has been inherited from the FakkuDroid era; no documentation available
        // Best guess : allows networking on main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // DB housekeeping
        performDatabaseHousekeeping();

        // Init notification channels
        UpdateNotificationChannel.init(this);
        DownloadNotificationChannel.init(this);
        MaintenanceNotificationChannel.init(this);

        // Clears all previous notifications
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();

        // Run app update checks
        if (Preferences.isAutomaticUpdateEnabled()) {
            Intent intent = UpdateCheckService.makeIntent(this, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutHelper.buildShortcuts(this);
        }

        FirebaseAnalytics.getInstance(this).setUserProperty("color_theme", Integer.toString(Preferences.getColorTheme()));
    }

    // We have asked for permissions, but still denied.
    public static void reset(Activity activity) {
        ToastUtil.toast(R.string.reset);
        Preferences.setIsFirstRun(true);
        Intent intent = new Intent(activity, IntroActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        instance.startActivity(intent);
        activity.finish();
    }

    /**
     * Clean up and upgrade database
     */
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    private void performDatabaseHousekeeping() {
        HentoidDB oldDB = HentoidDB.getInstance(this);

        // Perform technical data updates that need to be done before app launches
        DatabaseMaintenance.performOldDatabaseUpdate(oldDB);

        // Launch a service that will perform non-structural DB housekeeping tasks
        Intent intent = DatabaseMaintenanceService.makeIntent(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}

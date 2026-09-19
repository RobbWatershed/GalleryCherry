package me.devsaki.hentoid.util.file

import android.Manifest.permission.ACCESS_LOCAL_NETWORK
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import androidx.core.content.ContextCompat

fun Context.checkPermission(code: String): Boolean {
    return ContextCompat.checkSelfPermission(this, code) == PERMISSION_GRANTED
}

fun Context.checkPermissions(vararg codes: String): Boolean {
    return codes.all { checkPermission(it) }
}

fun Activity.checkExternalStorageReadWritePermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
    return checkPermissions(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
}

fun Context.checkNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkPermission(POST_NOTIFICATIONS)
    } else true
}

fun Context.checkLocalNetworkPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
        checkPermission(ACCESS_LOCAL_NETWORK)
    } else true
}
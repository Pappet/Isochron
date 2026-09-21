package com.isochron.audit.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

private const val TAG = "AppSettings"

/**
 * Opens this app's entry in the system settings, on the permission page when the
 * device supports it.
 *
 * Needed whenever a permission was denied permanently: Android then silently ignores
 * further `requestPermissions` calls, so the in-app prompt becomes a dead button and
 * the system settings are the only remaining way to grant it.
 */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No activity available to open app settings", e)
    }
}

/**
 * Opens the system Wi-Fi settings so the user can switch the adapter on. Apps cannot
 * toggle Wi-Fi themselves since Android 10, so this is the only actionable fix we can
 * offer from a "Wi-Fi is off" banner.
 */
fun Context.openWifiSettings() {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No activity available to open Wi-Fi settings", e)
    }
}

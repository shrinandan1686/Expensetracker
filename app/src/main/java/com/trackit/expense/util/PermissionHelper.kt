package com.trackit.expense.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Utility for verifying system permissions required by TrackIt's core functionality.
 */
object PermissionHelper {

    /** Permissions that can be requested via the standard Android runtime dialog. */
    val runtimePermissions = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Returns true if all [runtimePermissions] are granted. */
    fun hasRuntimePermissions(context: Context): Boolean {
        return runtimePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Returns true if the device allows the app to draw overlays over other apps. */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** Returns true if the app is excluded from battery optimization. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Returns true if THE CORE APP IS FUNCTIONAL (SMS + Overlay). */
    fun isCoreFunctional(context: Context): Boolean {
        // Battery optimization is recommended but not strictly "blocking" for the overlay to appear
        // however for long-term stability it's best to include it in the core check.
        return hasRuntimePermissions(context) && hasOverlayPermission(context)
    }
}

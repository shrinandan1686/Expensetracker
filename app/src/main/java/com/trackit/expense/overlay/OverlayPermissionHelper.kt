package com.trackit.expense.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Utility for checking and requesting the [android.Manifest.permission.SYSTEM_ALERT_WINDOW]
 * permission at runtime.
 *
 * Android 6.0+ (API 23) requires this permission to be granted explicitly by the user
 * through the Settings screen (it cannot be granted via the normal runtime permission dialog).
 *
 * ## Typical flow
 * 1. On first launch, call [hasOverlayPermission].
 * 2. If false, show a rationale dialog in the UI explaining why it is needed.
 * 3. When the user agrees, call [openPermissionSettings] to send them to the Settings screen.
 * 4. On return to the app (in `onResume`), check [hasOverlayPermission] again.
 *
 * ## Why is this needed?
 * The [com.trackit.expense.overlay.ExpenseOverlayService] uses
 * [android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] to draw over all apps.
 * Without this permission, `windowManager.addView()` will throw a
 * [android.view.WindowManager.BadTokenException].
 */
object OverlayPermissionHelper {

    /**
     * Returns true if the app is allowed to draw over other apps.
     *
     * On API < 23, this permission is granted at install time and is always true.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Opens the system "Display over other apps" settings screen for this package.
     *
     * Needs [Intent.FLAG_ACTIVITY_NEW_TASK] because it is called from non-Activity contexts
     * (e.g., Application onCreate).
     */
    fun openPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Rationale message to show users before redirecting them to Settings.
     * Adjust wording to match your app's tone.
     */
    const val RATIONALE_TITLE   = "Enable Overlay Permission"
    const val RATIONALE_BODY    =
        "TrackIt needs to display a quick expense entry popup right after a UPI payment is " +
        "detected in your SMS. This popup appears briefly over your current app — no data " +
        "is read without your action.\n\nGo to Settings → Allow display over other apps → Enable for TrackIt."
    const val RATIONALE_CONFIRM = "Open Settings"
    const val RATIONALE_DISMISS = "Not Now"
}

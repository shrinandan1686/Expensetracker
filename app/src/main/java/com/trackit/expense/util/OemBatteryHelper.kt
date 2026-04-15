package com.trackit.expense.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Utility for sending users to OEM-specific battery optimisation screens.
 *
 * Xiaomi/MIUI, Samsung OneUI, OnePlus OxygenOS, OPPO ColorOS, Vivo FunTouch, and
 * Huawei EMUI all have proprietary battery managers that can kill background receivers.
 * This helper detects the OEM and launches the correct auto-start / battery-exempt
 * settings screen so the user can whitelist TrackIt.
 *
 * ## Usage
 * ```kotlin
 * if (OemBatteryHelper.hasOemBatterySettings(context)) {
 *     OemBatteryHelper.launchOemBatterySettings(context)
 * }
 * ```
 */
object OemBatteryHelper {

    private const val TAG = "OemBatteryHelper"

    /** Returns true if the running device has a known OEM battery settings screen. */
    fun hasOemBatterySettings(context: Context): Boolean = buildOemIntent(context) != null

    /**
     * Launches the OEM battery / auto-start settings screen.
     * @return true if the activity was started, false if no known screen exists or launch failed.
     */
    fun launchOemBatterySettings(context: Context): Boolean {
        val intent = buildOemIntent(context) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch OEM battery settings: ${e.message}")
            false
        }
    }

    /**
     * Human-readable OEM name for use in warning banners.
     * Returns null on stock Android.
     */
    fun oemName(): String? = when {
        isXiaomi()  -> "Xiaomi (MIUI)"
        isSamsung() -> "Samsung (OneUI)"
        isOnePlus() -> "OnePlus (OxygenOS)"
        isOppo()    -> "OPPO / Realme (ColorOS)"
        isVivo()    -> "Vivo (FunTouch OS)"
        isHuawei()  -> "Huawei / Honor (EMUI)"
        else        -> null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildOemIntent(context: Context): Intent? {
        val candidates = mutableListOf<Intent>()
        val pkg = context.packageName

        when {
            // ── Xiaomi / MIUI ─────────────────────────────────────────────────
            isXiaomi() -> {
                // MIUI autostart manager (primary)
                candidates += Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                    putExtra("package_name", pkg)
                }
                // MIUI 12+ PowerKeeper hidden apps (battery saver exemption)
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", pkg)
                    putExtra("package_label",
                        context.applicationInfo.loadLabel(context.packageManager).toString())
                }
            }

            // ── Samsung OneUI ──────────────────────────────────────────────────
            isSamsung() -> {
                // Device Care → Battery → Sleeping apps exclusion list
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                }
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.sm.battery",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                }
            }

            // ── OnePlus OxygenOS ───────────────────────────────────────────────
            isOnePlus() -> {
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
            }

            // ── OPPO / Realme ColorOS ─────────────────────────────────────────
            isOppo() -> {
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.FakeStartupAppListActivity"
                    )
                }
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                }
            }

            // ── Vivo FunTouch OS ──────────────────────────────────────────────
            isVivo() -> {
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            }

            // ── Huawei / Honor EMUI ───────────────────────────────────────────
            isHuawei() -> {
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                candidates += Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
            }
        }

        val pm = context.packageManager
        return candidates.firstOrNull { intent ->
            intent.component?.let { comp ->
                runCatching { pm.getActivityInfo(comp, 0); true }.getOrDefault(false)
            } ?: (intent.resolveActivity(pm) != null)
        }
    }

    private fun isXiaomi()  = Build.MANUFACTURER.equals("Xiaomi",   ignoreCase = true)
    private fun isSamsung() = Build.MANUFACTURER.equals("Samsung",  ignoreCase = true)
    private fun isOnePlus() = Build.MANUFACTURER.equals("OnePlus",  ignoreCase = true)
    private fun isOppo()    = Build.MANUFACTURER.equals("OPPO",     ignoreCase = true) ||
                              Build.MANUFACTURER.equals("Realme",   ignoreCase = true)
    private fun isVivo()    = Build.MANUFACTURER.equals("Vivo",     ignoreCase = true)
    private fun isHuawei()  = Build.MANUFACTURER.equals("Huawei",   ignoreCase = true) ||
                              Build.MANUFACTURER.equals("Honor",    ignoreCase = true)
}

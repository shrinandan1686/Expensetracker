package com.trackit.expense

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.trackit.expense.presentation.navigation.NavGraph
import com.trackit.expense.presentation.navigation.Screen
import com.trackit.expense.presentation.navigation.TrackItBottomNav
import com.trackit.expense.presentation.theme.BudgetError
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.TrackItPrimary
import com.trackit.expense.presentation.theme.TrackItTheme
import com.google.firebase.auth.FirebaseAuth
import com.trackit.expense.sms.SmsReceiver
import com.trackit.expense.util.OemBatteryHelper
import com.trackit.expense.util.PendingExpenseStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity entry point for TrackIt.
 *
 * ## Per-resume hardening checks (in [onResume])
 * 1. **Core permissions** – SMS + Notification. If missing → blocking dialog (existing).
 * 2. **Overlay permission** – [Settings.canDrawOverlays]. If lost → non-blocking banner.
 * 3. **SMS receiver health** – component enabled state. If OEM killed it → banner with
 *    "Tap to restart" that re-enables the component AND opens the OEM battery screen.
 *
 * ## On-create hardening
 * 4. **Crash recovery** – [PendingExpenseStore]. If a prior session left an unsaved
 *    expense → dismissible card "We saved your incomplete expense" with a CTA to AddExpenseScreen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferenceRepository: com.trackit.expense.domain.repository.PreferenceRepository
    @Inject lateinit var settingsRepository:   com.trackit.expense.domain.repository.SettingsRepository
    @Inject lateinit var pendingExpenseStore:  PendingExpenseStore

    // ── Compose-observable state ──────────────────────────────────────────────
    private var isCoreFunctional    by mutableStateOf(true)
    private var overlayPermissionOk by mutableStateOf(true)
    private var smsReceiverAlive    by mutableStateOf(true)
    private var showPendingRestore  by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check crash recovery once per cold start
        showPendingRestore = pendingExpenseStore.hasPendingExpense

        setContent {
            val themeMode    by settingsRepository.themeMode.collectAsState(initial = com.trackit.expense.domain.repository.ThemeMode.SYSTEM)
            val dynamicColor by settingsRepository.dynamicColorEnabled.collectAsState(initial = true)
            val darkTheme = when (themeMode) {
                com.trackit.expense.domain.repository.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.trackit.expense.domain.repository.ThemeMode.LIGHT  -> false
                com.trackit.expense.domain.repository.ThemeMode.DARK   -> true
            }

            TrackItTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                val isOnboardingCompleted = remember { preferenceRepository.isOnboardingCompleted() }
                val isAuthenticated = remember { FirebaseAuth.getInstance().currentUser != null }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val startDest = when {
                        !isAuthenticated      -> Screen.Login.route
                        !isOnboardingCompleted -> Screen.Onboarding.route
                        else                   -> Screen.Home.route
                    }

                    Scaffold(
                        containerColor = DarkBackground,
                        bottomBar = { 
                        Column {
                            // ── In-app status banners (non-blocking) ─────────
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Overlay permission lost banner (Hidden per user request)
                                /*
                                if (!overlayPermissionOk) {
                                    StatusBanner(
                                        icon    = Icons.Default.CloudOff,
                                        tint    = Color(0xFFFF9800),
                                        text    = "Overlay permission revoked — auto-entry disabled",
                                        action  = "Fix",
                                        onAction = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                startActivity(Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    android.net.Uri.parse("package:$packageName")
                                                ))
                                            }
                                        }
                                    )
                                }
                                */

                                // SMS receiver killed by OEM
                                if (!smsReceiverAlive) {
                                    StatusBanner(
                                        icon    = Icons.Default.SmsFailed,
                                        tint    = BudgetError,
                                        text    = "SMS detection paused${OemBatteryHelper.oemName()?.let { " ($it)" } ?: ""}. Tap to restart.",
                                        action  = "Restart",
                                        onAction = { restartSmsReceiver() }
                                    )
                                }

                                // Crash recovery
                                if (showPendingRestore) {
                                    PendingExpenseRestoreCard(
                                        onRestore = {
                                            val pending = pendingExpenseStore.loadPending()
                                            pendingExpenseStore.clearPending()
                                            showPendingRestore = false
                                            if (pending != null) {
                                                navController.navigate(
                                                    Screen.AddExpense.createRoute(
                                                        amount   = pending.amount.toString(),
                                                        merchant = pending.merchant,
                                                        account  = pending.account
                                                    )
                                                )
                                            }
                                        },
                                        onDismiss = {
                                            pendingExpenseStore.clearPending()
                                            showPendingRestore = false
                                        }
                                    )
                                }
                            }
                            TrackItBottomNav(navController) 
                        }
                    }) { padding ->
                        Box(Modifier.padding(padding)) {
                            NavGraph(navController = navController, startDestination = startDest)

                        }
                    }
                }

                // ── Critical Permission Guard (blocking) ─────────────────────
                if (isAuthenticated && isOnboardingCompleted && !isCoreFunctional) {
                    PermissionsRequiredDialog()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 1. Core permissions (SMS + notification)
        isCoreFunctional = com.trackit.expense.util.PermissionHelper.isCoreFunctional(this)
        if (!isCoreFunctional) {
            val missing = com.trackit.expense.util.PermissionHelper.runtimePermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            Log.d(TAG, "Missing permissions: ${missing.joinToString()}")
        }

        // 2. Overlay permission
        overlayPermissionOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        // 3. SMS receiver health
        smsReceiverAlive = isSmsReceiverEnabled()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SMS Receiver Self-test
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks whether [SmsReceiver] is still enabled via PackageManager.
     *
     * OEM battery managers (Xiaomi MIUI, Samsung OneUI) can set a component to
     * [PackageManager.COMPONENT_ENABLED_STATE_DISABLED] or
     * [PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER] without user consent.
     * The DEFAULT state (0) is the normal "enabled by manifest" state.
     */
    private fun isSmsReceiverEnabled(): Boolean {
        val comp  = ComponentName(this, SmsReceiver::class.java)
        val state = packageManager.getComponentEnabledSetting(comp)
        val alive = state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        if (!alive) Log.w(TAG, "SmsReceiver is DISABLED (state=$state). Will attempt re-enable.")
        return alive
    }

    /**
     * Re-enables the SmsReceiver component and opens OEM battery settings so the
     * user can permanently whitelist the app.
     */
    private fun restartSmsReceiver() {
        val comp = ComponentName(this, SmsReceiver::class.java)
        packageManager.setComponentEnabledSetting(
            comp,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        smsReceiverAlive = true
        Log.i(TAG, "SmsReceiver re-enabled.")

        // Open OEM battery exemption screen if available
        if (OemBatteryHelper.hasOemBatterySettings(this)) {
            OemBatteryHelper.launchOemBatterySettings(this)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Composables
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    private fun PermissionsRequiredDialog() {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape    = RoundedCornerShape(24.dp),
                colors   = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(
                    modifier              = Modifier.padding(24.dp),
                    horizontalAlignment   = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Security, null,
                        tint = TrackItPrimary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Permissions Required",
                        style      = MaterialTheme.typography.headlineSmall,
                        color      = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "TrackIt needs SMS and Notification permissions to function. Please enable them in Settings.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick  = {
                            startActivity(Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:$packageName")
                            ))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("Open Settings") }
                }
            }
        }
    }

    @Composable
    private fun StatusBanner(
        icon:     androidx.compose.ui.graphics.vector.ImageVector,
        tint:     Color,
        text:     String,
        action:   String,
        onAction: () -> Unit
    ) {
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text,
                    color    = Color.White.copy(alpha = 0.85f),
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onAction) {
                    Text(action, color = tint, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun PendingExpenseRestoreCard(
        onRestore: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TrackItPrimary.copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)) {
                Text("We saved your incomplete expense",
                    color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("You left an expense entry unfinished. Tap below to continue where you left off.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(8.dp)
                    ) { Text("Dismiss", color = Color.White.copy(alpha = 0.6f)) }
                    Button(
                        onClick  = onRestore,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = TrackItPrimary)
                    ) { Text("Continue") }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

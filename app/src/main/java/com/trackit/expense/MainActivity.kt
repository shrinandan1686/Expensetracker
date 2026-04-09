package com.trackit.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.trackit.expense.overlay.OverlayPermissionHelper
import com.trackit.expense.presentation.navigation.NavGraph
import com.trackit.expense.presentation.navigation.TrackItBottomNav
import com.trackit.expense.presentation.theme.TrackItTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * Single Activity entry point for TrackIt.
 *
 * ## Overlay Permission
 * On first launch (and whenever [SYSTEM_ALERT_WINDOW] is missing), a rationale
 * [AlertDialog] is shown explaining why the permission is needed. The user can:
 * - Tap "Open Settings" → directed to the system "Display over other apps" screen.
 * - Tap "Not Now" → dismisses the dialog (the app will fall back to silent auto-save).
 *
 * The check is repeated in [onResume] so the UI updates automatically when the user
 * returns from the Settings screen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var preferenceRepository: com.trackit.expense.domain.repository.PreferenceRepository

    @javax.inject.Inject
    lateinit var settingsRepository: com.trackit.expense.domain.repository.SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = com.trackit.expense.domain.repository.ThemeMode.SYSTEM)
            val dynamicColor by settingsRepository.dynamicColorEnabled.collectAsState(initial = true)
            
            val darkTheme = when (themeMode) {
                com.trackit.expense.domain.repository.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.trackit.expense.domain.repository.ThemeMode.LIGHT -> false
                com.trackit.expense.domain.repository.ThemeMode.DARK -> true
            }

            TrackItTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor
            ) {
                val isOnboardingCompleted = remember { preferenceRepository.isOnboardingCompleted() }
                
                // Track if core functional permissions (SMS + Overlay) are granted
                // We don't 'remember' this so it re-checks on every recomposition (triggered by onResume invalidation)
                val isCoreFunctional = com.trackit.expense.util.PermissionHelper.isCoreFunctional(this)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDest = if (isOnboardingCompleted) {
                        com.trackit.expense.presentation.navigation.Screen.Home.route
                    } else {
                        com.trackit.expense.presentation.navigation.Screen.Onboarding.route
                    }

                    androidx.compose.material3.Scaffold(
                        bottomBar = { TrackItBottomNav(navController) }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            NavGraph(
                                navController = navController,
                                startDestination = startDest
                            )
                        }
                    }
                }

                // ── Critical Permission Guard ────────────────────────────────
                // Show a blocking dialog if onboarding is done but user revoked permissions
                if (isOnboardingCompleted && !isCoreFunctional) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = com.trackit.expense.presentation.theme.DarkSurface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    null,
                                    tint = com.trackit.expense.presentation.theme.TrackItPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Permissions Required",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "TrackIt needs SMS and Overlay permissions to function correctly. Please enable them in settings to continue.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse("package:$packageName")
                                        )
                                        startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.invalidate()
    }
}

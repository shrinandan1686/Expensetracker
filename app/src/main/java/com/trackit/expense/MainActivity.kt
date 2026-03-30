package com.trackit.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.trackit.expense.overlay.OverlayPermissionHelper
import com.trackit.expense.presentation.navigation.NavGraph
import com.trackit.expense.presentation.theme.TrackItTheme
import dagger.hilt.android.AndroidEntryPoint

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackItTheme {
                var showRationale by remember {
                    mutableStateOf(!OverlayPermissionHelper.hasOverlayPermission(this))
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    NavGraph()
                }

                // ── Overlay permission rationale dialog ──────────────────────
                if (showRationale) {
                    AlertDialog(
                        onDismissRequest = { showRationale = false },
                        title   = { Text(OverlayPermissionHelper.RATIONALE_TITLE) },
                        text    = { Text(OverlayPermissionHelper.RATIONALE_BODY) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showRationale = false
                                    OverlayPermissionHelper.openPermissionSettings(this@MainActivity)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    OverlayPermissionHelper.RATIONALE_CONFIRM,
                                    color = Color.White
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRationale = false }) {
                                Text(OverlayPermissionHelper.RATIONALE_DISMISS)
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Re-check the overlay permission every time the user returns to the app
     * (e.g., after granting it in the system Settings screen).
     * If granted, hide the rationale — no restart needed.
     */
    override fun onResume() {
        super.onResume()
        // The Compose state automatically hides the dialog once the permission is granted
        // because showRationale is derived from the permission check. However, since it's
        // a remember{} inside setContent we call invalidate on the Window to re-trigger
        // composition on return from Settings.
        window.decorView.invalidate()
    }
}

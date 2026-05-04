package com.trackit.expense.presentation.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.TrackItPrimary
import com.trackit.expense.presentation.theme.TrackItSecondary
import com.trackit.expense.util.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> PageOneRuntimePermissions()
                1 -> PageTwoBatteryAndBudget(
                    budgetInput = uiState.budgetInput,
                    onBudgetChanged = viewModel::onBudgetInputChanged,
                    onNext = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> PageThreeProfile(
                    nameInput  = uiState.nameInput,
                    upiIdInput = uiState.upiIdInput,
                    onNameChanged  = viewModel::onNameChanged,
                    onUpiIdChanged = viewModel::onUpiIdChanged,
                    onFinish  = viewModel::completeOnboarding,
                    isSaving  = uiState.isSaving
                )
            }
        }

        // Bottom Navigation UI (Dots + Next Button)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            // Page Indicators
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .height(50.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { iteration ->
                    val color = if (pagerState.currentPage == iteration) TrackItPrimary else Color.White.copy(alpha = 0.2f)
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(if (pagerState.currentPage == iteration) 10.dp else 8.dp)
                    )
                }
            }

            // Next button (shown on Pages 0 — page 1 has its own Next button)
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    containerColor = TrackItPrimary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PageOneRuntimePermissions() {
    val context = LocalContext.current
    val permissionState = rememberMultiplePermissionsState(PermissionHelper.runtimePermissions)

    OnboardingPage(
        title = "Auto-detect payments",
        description = "TrackIt reads incoming bank SMS to automatically log your expenses instantly after you pay.",
        icon = Icons.Default.Sms,
        buttonText = if (permissionState.allPermissionsGranted) "Permissions Granted" else "Grant SMS Access",
        onButtonClick = { permissionState.launchMultiplePermissionRequest() },
        isButtonEnabled = !permissionState.allPermissionsGranted
    )
}

@Composable
private fun PageTwoBatteryAndBudget(
    budgetInput: String,
    onBudgetChanged: (String) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IllustrationCircle(icon = Icons.Default.Assessment)
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Stay in control",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "To ensure 24/7 detection, please exclude TrackIt from battery optimization.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = TrackItSecondary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BatteryChargingFull, null, tint = TrackItSecondary)
            Spacer(Modifier.width(8.dp))
            Text("Optimization Settings", color = TrackItSecondary)
        }

        Spacer(Modifier.height(48.dp))
        Divider(color = Color.White.copy(alpha = 0.1f))
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Set Monthly Budget (Optional)",
            style = MaterialTheme.typography.labelLarge,
            color = TrackItPrimary
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = budgetInput,
            onValueChange = onBudgetChanged,
            prefix = { Text("₹ ", color = Color.White.copy(alpha = 0.5f)) },
            placeholder = { Text("e.g. 20000", color = Color.White.copy(alpha = 0.3f)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = TrackItPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
            )
        )

        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrackItPrimary)
        ) {
            Text("Next", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun PageThreeProfile(
    nameInput: String,
    upiIdInput: String,
    onNameChanged: (String) -> Unit,
    onUpiIdChanged: (String) -> Unit,
    onFinish: () -> Unit,
    isSaving: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IllustrationCircle(icon = Icons.Default.AccountCircle)
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Your identity",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Set your display name and UPI ID so friends can settle up with you later.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChanged,
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = TrackItPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = TrackItPrimary,
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
            )
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = upiIdInput,
            onValueChange = onUpiIdChanged,
            label = { Text("UPI ID (optional)") },
            placeholder = { Text("yourname@upi", color = Color.White.copy(alpha = 0.3f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = TrackItPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = TrackItPrimary,
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
            )
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrackItPrimary),
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    description: String,
    icon: ImageVector,
    buttonText: String,
    onButtonClick: () -> Unit,
    isButtonEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IllustrationCircle(icon = icon)
        Spacer(Modifier.height(48.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onButtonClick,
            enabled = isButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TrackItPrimary,
                disabledContainerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Text(buttonText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IllustrationCircle(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(TrackItPrimary.copy(alpha = 0.15f), Color.Transparent)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = TrackItPrimary
        )
    }
}

@Composable
private fun CircularProgressIndicator(size: androidx.compose.ui.unit.Dp, color: Color) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = 2.dp
    )
}

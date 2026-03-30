package com.trackit.expense.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkOnSurface
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.DarkSurfaceVariant
import com.trackit.expense.presentation.theme.IncomeGreen
import com.trackit.expense.presentation.theme.TrackItPrimary
import com.trackit.expense.presentation.theme.TrackItSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val ScrimColor       = Color(0xCC000000)   // 80% black scrim
private val SheetBg          = Color(0xFF16162A)   // deep navy popup background
private val HeaderGradient   = Brush.horizontalGradient(
    listOf(Color(0xFF6C63FF), Color(0xFF4B44CC))
)
private val FieldBorder      = Color(0xFF3A3A5C)
private val FieldText        = Color(0xFFE8E8FF)
private val FieldLabel       = Color(0xFFADADD4)
private val SaveGradient     = Brush.horizontalGradient(
    listOf(Color(0xFF6C63FF), Color(0xFF9C8FFF))
)

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen overlay that renders a semi-transparent scrim + bottom-sheet popup
 * for the user to review and save a detected UPI payment.
 *
 * ## Parameters
 * - [amount]         Amount in INR (Double) — non-editable, pre-filled.
 * - [detectedMerch]  Auto-detected merchant string from SMS parser; editable.
 * - [accountLast4]   Last 4 digits of account/card; null if not detected.
 * - [paymentMode]    Payment channel from SMS ("UPI", "PhonePe", etc.).
 * - [timestamp]      Unix epoch millis of the transaction (non-editable display).
 * - [backPressCount] Incremented by the Service whenever KEYCODE_BACK is captured.
 *                    The popup observes this via [LaunchedEffect] and shows a [Snackbar].
 * - [showSuccess]    When true, replaces the form with the success checkmark animation.
 * - [onSave]         Invoked with [ExpenseEntryData] when the user taps "Save".
 * - [onSkip]         Invoked with [ExpenseEntryData]? after the skip confirmation dialog.
 *
 * ## Dismissal protection
 * - Back press → Snackbar "Please save or skip this expense" (no dismiss).
 * - Skip (first tap) → confirmation [AlertDialog].
 * - Skip (confirm) → [onSkip] is called.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryPopup(
    amount: Double,
    detectedMerch: String?,
    accountLast4: String?,
    paymentMode: String,
    timestamp: Long,
    backPressCount: Int,
    showSuccess: Boolean,
    onSave: (ExpenseEntryData) -> Unit,
    onSkip: (ExpenseEntryData?) -> Unit
) {
    // ── Local form state ──────────────────────────────────────────────────────
    var merchant by remember { mutableStateOf(detectedMerch ?: "") }
    var selectedCategory by remember {
        mutableStateOf(ExpenseCategory.inferFrom(detectedMerch))
    }
    var notes by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showSkipDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ── Back-press → snackbar ─────────────────────────────────────────────────
    LaunchedEffect(backPressCount) {
        if (backPressCount > 0) {
            snackbarHostState.showSnackbar("Please save or skip this expense")
        }
    }

    // ── Helper to build current form data ────────────────────────────────────
    fun currentData() = ExpenseEntryData(
        amount       = amount,
        merchant     = merchant,
        category     = selectedCategory,
        accountLast4 = accountLast4,
        notes        = notes,
        timestamp    = timestamp,
        paymentMode  = paymentMode
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Root: full-screen scrim + bottom sheet
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimColor)
            // Tap on scrim → back-press warning (not dismiss)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                scope.launch {
                    snackbarHostState.showSnackbar("Please save or skip this expense")
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {

        // ── Bottom sheet ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(SheetBg)
                .navigationBarsPadding()
                .imePadding()
                // Consume clicks so they don't propagate to scrim
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
        ) {
            AnimatedContent(
                targetState = showSuccess,
                transitionSpec = {
                    (scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                    (scaleOut(tween(150)) + fadeOut())
                },
                label = "overlay_content"
            ) { isSuccess ->
                if (isSuccess) {
                    SuccessView()
                } else {
                    FormContent(
                        amount           = amount,
                        merchant         = merchant,
                        onMerchantChange = { merchant = it },
                        selectedCategory = selectedCategory,
                        categoryExpanded = categoryExpanded,
                        onCategoryExpand = { categoryExpanded = it },
                        onCategorySelect = { selectedCategory = it; categoryExpanded = false },
                        accountLast4     = accountLast4,
                        paymentMode      = paymentMode,
                        timestamp        = timestamp,
                        notes            = notes,
                        onNotesChange    = { notes = it },
                        onSave           = { onSave(currentData()) },
                        onSkipRequest    = { showSkipDialog = true }
                    )
                }
            }
        }

        // ── Snackbar ────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData        = data,
                containerColor      = Color(0xFF2D2D4F),
                contentColor        = DarkOnSurface,
                actionColor         = TrackItPrimary,
                shape               = RoundedCornerShape(12.dp)
            )
        }
    }

    // ── Skip confirmation dialog ──────────────────────────────────────────────
    if (showSkipDialog) {
        SkipConfirmationDialog(
            onConfirm = {
                showSkipDialog = false
                onSkip(currentData())
            },
            onDismiss = { showSkipDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Form content
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormContent(
    amount: Double,
    merchant: String,
    onMerchantChange: (String) -> Unit,
    selectedCategory: ExpenseCategory,
    categoryExpanded: Boolean,
    onCategoryExpand: (Boolean) -> Unit,
    onCategorySelect: (ExpenseCategory) -> Unit,
    accountLast4: String?,
    paymentMode: String,
    timestamp: Long,
    notes: String,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onSkipRequest: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {

        // ── Drag handle + Header ─────────────────────────────────────────────
        DragHandle()
        PopupHeader(paymentMode = paymentMode)

        // ── Amount ──────────────────────────────────────────────────────────
        AmountDisplay(amount = amount)

        // ── Fields ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Merchant
            OverlayTextField(
                value       = merchant,
                onValueChange = onMerchantChange,
                label       = "Merchant",
                placeholder = "Who did you pay?",
                leadingIcon = { Text("🏪", fontSize = 18.sp) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editable",
                        tint = TrackItPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                imeAction = ImeAction.Next
            )

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded        = categoryExpanded,
                onExpandedChange = { onCategoryExpand(it) }
            ) {
                OutlinedTextField(
                    value         = selectedCategory.label,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Category", color = FieldLabel) },
                    leadingIcon   = { Text("📂", fontSize = 18.sp) },
                    trailingIcon  = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = FieldLabel
                        )
                    },
                    shape  = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded        = categoryExpanded,
                    onDismissRequest = { onCategoryExpand(false) },
                    modifier        = Modifier.background(DarkSurfaceVariant)
                ) {
                    ExpenseCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text  = cat.label,
                                    color = if (cat == selectedCategory) TrackItPrimary else FieldText,
                                    fontWeight = if (cat == selectedCategory) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            onClick = { onCategorySelect(cat) }
                        )
                    }
                }
            }

            // Account (non-editable display)
            if (accountLast4 != null) {
                OutlinedTextField(
                    value    = "•••• •••• •••• $accountLast4",
                    onValueChange = {},
                    readOnly = true,
                    label    = { Text("Account", color = FieldLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = FieldLabel
                        )
                    },
                    shape   = RoundedCornerShape(12.dp),
                    colors  = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Notes
            OverlayTextField(
                value         = notes,
                onValueChange = onNotesChange,
                label         = "Notes (optional)",
                placeholder   = "Add a note…",
                leadingIcon   = { Text("📝", fontSize = 18.sp) },
                imeAction     = ImeAction.Done,
                singleLine    = false,
                maxLines      = 2,
                capitalization = KeyboardCapitalization.Sentences
            )

            // Date/Time (non-editable)
            OutlinedTextField(
                value    = dateFormatter.format(Date(timestamp)),
                onValueChange = {},
                readOnly = true,
                label    = { Text("Date & Time", color = FieldLabel) },
                leadingIcon = { Text("🕐", fontSize = 18.sp) },
                shape    = RoundedCornerShape(12.dp),
                colors   = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Action buttons ────────────────────────────────────────────────
            ActionButtons(
                onSave        = onSave,
                onSkipRequest = onSkipRequest
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FieldBorder)
        )
    }
}

@Composable
private fun PopupHeader(paymentMode: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderGradient)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text  = "💳  Payment Detected",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "via $paymentMode",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
        // UPI/bank logo badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text  = "UPI",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AmountDisplay(amount: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(
                    fontSize   = 22.sp,
                    color      = TrackItPrimary,
                    fontWeight = FontWeight.Light
                )) { append("₹") }
                withStyle(SpanStyle(
                    fontSize   = 52.sp,
                    color      = DarkOnSurface,
                    fontWeight = FontWeight.Bold
                )) {
                    // Format: 1,200.50 → insert comma every 3 digits from right
                    append(formatAmount(amount))
                }
            }
        )
    }
}

@Composable
private fun ActionButtons(
    onSave: () -> Unit,
    onSkipRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Skip button (outlined, requires double-tap confirmation)
        TextButton(
            onClick  = onSkipRequest,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = FieldLabel
            )
        ) {
            Text(
                text       = "SKIP",
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        // Save button (gradient)
        Box(
            modifier = Modifier
                .weight(2f)
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SaveGradient)
                .clickable { onSave() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "SAVE EXPENSE",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                style      = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// ── Success view ──────────────────────────────────────────────────────────────

@Composable
private fun SuccessView() {
    var scaleReady by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue  = if (scaleReady) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "success_scale"
    )

    LaunchedEffect(Unit) { scaleReady = true }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector        = Icons.Filled.CheckCircle,
                contentDescription = "Saved",
                tint               = IncomeGreen,
                modifier           = Modifier
                    .size(88.dp)
                    .scale(scale)
            )
            Text(
                text      = "Expense Saved!",
                style     = MaterialTheme.typography.titleLarge,
                color     = DarkOnSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "Closing in a moment…",
                style = MaterialTheme.typography.bodyMedium,
                color = FieldLabel
            )
        }
    }
}

// ── Skip confirmation dialog ──────────────────────────────────────────────────

@Composable
private fun SkipConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest   = onDismiss,
        icon               = {
            Icon(Icons.Default.Info, contentDescription = null, tint = TrackItPrimary)
        },
        title = {
            Text(
                "Skip this expense?",
                fontWeight = FontWeight.Bold,
                color      = DarkOnSurface
            )
        },
        text = {
            Text(
                text  = "This transaction will be saved as unreviewed. You can find and edit it later in History → Skipped.",
                color = FieldLabel,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = TrackItPrimary)
            ) {
                Text("Yes, Skip", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Editing", color = TrackItSecondary)
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 0.dp,
        shape          = RoundedCornerShape(20.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility composables & helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverlayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, color = FieldLabel) },
        placeholder   = { Text(placeholder, color = FieldLabel.copy(alpha = 0.5f)) },
        leadingIcon   = leadingIcon,
        trailingIcon  = trailingIcon,
        singleLine    = singleLine,
        maxLines      = maxLines,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction      = imeAction
        ),
        shape   = RoundedCornerShape(12.dp),
        colors  = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = TrackItPrimary,
    unfocusedBorderColor = FieldBorder,
    focusedLabelColor    = TrackItPrimary,
    unfocusedLabelColor  = FieldLabel,
    focusedTextColor     = FieldText,
    unfocusedTextColor   = FieldText,
    cursorColor          = TrackItPrimary,
    focusedContainerColor   = DarkSurface,
    unfocusedContainerColor = DarkSurface
)

/** Format 1234.5 → "1,234.50" for the hero amount display. */
private fun formatAmount(amount: Double): String {
    val formatted = String.format("%.2f", amount)
    val parts     = formatted.split(".")
    val intPart   = parts[0]
    val decPart   = parts.getOrElse(1) { "00" }
    // Indian number grouping: last 3 then 2 each
    val grouped = buildString {
        val rev = intPart.reversed()
        rev.forEachIndexed { i, c ->
            if (i == 3 && i < rev.length) append(',')
            else if (i > 3 && (i - 3) % 2 == 0 && i < rev.length) append(',')
            append(c)
        }
    }.reversed()
    return "$grouped.$decPart"
}

package com.trackit.expense.presentation.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.expense.overlay.ExpenseCategory
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.DarkSurfaceVariant
import com.trackit.expense.presentation.theme.TrackItPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Nav back on save
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }
    // Snackbar on error
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Add Expense", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Amount ────────────────────────────────────────────────────────
            SectionLabel("Amount (₹)")
            OutlinedTextField(
                value = uiState.amountInput,
                onValueChange = viewModel::onAmountChanged,
                placeholder = { Text("0.00", color = Color.White.copy(alpha = 0.35f)) },
                leadingIcon = { Text("₹", color = TrackItPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            // ── Merchant ──────────────────────────────────────────────────────
            SectionLabel("Merchant / Payee")
            OutlinedTextField(
                value = uiState.merchant,
                onValueChange = viewModel::onMerchantChanged,
                placeholder = { Text("Swiggy, Amazon, etc.", color = Color.White.copy(alpha = 0.35f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            // ── Category chips ────────────────────────────────────────────────
            SectionLabel("Category")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp)
            ) {
                ExpenseCategory.entries.forEach { cat ->
                    val selected = uiState.selectedCategory == cat
                    FilterChip(
                        selected = selected,
                        onClick  = { viewModel.onCategorySelected(cat) },
                        label    = { Text("${cat.emoji} ${cat.displayName}", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor    = TrackItPrimary,
                            selectedLabelColor        = Color.White,
                            containerColor            = DarkSurfaceVariant,
                            labelColor                = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            // ── Account ───────────────────────────────────────────────────────
            SectionLabel("Account / Card  (optional)")
            OutlinedTextField(
                value = uiState.account,
                onValueChange = viewModel::onAccountChanged,
                placeholder = { Text("UPI · XX1234", color = Color.White.copy(alpha = 0.35f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            // ── Notes ─────────────────────────────────────────────────────────
            SectionLabel("Notes  (optional)")
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::onNotesChanged,
                placeholder = { Text("Add a note…", color = Color.White.copy(alpha = 0.35f)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))
            
            // ── Location Detection Feedback ───────────────────────────────────
            AnimatedVisibility(visible = uiState.locationAddress != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn, 
                        contentDescription = null, 
                        tint = TrackItPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Detected at: ${uiState.locationAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // ── Save button ───────────────────────────────────────────────────
            Button(
                onClick  = viewModel::onSaveExpense,
                enabled  = !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TrackItPrimary)
            ) {
                AnimatedVisibility(visible = uiState.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    if (uiState.isSubmitting) "Saving…" else "Save Expense",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor        = Color.White,
    unfocusedTextColor      = Color.White,
    focusedBorderColor      = TrackItPrimary,
    unfocusedBorderColor    = Color.White.copy(alpha = 0.2f),
    cursorColor             = TrackItPrimary,
    focusedContainerColor   = DarkSurface,
    unfocusedContainerColor = DarkSurface
)

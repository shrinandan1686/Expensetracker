package com.trackit.expense.presentation.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Add Expense screen.
 *
 * Displays a form for the user to manually log a new expense.
 *
 * Fields:
 * - Amount (numeric, in INR)
 * - Note / description
 * - Category picker (bottom sheet)
 * - Payment method selector (UPI / Cash / Card)
 * - Date/time picker (defaults to now)
 *
 * State is managed by [AddExpenseViewModel].
 * Navigates back via [onNavigateBack] once [AddExpenseUiState.isSaved] is true.
 *
 * TODO: Implement full form UI with Material3 components.
 */
@Composable
fun AddExpenseScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Automatically navigate back when the expense is saved
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Add Expense Screen — TODO: Implement Form UI")
            // TODO: Replace with full form layout:
            //  - AmountInputField
            //  - NoteTextField
            //  - CategoryPickerRow → Bottom Sheet
            //  - PaymentMethodChipGroup
            //  - DateTimePicker
            //  - SaveButton (calls viewModel.onSaveExpense())
        }
    }
}

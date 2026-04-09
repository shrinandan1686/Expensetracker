package com.trackit.expense.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import com.trackit.expense.overlay.ExpenseCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the manual Add Expense screen.
 *
 * @property amountInput     Raw string from the amount text field.
 * @property merchant        Merchant name typed by the user.
 * @property selectedCategory Currently selected [ExpenseCategory].
 * @property account         Account/card identifier (optional free text).
 * @property notes           Optional note.
 * @property isSubmitting    True while the Room write is in progress.
 * @property isSaved         True once the expense has been saved — triggers nav back.
 * @property errorMessage    Validation or save error message.
 */
data class AddExpenseUiState(
    val amountInput: String              = "",
    val merchant: String                 = "",
    val selectedCategory: ExpenseCategory = ExpenseCategory.OTHERS,
    val account: String                  = "",
    val notes: String                    = "",
    val isSubmitting: Boolean            = false,
    val isSaved: Boolean                 = false,
    val errorMessage: String?            = null
)

/**
 * ViewModel for [AddExpenseScreen].
 *
 * Handles form field updates, client-side validation, and persisting the expense
 * via [AddExpenseUseCase] (offline-first — writes to Room immediately).
 */
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val addExpenseUseCase: AddExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Input event handlers (one per form field)
    // ─────────────────────────────────────────────────────────────────────────

    fun onAmountChanged(input: String) =
        _uiState.update { it.copy(amountInput = input, errorMessage = null) }

    fun onMerchantChanged(value: String) =
        _uiState.update { it.copy(merchant = value) }

    fun onCategorySelected(cat: ExpenseCategory) =
        _uiState.update { it.copy(selectedCategory = cat) }

    fun onAccountChanged(value: String) =
        _uiState.update { it.copy(account = value) }

    fun onNotesChanged(value: String) =
        _uiState.update { it.copy(notes = value) }

    // ─────────────────────────────────────────────────────────────────────────
    // Save
    // ─────────────────────────────────────────────────────────────────────────

    fun onSaveExpense() {
        val state = _uiState.value

        // Validate amount
        val amount = state.amountInput.replace(",", "").toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid amount.") }
            return
        }
        if (state.merchant.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Merchant name is required.") }
            return
        }

        val expense = Expense(
            amount        = amount,
            merchant      = state.merchant.trim(),
            category      = state.selectedCategory.displayName,
            account       = state.account.trim(),
            notes         = state.notes.trim().takeIf { it.isNotBlank() },
            isLogged      = true,             // user manually entered it
            loggedAt      = System.currentTimeMillis(),
            transactionAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            addExpenseUseCase(expense)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isSaved = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message) }
                }
        }
    }

    fun onErrorDismissed() =
        _uiState.update { it.copy(errorMessage = null) }
}

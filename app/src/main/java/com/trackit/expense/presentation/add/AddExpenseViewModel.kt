package com.trackit.expense.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Category
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Add Expense screen.
 *
 * @property amountInput Raw string from the amount text field.
 * @property note User-typed expense note.
 * @property selectedCategory Currently selected [Category]; null if unset.
 * @property paymentMethod Selected payment method string.
 * @property isSubmitting True while the save operation is in progress.
 * @property isSaved True once the expense has been saved — triggers navigation back.
 * @property errorMessage Validation or save error message.
 */
data class AddExpenseUiState(
    val amountInput: String = "",
    val note: String = "",
    val selectedCategory: Category? = null,
    val paymentMethod: String = "UPI",
    val isSubmitting: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for [AddExpenseScreen].
 *
 * Handles form field updates, validation, and submitting the new expense
 * via [AddExpenseUseCase].
 */
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val addExpenseUseCase: AddExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    fun onAmountChanged(input: String) {
        _uiState.update { it.copy(amountInput = input) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onCategorySelected(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onPaymentMethodChanged(method: String) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun onSaveExpense() {
        val state = _uiState.value
        val amountRupees = state.amountInput.toDoubleOrNull()
        if (amountRupees == null || amountRupees <= 0) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid amount.") }
            return
        }

        val amountPaise = (amountRupees * 100).toLong()
        val expense = Expense(
            amount = amountPaise,
            note = state.note.trim(),
            categoryId = state.selectedCategory?.id,
            paymentMethod = state.paymentMethod,
            timestamp = System.currentTimeMillis()
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

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

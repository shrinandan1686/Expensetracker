package com.trackit.expense.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetExpenseByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State representation for the Expense Detail screen.
 */
sealed interface ExpenseDetailUiState {
    data object Loading : ExpenseDetailUiState
    data class Success(val expense: Expense) : ExpenseDetailUiState
    data class Error(val message: String) : ExpenseDetailUiState
}

/**
 * ViewModel for the Expense Detail screen.
 *
 * It manages the lifecycle of a single expense record, allowing the user
 * to view complete transaction metadata and perform destructive actions (deletion).
 */
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getExpenseByIdUseCase: GetExpenseByIdUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase
) : ViewModel() {

    // Retrieve expenseId from navigation arguments
    private val expenseId: String = checkNotNull(savedStateHandle["expenseId"])

    /**
     * Observable UI state representing the current expense or error conditions.
     */
    val uiState: StateFlow<ExpenseDetailUiState> = getExpenseByIdUseCase(expenseId)
        .map { expense ->
            if (expense != null) {
                ExpenseDetailUiState.Success(expense)
            } else {
                ExpenseDetailUiState.Error("Expense not found")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExpenseDetailUiState.Loading
        )

    /**
     * Deletes the current expense and triggers navigation back.
     *
     * @param onComplete Callback invoked after successful deletion.
     */
    fun deleteExpense(onComplete: () -> Unit) {
        viewModelScope.launch {
            deleteExpenseUseCase(expenseId).onSuccess {
                onComplete()
            }
        }
    }
}

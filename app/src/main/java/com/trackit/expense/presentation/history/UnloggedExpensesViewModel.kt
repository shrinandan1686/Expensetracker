package com.trackit.expense.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetUnloggedExpensesUseCase
import com.trackit.expense.domain.usecase.MarkExpenseLoggedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnloggedUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class UnloggedExpensesViewModel @Inject constructor(
    private val getUnloggedUseCase: GetUnloggedExpensesUseCase,
    private val markLoggedUseCase: MarkExpenseLoggedUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnloggedUiState())
    val uiState: StateFlow<UnloggedUiState> = _uiState.asStateFlow()

    init {
        observeUnlogged()
    }

    private fun observeUnlogged() {
        viewModelScope.launch {
            getUnloggedUseCase().collect { list ->
                _uiState.update { it.copy(expenses = list, isLoading = false) }
            }
        }
    }

    fun onLogExpense(expense: Expense) {
        viewModelScope.launch {
            markLoggedUseCase(expense.id)
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onIgnoreExpense(expense: Expense) {
        viewModelScope.launch {
            deleteExpenseUseCase(expense.id)
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun onIgnoreAll() {
        viewModelScope.launch {
            // Sequential deletion for simplicity in review queue
            uiState.value.expenses.forEach { expense ->
                deleteExpenseUseCase(expense.id)
            }
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

package com.trackit.expense.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetAllExpensesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Home screen.
 *
 * @property expenses List of the most recent expenses shown in the feed.
 * @property totalMonthlySpend Total spend amount in paise for the current month.
 * @property isLoading True while initial data is being loaded.
 * @property errorMessage Non-null when an error has occurred.
 */
data class HomeUiState(
    val expenses: List<Expense> = emptyList(),
    val totalMonthlySpend: Long = 0L,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel for [HomeScreen].
 *
 * Annotated with [@HiltViewModel] for Hilt injection.
 * Exposes [uiState] as a [StateFlow] collected by the Compose UI.
 *
 * TODO: Inject and use GetMonthlyTotalUseCase once implemented.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllExpensesUseCase: GetAllExpensesUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val expenses: StateFlow<List<Expense>> = getAllExpensesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        observeExpenses()
    }

    private fun observeExpenses() {
        viewModelScope.launch {
            getAllExpensesUseCase().collect { list ->
                _uiState.update { state ->
                    state.copy(
                        expenses = list,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onDeleteExpense(expense: Expense) {
        viewModelScope.launch {
            deleteExpenseUseCase(expense)
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

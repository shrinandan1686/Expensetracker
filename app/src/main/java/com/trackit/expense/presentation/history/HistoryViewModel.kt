package com.trackit.expense.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetAllExpensesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filter modes available on the History screen.
 */
enum class HistoryFilter { ALL, THIS_WEEK, THIS_MONTH, CUSTOM }

/**
 * UI state for the History screen.
 *
 * @property allExpenses The complete unfiltered list.
 * @property filteredExpenses Filtered/searched subset for display.
 * @property activeFilter Currently selected [HistoryFilter].
 * @property searchQuery Current search string.
 * @property isLoading True while loading initial data.
 * @property errorMessage Non-null on error.
 */
data class HistoryUiState(
    val allExpenses: List<Expense> = emptyList(),
    val filteredExpenses: List<Expense> = emptyList(),
    val activeFilter: HistoryFilter = HistoryFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel for [HistoryScreen].
 *
 * Manages the expense list with filtering and search on top of [GetAllExpensesUseCase].
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getAllExpensesUseCase: GetAllExpensesUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeExpenses()
    }

    private fun observeExpenses() {
        viewModelScope.launch {
            getAllExpensesUseCase().collect { list ->
                _uiState.update { state ->
                    state.copy(
                        allExpenses = list,
                        filteredExpenses = applyFilter(list, state.activeFilter, state.searchQuery),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredExpenses = applyFilter(state.allExpenses, state.activeFilter, query)
            )
        }
    }

    fun onFilterChanged(filter: HistoryFilter) {
        _uiState.update { state ->
            state.copy(
                activeFilter = filter,
                filteredExpenses = applyFilter(state.allExpenses, filter, state.searchQuery)
            )
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

    // ──────────────────────────── Private Helpers ────────────────────────────

    private fun applyFilter(
        list: List<Expense>,
        filter: HistoryFilter,
        query: String
    ): List<Expense> {
        val now = System.currentTimeMillis()
        val filtered = when (filter) {
            HistoryFilter.ALL        -> list
            HistoryFilter.THIS_WEEK  -> list.filter { it.timestamp >= now - 7 * 24 * 60 * 60 * 1000L }
            HistoryFilter.THIS_MONTH -> list.filter { it.timestamp >= now - 30 * 24 * 60 * 60 * 1000L }
            HistoryFilter.CUSTOM     -> list // TODO: Wire custom date range picker
        }
        return if (query.isBlank()) filtered
        else filtered.filter { expense ->
            expense.note.contains(query, ignoreCase = true) ||
            expense.merchantName?.contains(query, ignoreCase = true) == true
        }
    }
}

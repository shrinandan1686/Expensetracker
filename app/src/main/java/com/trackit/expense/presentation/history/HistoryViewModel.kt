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

/** Filter chips on the History screen. */
enum class HistoryFilter { ALL, THIS_WEEK, THIS_MONTH, UNREVIEWED }

/**
 * UI state for the History screen.
 *
 * @property allExpenses      Full unfiltered list from Room.
 * @property filteredExpenses Subset after applying [activeFilter] + [searchQuery].
 * @property activeFilter     Currently selected chip.
 * @property searchQuery      Search bar text.
 * @property isLoading        True while awaiting first Room emission.
 * @property errorMessage     Non-null on delete failure.
 */
data class HistoryUiState(
    val allExpenses: List<Expense>      = emptyList(),
    val filteredExpenses: List<Expense> = emptyList(),
    val activeFilter: HistoryFilter     = HistoryFilter.ALL,
    val searchQuery: String             = "",
    val isLoading: Boolean              = true,
    val errorMessage: String?           = null
)

/**
 * ViewModel for [HistoryScreen].
 *
 * Consumes [GetAllExpensesUseCase] and applies in-memory filter + search.
 * Deletes are id-based via [DeleteExpenseUseCase].
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getAllExpensesUseCase: GetAllExpensesUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init { observeExpenses() }

    private fun observeExpenses() {
        viewModelScope.launch {
            getAllExpensesUseCase().collect { list ->
                _uiState.update { state ->
                    state.copy(
                        allExpenses      = list,
                        filteredExpenses = applyFilter(list, state.activeFilter, state.searchQuery),
                        isLoading        = false
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User actions
    // ─────────────────────────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery      = query,
                filteredExpenses = applyFilter(state.allExpenses, state.activeFilter, query)
            )
        }
    }

    fun onFilterChanged(filter: HistoryFilter) {
        _uiState.update { state ->
            state.copy(
                activeFilter     = filter,
                filteredExpenses = applyFilter(state.allExpenses, filter, state.searchQuery)
            )
        }
    }

    fun onDeleteExpense(expense: Expense) {
        viewModelScope.launch {
            deleteExpenseUseCase(expense.id)            // id-based delete
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
        }
    }

    fun onErrorDismissed() =
        _uiState.update { it.copy(errorMessage = null) }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter logic
    // ─────────────────────────────────────────────────────────────────────────

    private fun applyFilter(
        list: List<Expense>,
        filter: HistoryFilter,
        query: String
    ): List<Expense> {
        val now  = System.currentTimeMillis()
        val week = 7  * 24 * 60 * 60 * 1000L
        val month= 30 * 24 * 60 * 60 * 1000L

        val filtered = when (filter) {
            HistoryFilter.ALL        -> list
            HistoryFilter.THIS_WEEK  -> list.filter { it.transactionAt >= now - week }
            HistoryFilter.THIS_MONTH -> list.filter { it.transactionAt >= now - month }
            HistoryFilter.UNREVIEWED -> list.filter { !it.isLogged }
        }

        return if (query.isBlank()) filtered
        else filtered.filter { e ->
            e.merchant.contains(query, ignoreCase = true) ||
            e.category.contains(query, ignoreCase = true) ||
            e.notes?.contains(query, ignoreCase = true) == true ||
            e.account.contains(query, ignoreCase = true)
        }
    }
}

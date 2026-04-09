package com.trackit.expense.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetAllExpensesUseCase
import com.trackit.expense.domain.usecase.GetMonthlyTotalUseCase
import com.trackit.expense.domain.usecase.GetUnloggedExpensesUseCase
import com.trackit.expense.domain.usecase.SyncExpensesUseCase
import com.trackit.expense.domain.usecase.GetCategoryTotalsUseCase
import com.trackit.expense.domain.usecase.GetMonthlyBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * UI state for the Home screen.
 *
 * @property recentExpenses  Most recent 20 logged expenses for the feed.
 * @property unloggedCount   Badge count — auto-detected but not yet reviewed.
 * @property monthlyTotal    Total spend in INR Double for the current month.
 * @property currentMonth    ISO month string "YYYY-MM" shown in the summary header.
 * @property isLoading       True while first data emission is awaited.
 * @property errorMessage    Non-null when a delete or sync error has occurred.
 */
data class HomeUiState(
    val recentExpenses: List<Expense>  = emptyList(),
    val categoryTotals: List<com.trackit.expense.data.local.dao.CategoryTotal> = emptyList(),
    val monthlyBudget: Double          = 0.0,
    val unloggedCount: Int             = 0,
    val monthlyTotal: Double           = 0.0,
    val currentMonth: String           = "",
    val isLoading: Boolean             = true,
    val errorMessage: String?          = null
)

/**
 * ViewModel for [HomeScreen].
 *
 * Combines three reactive flows from the domain layer:
 * - [GetAllExpensesUseCase]       → recent expense list
 * - [GetUnloggedExpensesUseCase]  → unreviewed badge count
 * - [GetMonthlyTotalUseCase]      → current month spend total
 */

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllExpensesUseCase:    GetAllExpensesUseCase,
    private val getUnloggedUseCase:      GetUnloggedExpensesUseCase,
    private val getMonthlyTotalUseCase:  GetMonthlyTotalUseCase,
    private val getBudgetUseCase:        GetMonthlyBudgetUseCase,
    private val getCategoryTotalsUseCase: GetCategoryTotalsUseCase,
    private val deleteExpenseUseCase:    DeleteExpenseUseCase,
    private val syncExpensesUseCase:     SyncExpensesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val monthKey: String = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        .format(Date())

    init {
        _uiState.update { it.copy(currentMonth = monthKey) }
        observeExpenses()
        observeUnlogged()
        observeMonthlyTotal()
        observeBudget()
        observeCategoryTotals()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observers
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeExpenses() {
        viewModelScope.launch {
            getAllExpensesUseCase().collect { list ->
                _uiState.update { state ->
                    state.copy(
                        recentExpenses = list.take(20),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeUnlogged() {
        viewModelScope.launch {
            getUnloggedUseCase().collect { list ->
                _uiState.update { it.copy(unloggedCount = list.size) }
            }
        }
    }

    private fun observeMonthlyTotal() {
        viewModelScope.launch {
            getMonthlyTotalUseCase(monthKey).collect { total ->
                _uiState.update { it.copy(monthlyTotal = total) }
            }
        }
    }

    private fun observeBudget() {
        viewModelScope.launch {
            getBudgetUseCase(monthKey).collect { budget ->
                _uiState.update { it.copy(monthlyBudget = budget?.overall ?: 0.0) }
            }
        }
    }

    private fun observeCategoryTotals() {
        viewModelScope.launch {
            getCategoryTotalsUseCase(monthKey).collect { totals ->
                _uiState.update { it.copy(categoryTotals = totals) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User actions
    // ─────────────────────────────────────────────────────────────────────────

    fun onDeleteExpense(expense: Expense) {
        viewModelScope.launch {
            deleteExpenseUseCase(expense.id)
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
        }
    }

    fun onSyncNow() {
        syncExpensesUseCase()
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

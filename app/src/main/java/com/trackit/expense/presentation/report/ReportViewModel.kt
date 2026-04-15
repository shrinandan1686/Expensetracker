package com.trackit.expense.presentation.report

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.repository.ExpenseRepository
import com.trackit.expense.domain.usecase.ExportDataUseCase
import com.trackit.expense.domain.usecase.GetMonthlyBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class MonthTotal(
    val month: String,   // "YYYY-MM"
    val label: String,   // "Jan", "Feb" …
    val total: Double
)

data class MerchantStat(
    val merchant: String,
    val total: Double,
    val count: Int
)

data class ReportUiState(
    val selectedMonth: String           = "",
    val availableMonths: List<String>   = emptyList(),
    val monthlyTotal: Double            = 0.0,
    val monthlyBudget: Double           = 0.0,
    val avgDailySpend: Double           = 0.0,
    val highestExpense: Expense?        = null,
    val topCategory: String             = "",
    val monthlyTotals: List<MonthTotal> = emptyList(),
    val dailyTotals: Map<Int, Double>   = emptyMap(),
    val daysInMonth: Int                = 30,
    val firstDayOfWeek: Int             = 0,   // 0 = Sunday offset
    val topMerchants: List<MerchantStat> = emptyList(),
    val selectedDay: Int?               = null,
    val selectedDayExpenses: List<Expense> = emptyList(),
    val isLoading: Boolean              = true,
    val shareUri: Uri?                  = null
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val getMonthlyBudgetUseCase: GetMonthlyBudgetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private val monthLabelFmt  = SimpleDateFormat("MMM", Locale.US)
    private val monthKeyFmt    = SimpleDateFormat("yyyy-MM", Locale.US)

    init {
        val months = buildAvailableMonths()
        val current = months.first()
        _uiState.update { it.copy(availableMonths = months, selectedMonth = current) }
        loadReportData(current)
    }

    fun onMonthSelected(month: String) {
        _uiState.update { it.copy(selectedMonth = month, isLoading = true) }
        loadReportData(month)
    }

    fun onDaySelected(day: Int) {
        val expenses = _uiState.value.selectedDayExpenses
        // If same day tapped again → deselect
        if (_uiState.value.selectedDay == day) {
            _uiState.update { it.copy(selectedDay = null, selectedDayExpenses = emptyList()) }
            return
        }
        val month = _uiState.value.selectedMonth
        viewModelScope.launch {
            val all = expenseRepository.getByMonth(month).firstOrNull() ?: emptyList()
            val dayExpenses = all.filter { expense ->
                val cal = Calendar.getInstance().apply { timeInMillis = expense.transactionAt }
                cal.get(Calendar.DAY_OF_MONTH) == day && expense.isLogged
            }
            _uiState.update { it.copy(selectedDay = day, selectedDayExpenses = dayExpenses) }
        }
    }

    fun onDismissDaySheet() {
        _uiState.update { it.copy(selectedDay = null, selectedDayExpenses = emptyList()) }
    }

    fun onExport() {
        viewModelScope.launch {
            exportDataUseCase(_uiState.value.selectedMonth).onSuccess { uri ->
                _uiState.update { it.copy(shareUri = uri) }
            }
        }
    }

    fun clearShareUri() {
        _uiState.update { it.copy(shareUri = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun loadReportData(month: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val expenses = expenseRepository.getByMonth(month).firstOrNull()
                ?.filter { it.isLogged } ?: emptyList()

            val total = expenses.sumOf { it.amount }
            val budget = getMonthlyBudgetUseCase(month).firstOrNull()?.overall ?: 0.0
            val daysInMonth = daysInMonth(month)
            val firstDow = firstDayOfWeek(month)

            // Average: divide by days elapsed in month (capped to days in month)
            val daysElapsed = daysElapsed(month, daysInMonth)
            val avg = if (daysElapsed > 0) total / daysElapsed else 0.0

            val highest = expenses.maxByOrNull { it.amount }

            // Category totals
            val catTotals = expenses.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
            val topCat = catTotals.maxByOrNull { it.value }?.key ?: ""

            // Daily totals map
            val dailyMap = expenseRepository.getDailyTotalsForMonth(month)
                .associate { it.day to it.total }

            // Merchant stats
            val merchantStats = expenses.groupBy { it.merchant }
                .map { (merchant, list) ->
                    MerchantStat(
                        merchant = merchant,
                        total    = list.sumOf { it.amount },
                        count    = list.size
                    )
                }
                .sortedByDescending { it.total }
                .take(5)

            // 6-month bar chart data
            val monthlyTotals = buildMonthlyTotals(month)

            _uiState.update {
                it.copy(
                    monthlyTotal    = total,
                    monthlyBudget   = budget,
                    avgDailySpend   = avg,
                    highestExpense  = highest,
                    topCategory     = topCat,
                    dailyTotals     = dailyMap,
                    daysInMonth     = daysInMonth,
                    firstDayOfWeek  = firstDow,
                    topMerchants    = merchantStats,
                    monthlyTotals   = monthlyTotals,
                    isLoading       = false
                )
            }
        }
    }

    private suspend fun buildMonthlyTotals(currentMonth: String): List<MonthTotal> {
        val cal = Calendar.getInstance()
        // Parse current month
        cal.time = monthKeyFmt.parse(currentMonth) ?: return emptyList()

        return (5 downTo 0).map { offset ->
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -offset)
            val key   = monthKeyFmt.format(c.time)
            val label = monthLabelFmt.format(c.time)
            val total = expenseRepository.getTotalByMonthOnce(key)
            MonthTotal(key, label, total)
        }
    }

    private fun buildAvailableMonths(): List<String> {
        val cal = Calendar.getInstance()
        return (0 until 13).map {
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -it)
            monthKeyFmt.format(c.time)
        }
    }

    private fun daysInMonth(month: String): Int {
        val cal = Calendar.getInstance()
        cal.time = monthKeyFmt.parse(month) ?: return 30
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun firstDayOfWeek(month: String): Int {
        val cal = Calendar.getInstance()
        cal.time = monthKeyFmt.parse(month) ?: return 0
        cal.set(Calendar.DAY_OF_MONTH, 1)
        // Sunday = 1 in Calendar, we want 0-based offset → subtract 1
        return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    private fun daysElapsed(month: String, daysInMonth: Int): Int {
        val cal = Calendar.getInstance()
        val monthKey = monthKeyFmt.format(cal.time)
        return if (month == monthKey) {
            cal.get(Calendar.DAY_OF_MONTH)
        } else {
            daysInMonth
        }
    }
}

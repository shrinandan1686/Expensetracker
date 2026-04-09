package com.trackit.expense.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.data.local.dao.CategoryTotal
import com.trackit.expense.domain.usecase.GetCategoryTotalsUseCase
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

data class AnalyticsUiState(
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val currentMonth: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val getCategoryTotalsUseCase: GetCategoryTotalsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    init {
        _uiState.update { it.copy(currentMonth = monthKey) }
        observeCategoryTotals()
    }

    private fun observeCategoryTotals() {
        viewModelScope.launch {
            getCategoryTotalsUseCase(monthKey).collect { totals ->
                _uiState.update { it.copy(categoryTotals = totals, isLoading = false) }
            }
        }
    }
}

package com.trackit.expense.domain.usecase

import com.trackit.expense.data.local.dao.CategoryTotal
import com.trackit.expense.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves spending totals grouped by category for a specific month.
 *
 * Used by the Home screen (Category chips) and the Analytics screen (Pie chart).
 */
class GetCategoryTotalsUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(month: String): Flow<List<CategoryTotal>> {
        return repository.getCategoryTotalsByMonth(month)
    }
}

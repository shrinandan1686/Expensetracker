package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Budget
import com.trackit.expense.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves the budget configuration for a specific month.
 *
 * Emits a [Budget] object containing the overall cap and category-specific
 * limits. If no budget is set for the month, it may return a default/empty
 * budget depending on repository implementation.
 */
class GetMonthlyBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository
) {
    operator fun invoke(month: String): Flow<Budget?> {
        return repository.getByMonth(month)
    }
}

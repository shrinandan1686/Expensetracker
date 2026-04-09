package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Budget
import com.trackit.expense.domain.repository.BudgetRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Saves or updates a budget.
 */
class SaveBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository
) {
    suspend operator fun invoke(
        month: String,
        amount: Double
    ): Result<Unit> {
        val budget = Budget(
            id = UUID.randomUUID().toString(),
            month = month,
            overall = amount,
            categoryBudgets = emptyMap()
        )
        return repository.save(budget)
    }
}

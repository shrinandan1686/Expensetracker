package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.repository.ExpenseRepository
import javax.inject.Inject

/**
 * Use case: mark an expense as user-reviewed via the overlay popup.
 * Sets [Expense.isLogged] = true and records [Expense.loggedAt] = now.
 */
class MarkExpenseLoggedUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(
        id: String,
        loggedAt: Long = System.currentTimeMillis()
    ): Result<Unit> = repository.markLogged(id, loggedAt)
}

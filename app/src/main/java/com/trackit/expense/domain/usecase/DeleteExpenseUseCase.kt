package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.repository.ExpenseRepository
import javax.inject.Inject

/**
 * Use case: delete an expense by its UUID.
 *
 * Deletions are local-only in the current version — the record is removed
 * from Room but no DELETE call is made to the server. A future version should
 * either soft-delete (is_deleted flag + sync) or add a server DELETE endpoint.
 */
class DeleteExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}

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

/**
 * Use case: trigger an immediate background sync via WorkManager.
 * Typically called from the overlay service after saving an expense, or from
 * a manual "Sync Now" action in Settings.
 */
class SyncExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke() = repository.enqueueSyncWork()
}

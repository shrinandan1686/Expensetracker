package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.repository.ExpenseRepository
import javax.inject.Inject

/**
 * Use case: Trigger a manual or scheduled sync of unsynced expenses to the remote API.
 *
 * Called by [SyncWorker] (WorkManager) for background syncs, or directly from a
 * ViewModel for on-demand sync.
 *
 * Returns [Result.success] if sync completed successfully,
 * or [Result.failure] with the exception if the network call failed.
 *
 * Injected by Hilt.
 */
class SyncExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.syncExpenses()
}

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

package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to retrieve a single expense by its unique identifier.
 *
 * This is used primarily by the Expense Detail screen to show data for a
 * specific transaction.
 */
class GetExpenseByIdUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(id: String): Flow<Expense?> {
        return repository.getById(id)
    }
}

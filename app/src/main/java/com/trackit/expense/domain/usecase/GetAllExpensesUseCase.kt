package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: observe the full expense list, ordered by transaction time (newest first).
 *
 * Returns a [Flow] that Room re-emits whenever any expense is inserted, updated,
 * or deleted. ViewModels collect this and expose [UiState] to composables.
 */
class GetAllExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(): Flow<List<Expense>> = repository.getAll()
}

/**
 * Use case: observe only unreviewed (auto-detected) expenses.
 * Useful for the "Pending Review" badge / list in the Home screen.
 */
class GetUnloggedExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(): Flow<List<Expense>> = repository.getUnlogged()
}

/**
 * Use case: observe expenses for a specific calendar month.
 * @param month ISO month string — "YYYY-MM" (e.g. "2026-03").
 */
class GetExpensesByMonthUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(month: String): Flow<List<Expense>> = repository.getByMonth(month)
}

/**
 * Use case: observe the total spend for a calendar month (logged expenses only).
 * Emits 0.0 when no logged expenses exist for the month.
 * @param month ISO month string — "YYYY-MM".
 */
class GetMonthlyTotalUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(month: String): Flow<Double> = repository.getTotalByMonth(month)
}

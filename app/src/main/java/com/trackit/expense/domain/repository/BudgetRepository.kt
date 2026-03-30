package com.trackit.expense.domain.repository

import com.trackit.expense.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for monthly budget persistence.
 *
 * Budgets are write-through to Room (offline-first). Budget data is local-only
 * in the current version — sync to the server is not implemented but reserved.
 */
interface BudgetRepository {

    /** All budgets ordered by month descending. */
    fun getAll(): Flow<List<Budget>>

    /**
     * The budget for [month], or null if none has been set.
     * @param month ISO month string, e.g. "2026-03".
     */
    fun getByMonth(month: String): Flow<Budget?>

    /**
     * Insert or replace the budget for its month.
     * Uses REPLACE conflict strategy so re-saving with the same month always wins.
     */
    suspend fun save(budget: Budget): Result<Unit>

    /** Delete a budget by its [id]. */
    suspend fun delete(id: String): Result<Unit>
}

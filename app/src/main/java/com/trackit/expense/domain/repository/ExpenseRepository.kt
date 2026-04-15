package com.trackit.expense.domain.repository

import com.trackit.expense.data.local.dao.DailyTotal
import com.trackit.expense.domain.model.Expense
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for all expense persistence operations.
 *
 * ## Offline-first guarantee
 * Implementations **must** write to the local Room database before returning,
 * then queue a background [com.trackit.expense.worker.SyncWorker] task.
 * The UI always observes Room via the returned [Flow]s and never waits for
 * a network response.
 *
 * All write operations return [Result] so callers can handle DB errors without
 * crashing — the UI shows a generic error snackbar on [Result.failure].
 */
interface ExpenseRepository {

    // ────────────────────────────── READS ─────────────────────────────────────

    /** All expenses ordered by [Expense.transactionAt] descending. */
    fun getAll(): Flow<List<Expense>>

    /** Expenses where [Expense.isLogged] == false (auto-detected, not yet reviewed). */
    fun getUnlogged(): Flow<List<Expense>>

    /**
     * Expenses whose [Expense.transactionAt] falls within [month].
     * @param month ISO month string, e.g. "2026-03".
     */
    fun getByMonth(month: String): Flow<List<Expense>>

    /** Expenses matching [category] exactly (case-sensitive). */
    fun getByCategory(category: String): Flow<List<Expense>>

    /**
     * Sum of [Expense.amount] for all **logged** expenses in [month].
     * @param month ISO month string, e.g. "2026-03".
     * @return Reactive Double in INR. Emits 0.0 when no expenses exist.
     */
    fun getTotalByMonth(month: String): Flow<Double>

    /** Single expense by its UUID [id]. Emits null if not found. */
    fun getById(id: String): Flow<Expense?>

    /**
     * Spending totals grouped by category for a specific month.
     */
    fun getCategoryTotalsByMonth(month: String): Flow<List<com.trackit.expense.data.local.dao.CategoryTotal>>

    /**
     * One-shot (non-reactive) sum of logged expenses for [month].
     * Used by the Report ViewModel where a Flow isn't needed.
     */
    suspend fun getTotalByMonthOnce(month: String): Double

    /**
     * Per-day spending totals for [month] (one-shot).
     * Returns a list of [DailyTotal] sorted by day ascending.
     * Days with no spending are omitted — callers should fill missing days with 0.
     */
    suspend fun getDailyTotalsForMonth(month: String): List<DailyTotal>

    // ────────────────────────────── WRITES ────────────────────────────────────

    /**
     * Persist a new or replacement expense to Room and enqueue a sync task.
     * @return The UUID of the saved expense, or an exception wrapped in [Result].
     */
    suspend fun save(expense: Expense): Result<String>

    /**
     * Update an existing expense record in Room and enqueue a sync task.
     * The [Expense.id] must already exist in the database.
     */
    suspend fun update(expense: Expense): Result<Unit>

    /**
     * Delete an expense by its [id].
     * NB: Deleted records are not propagated to the server in this version.
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Mark a specific expense as user-reviewed:
     * sets [Expense.isLogged] = true and [Expense.loggedAt] = now.
     */
    suspend fun markLogged(id: String, loggedAt: Long = System.currentTimeMillis()): Result<Unit>

    // ────────────────────────────── SYNC ──────────────────────────────────────

    /**
     * Enqueue a one-shot [com.trackit.expense.worker.SyncWorker] to push all
     * unsynced records to the remote API.
     * The actual HTTP call happens in the worker off the main thread.
     */
    fun enqueueSyncWork()
}

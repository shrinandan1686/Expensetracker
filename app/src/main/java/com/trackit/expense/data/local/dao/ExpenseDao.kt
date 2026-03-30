package com.trackit.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trackit.expense.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [ExpenseEntity].
 *
 * ## Reactive reads
 * All query functions return [Flow] so Room re-emits automatically whenever the
 * underlying data changes — no manual refresh needed in ViewModels.
 *
 * ## Month format
 * Month-scoped queries accept an ISO month string in **"YYYY-MM"** format (e.g. "2026-03").
 * SQLite's `strftime('%Y-%m', datetime(transaction_at/1000, 'unixepoch'))` converts the
 * epoch-millisecond [ExpenseEntity.transactionAt] column to that format at query time.
 *
 * ⚠️ Timezone note: `strftime` in SQLite uses UTC. If users are in non-UTC timezones,
 * transactions close to month boundaries may appear in the wrong month bucket.
 * Pass UTC-normalised epoch values (or pre-filter at the repository layer with explicit
 * start/end millis) for exact month boundaries.
 */
@Dao
interface ExpenseDao {

    // ─────────────────────────────── INSERT ─────────────────────────────────

    /**
     * Insert a single expense. [OnConflictStrategy.REPLACE] acts as an upsert —
     * an existing row with the same UUID is fully replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    /**
     * Bulk-insert a list of expenses (e.g., after seeding demo data).
     * Uses [OnConflictStrategy.IGNORE] to skip duplicates without rolling back.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    // ─────────────────────────────── UPDATE ─────────────────────────────────

    @Update
    suspend fun update(expense: ExpenseEntity)

    /** Mark a single expense as reviewed by the user (sets is_logged + logged_at). */
    @Query("UPDATE expenses SET is_logged = 1, logged_at = :loggedAt WHERE id = :id")
    suspend fun markLogged(id: String, loggedAt: Long)

    /**
     * Mark a batch of expenses as synced after a successful API call.
     * Called by [com.trackit.expense.worker.SyncWorker] after a successful POST.
     * Uses IN clause — supports up to ~999 IDs before hitting SQLite limits.
     */
    @Query("UPDATE expenses SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    // ─────────────────────────────── DELETE ─────────────────────────────────

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    // ─────────────────────────────── SELECT — ALL ────────────────────────────

    /** All expenses sorted by transaction time, newest first. */
    @Query("SELECT * FROM expenses ORDER BY transaction_at DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    /** A single expense by UUID. Emits null if the record is not found. */
    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<ExpenseEntity?>

    // ─────────────────────────────── SELECT — FILTERED ──────────────────────

    /**
     * Expenses not yet reviewed by the user in the overlay popup.
     * These are auto-saved records awaiting user confirmation.
     */
    @Query("""
        SELECT * FROM expenses
        WHERE is_logged = 0
        ORDER BY transaction_at DESC
    """)
    fun getUnlogged(): Flow<List<ExpenseEntity>>

    /**
     * Expenses whose [ExpenseEntity.transactionAt] falls within [month].
     * @param month "YYYY-MM" format, e.g. "2026-03".
     */
    @Query("""
        SELECT * FROM expenses
        WHERE strftime('%Y-%m', datetime(transaction_at / 1000, 'unixepoch')) = :month
        ORDER BY transaction_at DESC
    """)
    fun getByMonth(month: String): Flow<List<ExpenseEntity>>

    /**
     * Expenses for [month] falling within an explicit UTC epoch range.
     * Use this instead of [getByMonth] when exact timezone-aware boundaries are needed.
     */
    @Query("""
        SELECT * FROM expenses
        WHERE transaction_at >= :startMs AND transaction_at < :endMs
        ORDER BY transaction_at DESC
    """)
    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<ExpenseEntity>>

    /** Expenses matching [category] exactly. */
    @Query("""
        SELECT * FROM expenses
        WHERE category = :category
        ORDER BY transaction_at DESC
    """)
    fun getByCategory(category: String): Flow<List<ExpenseEntity>>

    // ─────────────────────────────── AGGREGATES ─────────────────────────────

    /**
     * Sum of [ExpenseEntity.amount] for all **logged** expenses in [month].
     * Returns 0.0 when no matching records exist (COALESCE prevents NULL).
     * @param month "YYYY-MM" format.
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses
        WHERE strftime('%Y-%m', datetime(transaction_at / 1000, 'unixepoch')) = :month
          AND is_logged = 1
    """)
    fun getTotalByMonth(month: String): Flow<Double>

    /**
     * Sum grouped by category for [month]. Returns a list of (category, total) pairs.
     * Useful for budget progress bars.
     */
    @Query("""
        SELECT category, COALESCE(SUM(amount), 0.0) AS total FROM expenses
        WHERE strftime('%Y-%m', datetime(transaction_at / 1000, 'unixepoch')) = :month
          AND is_logged = 1
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getTotalByCategoryForMonth(month: String): Flow<List<CategoryTotal>>

    // ─────────────────────────────── SYNC ───────────────────────────────────

    /**
     * One-shot (non-reactive) query for the sync worker.
     * Returns all records not yet confirmed by the server — called inside
     * [com.trackit.expense.worker.SyncWorker.doWork].
     */
    @Query("SELECT * FROM expenses WHERE is_synced = 0")
    suspend fun getUnsynced(): List<ExpenseEntity>

    /** Count of unsynced records — used to decide whether to start a sync. */
    @Query("SELECT COUNT(*) FROM expenses WHERE is_synced = 0")
    suspend fun getUnsyncedCount(): Int
}

/**
 * Lightweight projection returned by [ExpenseDao.getTotalByCategoryForMonth].
 * Not a full entity — Room maps this via column name matching.
 */
data class CategoryTotal(
    val category: String,
    val total: Double
)

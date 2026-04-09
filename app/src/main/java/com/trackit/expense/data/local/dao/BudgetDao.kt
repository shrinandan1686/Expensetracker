package com.trackit.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trackit.expense.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [BudgetEntity].
 *
 * Budgets are keyed by [BudgetEntity.month] ("YYYY-MM"). There is at most one
 * budget row per month — the UNIQUE index on [month] enforces this at the DB level
 * and [OnConflictStrategy.REPLACE] in [upsert] handles updates transparently.
 */
@Dao
interface BudgetDao {

    // ──────────────────────────── WRITE ──────────────────────────────────────

    /**
     * Insert or fully replace the budget for a given month.
     * Because [BudgetEntity.month] has a UNIQUE index, inserting a budget with an
     * existing month replaces the previous row — effectively an upsert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM budgets WHERE month = :month")
    suspend fun deleteByMonth(month: String)

    // ──────────────────────────── READ ───────────────────────────────────────

    /**
     * The budget for [month], or null if none has been set.
     * @param month "YYYY-MM" format, e.g. "2026-03".
     */
    @Query("SELECT * FROM budgets WHERE month = :month LIMIT 1")
    fun getByMonth(month: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE month = :month LIMIT 1")
    suspend fun getByMonthOneShot(month: String): BudgetEntity?

    /** All budgets, most recent month first. */
    @Query("SELECT * FROM budgets ORDER BY month DESC")
    fun getAll(): Flow<List<BudgetEntity>>

    /** One-shot check — returns true if a budget exists for [month]. */
    @Query("SELECT COUNT(*) > 0 FROM budgets WHERE month = :month")
    suspend fun existsForMonth(month: String): Boolean
}

package com.trackit.expense.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trackit.expense.data.local.dao.BudgetDao
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.data.local.entity.BudgetEntity
import com.trackit.expense.data.local.entity.ExpenseEntity

/**
 * Main Room database for TrackIt — single source of truth for all local data.
 *
 * ## Version history
 * | Version | Change |
 * |---------|--------|
 * | 1       | Initial: expenses + categories tables (Long id, paise amount) |
 * | 2       | Added `is_logged` column to expenses |
 * | 3       | Full schema redesign: UUID ids, Double amount, budget table added |
 *
 * ## Migrations
 * See [DatabaseMigrations] for SQL migration objects.
 * In **debug builds**, [fallbackToDestructiveMigration] drops and recreates the
 * database automatically — no migration SQL runs. Replace this with explicit
 * [addMigrations] calls before a production release.
 *
 * ## Usage
 * Built once by Hilt in [DatabaseModule]. Consumers inject the DAO interfaces,
 * not this class directly.
 */
@Database(
    entities = [
        ExpenseEntity::class,
        BudgetEntity::class
    ],
    version = 3,
    exportSchema = true   // generates schema JSON file for migration validation
)
abstract class TrackItDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DATABASE_NAME = "trackit_db"
    }
}

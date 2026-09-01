package com.trackit.expense.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trackit.expense.data.local.dao.AccountDao
import com.trackit.expense.data.local.dao.BudgetDao
import com.trackit.expense.data.local.dao.CategoryDao
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.data.local.dao.GroupDao
import com.trackit.expense.data.local.dao.SplitDao
import com.trackit.expense.data.local.entity.AccountEntity
import com.trackit.expense.data.local.entity.BudgetEntity
import com.trackit.expense.data.local.entity.CategoryEntity
import com.trackit.expense.data.local.entity.ExpenseEntity
import com.trackit.expense.data.local.entity.GroupEntity
import com.trackit.expense.data.local.entity.SplitEntity

/**
 * Main Room database for TrackIt — single source of truth for all local data.
 *
 * ## Version history
 * | Version | Change |
 * |---------|--------|
 * | 1       | Initial: expenses + categories tables (Long id, paise amount) |
 * | 2       | Added `is_logged` column to expenses |
 * | 3       | Full schema redesign: UUID ids, Double amount, budget table added |
 * | 4       | Added accounts table, updated categories schema, pre-seed logic |
 *
 * ## Migrations
 * [DatabaseMigrations.ALL] is wired into the builder in [DatabaseModule] and must
 * cover every consecutive version pair up to [version]. Destructive fallback is
 * enabled in debug builds only — in release it is off, so a missing migration
 * fails loudly at open time instead of silently wiping the user's history.
 *
 * ## Usage
 * Built once by Hilt in [DatabaseModule]. Consumers inject the DAO interfaces,
 * not this class directly.
 */
@Database(
    entities = [
        ExpenseEntity::class,
        BudgetEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        GroupEntity::class,
        SplitEntity::class
    ],
    version = 8,
    // Exported to app/schemas/ so each migration can be checked against the
    // schema Room actually expects, instead of being verified only at runtime.
    exportSchema = true
)
abstract class TrackItDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun groupDao(): GroupDao
    abstract fun splitDao(): SplitDao

    companion object {
        const val DATABASE_NAME = "trackit_db"

        /**
         * Returns a list of default categories to be pre-seeded.
         */
        fun getDefaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity(name = "Food", emoji = "🍔", sortOrder = 1),
            CategoryEntity(name = "Transport", emoji = "🚗", sortOrder = 2),
            CategoryEntity(name = "Rent", emoji = "🏠", sortOrder = 3),
            CategoryEntity(name = "Shopping", emoji = "🛍️", sortOrder = 4),
            CategoryEntity(name = "Health", emoji = "💊", sortOrder = 5),
            CategoryEntity(name = "Utilities", emoji = "⚡", sortOrder = 6),
            CategoryEntity(name = "Entertainment", emoji = "🎬", sortOrder = 7),
            CategoryEntity(name = "Misc", emoji = "📦", sortOrder = 8)
        )
    }
}

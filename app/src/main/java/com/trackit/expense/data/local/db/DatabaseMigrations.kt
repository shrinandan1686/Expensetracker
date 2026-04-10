package com.trackit.expense.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room [Migration] objects for TrackIt database version upgrades.
 *
 * ## How to use
 * Pass these to `Room.databaseBuilder(…).addMigrations(…)` **instead of**
 * `fallbackToDestructiveMigration()` before a production release:
 *
 * ```kotlin
 * Room.databaseBuilder(context, TrackItDatabase::class.java, DATABASE_NAME)
 *     .addMigrations(
 *         DatabaseMigrations.MIGRATION_1_2,
 *         DatabaseMigrations.MIGRATION_2_3
 *     )
 *     .build()
 * ```
 *
 * ## Development note
 * While `fallbackToDestructiveMigration()` is active in [DatabaseModule], these
 * migration objects are **never executed** — they are preserved here so they can
 * be enabled for a production release without rewriting from scratch.
 *
 * ## SQLite limitations
 * - Columns cannot be renamed or retyped directly in SQLite.
 * - `MIGRATION_2_3` uses a full table-recreation pattern (create_new → copy → drop → rename).
 * - Data is migrated best-effort; paise amounts are divided by 100 to convert to INR Double.
 */
object DatabaseMigrations {

    // ────────────────────────────────────────────────────────────────────────
    // v1 → v2 : Added is_logged column to expenses table
    // ────────────────────────────────────────────────────────────────────────

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Safe ALTER — SQLite allows adding a nullable/default column without rebuild
            db.execSQL(
                "ALTER TABLE expenses ADD COLUMN is_logged INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // v2 → v3 : Full schema redesign
    //
    // Changes:
    //   expenses.id         BIGINT auto-increment → TEXT (UUID)
    //   expenses.amount     BIGINT (paise)        → REAL  (INR Double)
    //   expenses.timestamp  renamed               → transaction_at
    //   expenses.note       removed               (merged into notes)
    //   expenses.merchant_name → merchant
    //   expenses.category_id   → category (denormalised String)
    //   expenses.payment_method → account (repurposed)
    //   expenses.upi_transaction_id removed
    //   expenses.logged_at  NEW nullable column
    //   expenses.raw_sms    NEW column
    //
    //   budgets             NEW table
    //   categories          DROPPED (denormalisation)
    // ────────────────────────────────────────────────────────────────────────

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // ── 1. Create new expenses table ─────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS expenses_new (
                    id               TEXT    NOT NULL PRIMARY KEY,
                    amount           REAL    NOT NULL DEFAULT 0.0,
                    merchant         TEXT    NOT NULL DEFAULT '',
                    category         TEXT    NOT NULL DEFAULT 'Others',
                    account          TEXT    NOT NULL DEFAULT '',
                    notes            TEXT,
                    raw_sms          TEXT    NOT NULL DEFAULT '',
                    is_logged        INTEGER NOT NULL DEFAULT 0,
                    is_synced        INTEGER NOT NULL DEFAULT 0,
                    transaction_at   INTEGER NOT NULL DEFAULT 0,
                    logged_at        INTEGER,
                    created_at       INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // ── 2. Best-effort data migration (v2 column names) ───────────────
            // amount is converted from paise (BIGINT) to INR (REAL / Double)
            db.execSQL("""
                INSERT INTO expenses_new (
                    id, amount, merchant, category, account,
                    raw_sms, is_logged, is_synced, transaction_at, created_at
                )
                SELECT
                    CAST(id AS TEXT),
                    CAST(amount AS REAL) / 100.0,
                    COALESCE(merchant_name, ''),
                    'Others',
                    COALESCE(payment_method, ''),
                    '',
                    COALESCE(is_logged, 0),
                    COALESCE(is_synced, 0),
                    COALESCE(timestamp, 0),
                    COALESCE(created_at, 0)
                FROM expenses
            """.trimIndent())

            // ── 3. Swap tables ────────────────────────────────────────────────
            db.execSQL("DROP TABLE IF EXISTS expenses")
            db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")

            // ── 4. Recreate indices on new table ─────────────────────────────
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_transaction_at ON expenses(transaction_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_is_synced ON expenses(is_synced)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_is_logged ON expenses(is_logged)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_category ON expenses(category)")

            // ── 5. Create budgets table ───────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id               TEXT NOT NULL PRIMARY KEY,
                    month            TEXT NOT NULL,
                    overall          REAL NOT NULL DEFAULT 0.0,
                    category_budgets TEXT NOT NULL DEFAULT '{}'
                )
            """.trimIndent())
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_month ON budgets(month)"
            )

            // ── 6. Drop legacy categories table (no longer needed) ────────────
            db.execSQL("DROP TABLE IF EXISTS categories")
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // v4 → v5 : Added location fields (latitude, longitude, address)
    // ────────────────────────────────────────────────────────────────────────
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN latitude REAL")
            db.execSQL("ALTER TABLE expenses ADD COLUMN longitude REAL")
            db.execSQL("ALTER TABLE expenses ADD COLUMN location_address TEXT")
        }
    }
}

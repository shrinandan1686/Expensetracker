package com.trackit.expense.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room [Migration] objects for TrackIt database version upgrades.
 *
 * ## Wiring
 * [ALL] is passed to `Room.databaseBuilder(…).addMigrations(…)` in [DatabaseModule].
 * Every consecutive version pair from 1 to the current schema version must be
 * present in [ALL] — a gap makes Room fall back to a destructive migration, which
 * silently wipes the user's entire expense history.
 *
 * ## Adding a migration
 * 1. Bump `version` in [TrackItDatabase].
 * 2. Add a `MIGRATION_<old>_<new>` object below.
 * 3. Add it to [ALL].
 * 4. Check the generated `app/schemas/<version>.json` matches what your SQL builds.
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
    // v3 → v4 : Re-introduced the accounts and categories tables
    //
    // v2 → v3 dropped `categories` in favour of a denormalised category string on
    // `expenses`. v4 brought both tables back as user-editable reference data:
    // accounts for card/UPI labels, categories for the picker in Settings.
    //
    // No data to carry over — v3 had no equivalent tables. DatabaseModule's
    // onCreate callback seeds default categories on a fresh install; an upgrading
    // install gets them from CategoryDao on first read instead.
    // ────────────────────────────────────────────────────────────────────────

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id         TEXT    NOT NULL PRIMARY KEY,
                    name       TEXT    NOT NULL,
                    last_4     TEXT    NOT NULL,
                    type       TEXT    NOT NULL,
                    is_default INTEGER NOT NULL,
                    color_hex  TEXT    NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS categories (
                    id         TEXT    NOT NULL PRIMARY KEY,
                    name       TEXT    NOT NULL,
                    emoji      TEXT    NOT NULL,
                    is_enabled INTEGER NOT NULL,
                    is_custom  INTEGER NOT NULL,
                    sort_order INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
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

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE expenses SET updated_at = created_at WHERE updated_at = 0")
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // v6 → v7 : Added groups and splits tables for Phase 2 group splits feature
    // ────────────────────────────────────────────────────────────────────────
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS groups (
                    id           TEXT NOT NULL PRIMARY KEY,
                    name         TEXT NOT NULL,
                    emoji        TEXT,
                    created_by   TEXT NOT NULL,
                    members_json TEXT NOT NULL DEFAULT '[]',
                    created_at   INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_groups_created_by ON groups(created_by)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS splits (
                    id                TEXT    NOT NULL PRIMARY KEY,
                    group_id          TEXT    NOT NULL,
                    description       TEXT    NOT NULL,
                    total_amount      REAL    NOT NULL DEFAULT 0.0,
                    paid_by           TEXT    NOT NULL,
                    participants_json TEXT    NOT NULL DEFAULT '[]',
                    expense_id        TEXT,
                    created_at        INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_splits_group_id ON splits(group_id)")
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // v7 → v8 : Soft-delete tombstones
    //
    // Deleting a row outright meant the deletion never reached the server, so the
    // expense came back on the next pull. is_deleted marks the row as a tombstone:
    // it stays local (and syncable) until the server has acknowledged it, and every
    // read query filters it out.
    // ────────────────────────────────────────────────────────────────────────

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_is_deleted ON expenses(is_deleted)")
        }
    }

    /**
     * Every migration, in order. Consecutive from 1 to the current schema version —
     * a missing pair means Room destroys and recreates the database instead.
     */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8
    )
}

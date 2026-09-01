package com.trackit.expense.data.local.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the migration chain preserves data.
 *
 * These exist because of what used to be here: `fallbackToDestructiveMigration()`
 * meant a schema bump silently dropped every expense a user had recorded. Now that
 * real migrations are wired up, something has to prove they work — a migration that
 * throws or loses rows is the same data-loss bug in different clothing.
 *
 * Schemas for versions 1–7 were never exported (`exportSchema` was false until v8),
 * so [MigrationTestHelper.createDatabase] cannot build those old databases from
 * JSON. The old schema is therefore written with raw SQL matching what the earlier
 * migrations leave behind, which is the state a real device is actually in.
 * `runMigrationsAndValidate` still validates the *result* against the committed
 * `8.json`, which is the assertion that matters.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val CURRENT_VERSION = 8
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackItDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Creates the test database at [version] with the given schema, bypassing
     * MigrationTestHelper (which would need an exported JSON for that version).
     */
    private fun createLegacyDb(version: Int, schema: SQLiteDatabase.() -> Unit) {
        val path = context.getDatabasePath(TEST_DB)
        path.parentFile?.mkdirs()
        path.delete()
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            schema()
            this.version = version
            close()
        }
    }

    /** The v3 `expenses` and `budgets` tables, as left by MIGRATION_2_3. */
    private fun SQLiteDatabase.createV3Schema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS expenses (
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
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_expenses_transaction_at ON expenses(transaction_at)")
        execSQL("CREATE INDEX IF NOT EXISTS index_expenses_is_synced ON expenses(is_synced)")
        execSQL("CREATE INDEX IF NOT EXISTS index_expenses_is_logged ON expenses(is_logged)")
        execSQL("CREATE INDEX IF NOT EXISTS index_expenses_category ON expenses(category)")
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS budgets (
                id               TEXT NOT NULL PRIMARY KEY,
                month            TEXT NOT NULL,
                overall          REAL NOT NULL DEFAULT 0.0,
                category_budgets TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent()
        )
        execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_month ON budgets(month)")
    }

    /** v3 plus the columns added by MIGRATION_4_5 and MIGRATION_5_6, and the v6_7 tables. */
    private fun SQLiteDatabase.createV7Schema() {
        createV3Schema()
        execSQL("ALTER TABLE expenses ADD COLUMN latitude REAL")
        execSQL("ALTER TABLE expenses ADD COLUMN longitude REAL")
        execSQL("ALTER TABLE expenses ADD COLUMN location_address TEXT")
        execSQL("ALTER TABLE expenses ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS accounts (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, last_4 TEXT NOT NULL,
                type TEXT NOT NULL, is_default INTEGER NOT NULL, color_hex TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL,
                is_enabled INTEGER NOT NULL, is_custom INTEGER NOT NULL,
                sort_order INTEGER NOT NULL, created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS groups (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, emoji TEXT,
                created_by TEXT NOT NULL, members_json TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_groups_created_by ON groups(created_by)")
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS splits (
                id TEXT NOT NULL PRIMARY KEY, group_id TEXT NOT NULL, description TEXT NOT NULL,
                total_amount REAL NOT NULL DEFAULT 0.0, paid_by TEXT NOT NULL,
                participants_json TEXT NOT NULL DEFAULT '[]', expense_id TEXT,
                created_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_splits_group_id ON splits(group_id)")
    }

    @Test
    fun migrate7To8_addsTombstoneColumn_andKeepsExistingExpenses() {
        createLegacyDb(7) {
            createV7Schema()
            execSQL(
                """
                INSERT INTO expenses (id, amount, merchant, category, account, is_logged, transaction_at, created_at, updated_at)
                VALUES ('keep-me', 249.5, 'Swiggy', 'Food', 'XX1234', 1, 1700000000000, 1700000000000, 1700000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, CURRENT_VERSION, true, DatabaseMigrations.MIGRATION_7_8
        )

        db.query("SELECT id, amount, merchant, is_deleted FROM expenses").use { c ->
            assertThat(c.count).isEqualTo(1)
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("keep-me")
            assertThat(c.getDouble(1)).isEqualTo(249.5)
            assertThat(c.getString(2)).isEqualTo("Swiggy")
            // Existing rows must default to "not deleted", or every expense a user
            // already had would disappear from the UI the moment they update.
            assertThat(c.getInt(3)).isEqualTo(0)
        }
    }

    @Test
    fun migrateFullChainFrom3To8_preservesDataAndBuildsExpectedSchema() {
        createLegacyDb(3) {
            createV3Schema()
            execSQL(
                """
                INSERT INTO expenses (id, amount, merchant, category, is_logged, transaction_at, created_at)
                VALUES ('old-row', 100.0, 'DMart', 'Groceries', 1, 1600000000000, 1600000000000)
                """.trimIndent()
            )
        }

        // Runs 3_4, 4_5, 5_6, 6_7 and 7_8 in sequence, then validates the result
        // against the committed 8.json. MIGRATION_3_4 was missing entirely before,
        // which made this whole path a destructive migration.
        val db = helper.runMigrationsAndValidate(
            TEST_DB, CURRENT_VERSION, true, *DatabaseMigrations.ALL
        )

        db.query("SELECT id, merchant, is_deleted, updated_at FROM expenses").use { c ->
            assertThat(c.count).isEqualTo(1)
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("old-row")
            assertThat(c.getString(1)).isEqualTo("DMart")
            assertThat(c.getInt(2)).isEqualTo(0)
            // MIGRATION_5_6 backfills updated_at from created_at.
            assertThat(c.getLong(3)).isEqualTo(1600000000000L)
        }

        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        assertThat(tables).containsAtLeast("expenses", "budgets", "accounts", "categories", "groups", "splits")
    }

    @Test
    fun migrationChain_coversEveryConsecutiveVersion() {
        // A gap is invisible at build time and only surfaces as Room falling back to
        // a destructive migration on a real user's device.
        val pairs = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }.toSet()
        for (v in 1 until CURRENT_VERSION) {
            assertThat(pairs).contains(v to v + 1)
        }
    }
}

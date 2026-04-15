package com.trackit.expense.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.trackit.expense.data.local.db.TrackItDatabase
import com.trackit.expense.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room DAO integration tests for [ExpenseDao].
 *
 * Uses an in-memory database — all tests are isolated and self-cleaning.
 * Tests run on the Android emulator / device via androidTest.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseDaoTest {

    private lateinit var db: TrackItDatabase
    private lateinit var dao: ExpenseDao

    // Fixed timestamps for deterministic queries
    private val T  = 1_700_000_000_000L   // some epoch
    private val T1 = T + 1_000L
    private val T2 = T + 2_000L
    private val T3 = T + 3_000L

    // Month that corresponds to the epoch above (UTC)
    // 2023-11 (November 2023)
    private val MONTH = "2023-11"

    @Before fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TrackItDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.expenseDao()
    }

    @After fun closeDb() = db.close()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun expense(
        id: String,
        amount: Double   = 100.0,
        merchant: String = "Test",
        category: String = "Others",
        isLogged: Boolean = true,
        isSynced: Boolean = false,
        transactionAt: Long = T
    ) = ExpenseEntity(
        id            = id,
        amount        = amount,
        merchant      = merchant,
        category      = category,
        isLogged      = isLogged,
        isSynced      = isSynced,
        transactionAt = transactionAt
    )

    // ── INSERT ───────────────────────────────────────────────────────────────

    @Test fun insert_then_getAll_returns_inserted_expense() = runTest {
        val e = expense("id-1")
        dao.insert(e)

        val all = dao.getAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isEqualTo("id-1")
    }

    @Test fun insert_duplicate_id_replaces_existing() = runTest {
        dao.insert(expense("dup", amount = 100.0))
        dao.insert(expense("dup", amount = 999.0))

        val all = dao.getAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].amount).isEqualTo(999.0)
    }

    @Test fun insertAll_ignores_duplicate_ids() = runTest {
        dao.insert(expense("a", amount = 50.0))
        dao.insertAll(listOf(
            expense("a", amount = 200.0),   // duplicate — should be ignored
            expense("b", amount = 75.0)
        ))

        val all = dao.getAll().first()
        val ids = all.map { it.id }
        assertThat(ids).containsExactly("a", "b")
        // "a" should still have original amount because IGNORE conflict strategy
        val aAmount = all.first { it.id == "a" }.amount
        assertThat(aAmount).isEqualTo(50.0)
    }

    @Test fun insertAll_empty_list_is_no_op() = runTest {
        dao.insertAll(emptyList())
        assertThat(dao.getAll().first()).isEmpty()
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Test fun update_modifies_existing_record() = runTest {
        dao.insert(expense("upd-1", merchant = "Old Merchant"))
        dao.update(expense("upd-1", merchant = "New Merchant"))

        val result = dao.getById("upd-1").first()
        assertThat(result?.merchant).isEqualTo("New Merchant")
    }

    @Test fun markLogged_sets_is_logged_and_logged_at() = runTest {
        dao.insert(expense("log-1", isLogged = false))

        val loggedAt = System.currentTimeMillis()
        dao.markLogged("log-1", loggedAt)

        val updated = dao.getById("log-1").first()
        assertThat(updated?.isLogged).isTrue()
        assertThat(updated?.loggedAt).isEqualTo(loggedAt)
    }

    @Test fun markSynced_updates_all_provided_ids() = runTest {
        dao.insert(expense("s1", isSynced = false))
        dao.insert(expense("s2", isSynced = false))
        dao.insert(expense("s3", isSynced = false))

        dao.markSynced(listOf("s1", "s3"))

        assertThat(dao.getById("s1").first()?.isSynced).isTrue()
        assertThat(dao.getById("s2").first()?.isSynced).isFalse()
        assertThat(dao.getById("s3").first()?.isSynced).isTrue()
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test fun deleteById_removes_correct_record() = runTest {
        dao.insert(expense("del-1"))
        dao.insert(expense("del-2"))

        dao.deleteById("del-1")

        val all = dao.getAll().first()
        assertThat(all.map { it.id }).containsExactly("del-2")
    }

    @Test fun deleteById_nonexistent_id_is_no_op() = runTest {
        dao.insert(expense("keep"))
        dao.deleteById("ghost")

        assertThat(dao.getAll().first()).hasSize(1)
    }

    // ── SELECT — ALL ─────────────────────────────────────────────────────────

    @Test fun getAll_returns_expenses_newest_first() = runTest {
        dao.insert(expense("old", transactionAt = T))
        dao.insert(expense("new", transactionAt = T3))
        dao.insert(expense("mid", transactionAt = T2))

        val all = dao.getAll().first()
        assertThat(all.map { it.id }).containsExactly("new", "mid", "old").inOrder()
    }

    @Test fun getAll_empty_db_returns_empty_list() = runTest {
        assertThat(dao.getAll().first()).isEmpty()
    }

    @Test fun getById_existing_id_returns_entity() = runTest {
        dao.insert(expense("find-me", amount = 456.0))

        val result = dao.getById("find-me").first()
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(456.0)
    }

    @Test fun getById_missing_id_returns_null() = runTest {
        val result = dao.getById("does-not-exist").first()
        assertThat(result).isNull()
    }

    // ── SELECT — UNLOGGED ────────────────────────────────────────────────────

    @Test fun getUnlogged_returns_only_unreviewed_expenses() = runTest {
        dao.insert(expense("logged",   isLogged = true))
        dao.insert(expense("unlogged", isLogged = false))

        val unlogged = dao.getUnlogged().first()
        assertThat(unlogged).hasSize(1)
        assertThat(unlogged[0].id).isEqualTo("unlogged")
    }

    @Test fun getUnlogged_empty_when_all_logged() = runTest {
        dao.insert(expense("a", isLogged = true))
        dao.insert(expense("b", isLogged = true))

        assertThat(dao.getUnlogged().first()).isEmpty()
    }

    // ── SELECT — BY MONTH ────────────────────────────────────────────────────

    @Test fun getByMonth_returns_expenses_in_the_given_month() = runTest {
        // T is in November 2023
        dao.insert(expense("nov", transactionAt = T))
        // Move to a different month (February 2024 ~ T + 90 days)
        dao.insert(expense("feb", transactionAt = T + 90L * 24 * 3600 * 1000))

        val nov = dao.getByMonth(MONTH).first()
        assertThat(nov).hasSize(1)
        assertThat(nov[0].id).isEqualTo("nov")
    }

    @Test fun getByMonth_no_expenses_returns_empty_list() = runTest {
        assertThat(dao.getByMonth(MONTH).first()).isEmpty()
    }

    // ── AGGREGATES ───────────────────────────────────────────────────────────

    @Test fun getTotalByMonth_sums_only_logged_expenses_for_month() = runTest {
        dao.insert(expense("logged-1",   amount = 500.0, isLogged = true,  transactionAt = T))
        dao.insert(expense("logged-2",   amount = 300.0, isLogged = true,  transactionAt = T1))
        dao.insert(expense("unlogged-1", amount = 999.0, isLogged = false, transactionAt = T2))

        val total = dao.getTotalByMonth(MONTH).first()
        assertThat(total).isEqualTo(800.0)
    }

    @Test fun getTotalByMonth_returns_zero_for_empty_month() = runTest {
        assertThat(dao.getTotalByMonth(MONTH).first()).isEqualTo(0.0)
    }

    @Test fun getTotalByCategoryForMonth_groups_correctly() = runTest {
        dao.insert(expense("f1", amount = 200.0, category = "Food", isLogged = true, transactionAt = T))
        dao.insert(expense("f2", amount = 150.0, category = "Food", isLogged = true, transactionAt = T1))
        dao.insert(expense("t1", amount = 100.0, category = "Transport", isLogged = true, transactionAt = T2))

        val totals = dao.getTotalByCategoryForMonth(MONTH).first()
        val map = totals.associate { it.category to it.total }

        assertThat(map["Food"]).isEqualTo(350.0)
        assertThat(map["Transport"]).isEqualTo(100.0)
    }

    @Test fun getTotalByMonthOneShot_matches_reactive_query() = runTest {
        dao.insert(expense("a", amount = 400.0, isLogged = true, transactionAt = T))
        dao.insert(expense("b", amount = 600.0, isLogged = true, transactionAt = T1))

        val oneShot  = dao.getTotalByMonthOneShot(MONTH)
        val reactive = dao.getTotalByMonth(MONTH).first()

        assertThat(oneShot).isEqualTo(reactive)
        assertThat(oneShot).isEqualTo(1000.0)
    }

    // ── SYNC ─────────────────────────────────────────────────────────────────

    @Test fun getUnsynced_returns_only_unsynced_records() = runTest {
        dao.insert(expense("synced",   isSynced = true))
        dao.insert(expense("unsynced", isSynced = false))

        val unsynced = dao.getUnsynced()
        assertThat(unsynced).hasSize(1)
        assertThat(unsynced[0].id).isEqualTo("unsynced")
    }

    @Test fun getUnsyncedCount_returns_correct_count() = runTest {
        dao.insert(expense("u1", isSynced = false))
        dao.insert(expense("u2", isSynced = false))
        dao.insert(expense("s1", isSynced = true))

        assertThat(dao.getUnsyncedCount()).isEqualTo(2)
    }

    // ── WIDGET ───────────────────────────────────────────────────────────────

    @Test fun getRecentLogged_returns_only_logged_sorted_newest_first() = runTest {
        dao.insert(expense("logged-old", isLogged = true,  transactionAt = T))
        dao.insert(expense("logged-new", isLogged = true,  transactionAt = T3))
        dao.insert(expense("unlogged",   isLogged = false, transactionAt = T2))

        val recent = dao.getRecentLogged(limit = 5)
        assertThat(recent.map { it.id }).containsExactly("logged-new", "logged-old").inOrder()
    }

    @Test fun getRecentLogged_limit_is_respected() = runTest {
        repeat(10) { i ->
            dao.insert(expense("logged-$i", isLogged = true, transactionAt = T + i * 1000L))
        }

        val limited = dao.getRecentLogged(limit = 3)
        assertThat(limited).hasSize(3)
    }

    // ── DUPLICATE DETECTION ──────────────────────────────────────────────────

    @Test fun findPotentialDuplicates_matches_by_merchant_amount_and_time() = runTest {
        dao.insert(expense("dup-1", amount = 450.0, merchant = "Swiggy", transactionAt = T))

        val results = dao.findPotentialDuplicates(
            merchant   = "swiggy",         // case-insensitive
            amountMin  = 449.0,
            amountMax  = 451.0,
            startTime  = T - 30_000,
            endTime    = T + 30_000
        )

        assertThat(results).hasSize(1)
        assertThat(results[0].id).isEqualTo("dup-1")
    }

    @Test fun findPotentialDuplicates_no_match_outside_time_window() = runTest {
        dao.insert(expense("old-1", amount = 450.0, merchant = "Swiggy", transactionAt = T))

        // Query for a window 1 hour later — no overlap
        val results = dao.findPotentialDuplicates(
            merchant  = "Swiggy",
            amountMin = 449.0,
            amountMax = 451.0,
            startTime = T + 3_600_000L,
            endTime   = T + 3_700_000L
        )

        assertThat(results).isEmpty()
    }

    @Test fun findPotentialDuplicates_no_match_outside_amount_range() = runTest {
        dao.insert(expense("amt-1", amount = 500.0, merchant = "Ola", transactionAt = T))

        val results = dao.findPotentialDuplicates(
            merchant  = "Ola",
            amountMin = 100.0,
            amountMax = 200.0,
            startTime = T - 1000,
            endTime   = T + 1000
        )

        assertThat(results).isEmpty()
    }

    @Test fun findPotentialDuplicates_returns_max_5_results() = runTest {
        repeat(7) { i ->
            dao.insert(expense("dup-$i", amount = 100.0, merchant = "Amazon", transactionAt = T + i * 100L))
        }

        val results = dao.findPotentialDuplicates(
            merchant  = "Amazon",
            amountMin = 99.0,
            amountMax = 101.0,
            startTime = T - 1000,
            endTime   = T + 10_000
        )

        assertThat(results.size).isAtMost(5)
    }

    // ── BULK IMPORT ──────────────────────────────────────────────────────────

    @Test fun existsByAmountAndTime_returns_true_when_match_exists() = runTest {
        dao.insert(expense("existing", amount = 450.0, transactionAt = T))

        val exists = dao.existsByAmountAndTime(
            amount  = 450.0,
            timeMin = T - 30_000,
            timeMax = T + 30_000
        )

        assertThat(exists).isTrue()
    }

    @Test fun existsByAmountAndTime_returns_false_when_no_match() = runTest {
        dao.insert(expense("other", amount = 450.0, transactionAt = T))

        // Different amount
        val exists = dao.existsByAmountAndTime(
            amount  = 999.0,
            timeMin = T - 30_000,
            timeMax = T + 30_000
        )

        assertThat(exists).isFalse()
    }

    @Test fun existsByAmountAndTime_returns_false_when_outside_time_range() = runTest {
        dao.insert(expense("far-away", amount = 200.0, transactionAt = T))

        val exists = dao.existsByAmountAndTime(
            amount  = 200.0,
            timeMin = T + 60_000,  // window starts after the record
            timeMax = T + 120_000
        )

        assertThat(exists).isFalse()
    }

    @Test fun existsByAmountAndTime_returns_false_on_empty_db() = runTest {
        assertThat(dao.existsByAmountAndTime(100.0, T - 1000, T + 1000)).isFalse()
    }

    // ── DAILY TOTALS ─────────────────────────────────────────────────────────

    @Test fun getDailyTotalsForMonth_returns_day_totals() = runTest {
        // Nov 15 2023 = T (approximate, UTC)
        dao.insert(expense("d1", amount = 200.0, isLogged = true, transactionAt = T))
        dao.insert(expense("d2", amount = 100.0, isLogged = true, transactionAt = T1))

        val dailyTotals = dao.getDailyTotalsForMonth(MONTH)

        assertThat(dailyTotals).isNotEmpty()
        // All totals should be positive
        dailyTotals.forEach { dt -> assertThat(dt.total).isGreaterThan(0.0) }
        // Sum of daily totals == total for month
        val daySum = dailyTotals.sumOf { it.total }
        assertThat(daySum).isEqualTo(300.0)
    }

    @Test fun getDailyTotalsForMonth_omits_unlogged_expenses() = runTest {
        dao.insert(expense("logged",   amount = 500.0, isLogged = true,  transactionAt = T))
        dao.insert(expense("unlogged", amount = 999.0, isLogged = false, transactionAt = T1))

        val daily = dao.getDailyTotalsForMonth(MONTH)
        val total = daily.sumOf { it.total }

        assertThat(total).isEqualTo(500.0)
    }

    @Test fun getDailyTotalsForMonth_empty_month_returns_empty_list() = runTest {
        assertThat(dao.getDailyTotalsForMonth(MONTH)).isEmpty()
    }

    // ── REACTIVE UPDATES ─────────────────────────────────────────────────────

    @Test fun getAll_reacts_to_new_insert() = runTest {
        val flow = dao.getAll()

        // Initial emission — empty
        assertThat(flow.first()).isEmpty()

        dao.insert(expense("reactive-1"))

        // After insert — should contain new record
        assertThat(flow.first()).hasSize(1)
    }

    @Test fun getUnlogged_reacts_to_markLogged() = runTest {
        dao.insert(expense("watch-1", isLogged = false))
        assertThat(dao.getUnlogged().first()).hasSize(1)

        dao.markLogged("watch-1", System.currentTimeMillis())

        assertThat(dao.getUnlogged().first()).isEmpty()
    }
}

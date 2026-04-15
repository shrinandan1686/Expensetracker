package com.trackit.expense.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.trackit.expense.data.local.dao.BudgetDao
import com.trackit.expense.data.local.dao.ExpenseDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that supplies data to [TrackItWidget].
 *
 * Reads directly from Room (no network call) and triggers a Glance redraw
 * via [TrackItWidget.updateAll] after the data is refreshed.
 *
 * ## Why Room-direct instead of ExpenseRepository?
 * [ExpenseRepository] returns [Flow], but Glance widgets are updated imperatively —
 * they request a one-shot snapshot and then re-render.  Calling [firstOrNull] on
 * a Flow works, but the DAOs expose one-shot suspend functions that are cheaper and
 * more explicit for this use-case.
 */
@Singleton
class WidgetDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao
) {

    private val monthKeyFmt   = SimpleDateFormat("yyyy-MM", Locale.US)
    private val monthLabelFmt = SimpleDateFormat("MMMM yyyy", Locale.US)

    // ──────────────────────────────────────────────────────────────────────────

    /** Returns a fresh [WidgetData] snapshot for the current month. */
    suspend fun getWidgetData(): WidgetData {
        val cal        = Calendar.getInstance()
        val monthKey   = monthKeyFmt.format(cal.time)
        val monthLabel = monthLabelFmt.format(cal.time)

        val totalSpent   = expenseDao.getTotalByMonthOneShot(monthKey)
        val budget       = budgetDao.getByMonthOneShot(monthKey)?.overall ?: 0.0
        val recentEntity = expenseDao.getRecentLogged(3)
        val recent       = recentEntity.map { e ->
            RecentExpenseItem(id = e.id, merchant = e.merchant, amount = e.amount)
        }

        return WidgetData(
            monthLabel      = monthLabel,
            monthKey        = monthKey,
            totalSpent      = totalSpent,
            budget          = budget,
            recentExpenses  = recent
        )
    }

    /**
     * Fetches fresh data and triggers a Glance redraw for every active
     * [TrackItWidget] instance on the home screen.
     */
    suspend fun refreshWidget() {
        TrackItWidget().updateAll(context)
    }
}

package com.trackit.expense.widget

/**
 * Snapshot of the data the home screen widget needs to render.
 *
 * Fetched once per update cycle — no reactive Flow needed since widgets
 * are imperative (update-and-redraw), not reactive.
 */
data class WidgetData(
    /** Display label, e.g. "April 2026". */
    val monthLabel: String,
    /** "YYYY-MM" key used for DB queries. */
    val monthKey: String,
    /** Total logged spend for the current month (INR). */
    val totalSpent: Double,
    /** Overall budget cap for the current month (0 = unset). */
    val budget: Double,
    /** Last ≤ 3 logged expenses, newest first. */
    val recentExpenses: List<RecentExpenseItem>
) {
    /** 0.0–1.0 fraction; safe even when budget is 0. */
    val budgetProgress: Float
        get() = if (budget > 0) (totalSpent / budget).toFloat().coerceIn(0f, 1f) else 0f
}

data class RecentExpenseItem(
    val id: String,
    val merchant: String,
    val amount: Double
)

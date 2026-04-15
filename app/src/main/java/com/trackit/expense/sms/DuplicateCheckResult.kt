package com.trackit.expense.sms

import com.trackit.expense.data.local.entity.ExpenseEntity

/**
 * Result of a duplicate-expense check performed before showing the SMS notification.
 *
 * - [NoDuplicate]        → no matching expense in DB; proceed normally.
 * - [PossibleDuplicate]  → an expense with a similar amount (±₹[AMOUNT_TOLERANCE_INR])
 *                          and the same merchant was saved within [TIME_WINDOW_MS].
 *                          Show a "Possible duplicate" warning in AddExpenseScreen.
 */
sealed class DuplicateCheckResult {
    object NoDuplicate : DuplicateCheckResult()
    data class PossibleDuplicate(val existingExpense: ExpenseEntity) : DuplicateCheckResult()
}

/** Amount tolerance for duplicate matching: ±₹5. */
const val AMOUNT_TOLERANCE_INR = 5.0

/** Time window for duplicate matching: ±60 seconds. */
const val TIME_WINDOW_MS = 60_000L

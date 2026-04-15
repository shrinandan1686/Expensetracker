package com.trackit.expense.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences store for an in-progress, not-yet-confirmed expense.
 *
 * ## Crash-recovery flow
 * 1. When the SMS overlay (or any entry UI) starts filling in an expense, it calls
 *    [savePending] with the current partial data.
 * 2. If the process crashes mid-entry, the data survives in SharedPreferences.
 * 3. On the next [MainActivity.onCreate], [hasPendingExpense] is checked.
 * 4. If true, a "We saved your incomplete expense" recovery card is shown.
 * 5. Tapping the card navigates to AddExpenseScreen pre-filled with [loadPending].
 * 6. The store is cleared on save, discard, or explicit dismissal.
 */
@Singleton
class PendingExpenseStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class PendingExpense(
        val amount: Double,
        val merchant: String,
        val account: String,
        val notes: String,
        val rawSms: String,
        val timestamp: Long
    )

    /** True if there is unsaved expense data from a prior session. */
    val hasPendingExpense: Boolean get() = prefs.contains(KEY_PENDING_JSON)

    /**
     * Persist partial expense data before the user has confirmed it.
     * Call this as soon as the overlay/entry UI populates the fields.
     */
    fun savePending(expense: PendingExpense) {
        val json = JSONObject().apply {
            put("amount",    expense.amount)
            put("merchant",  expense.merchant)
            put("account",   expense.account)
            put("notes",     expense.notes)
            put("rawSms",    expense.rawSms)
            put("timestamp", expense.timestamp)
        }.toString()
        prefs.edit().putString(KEY_PENDING_JSON, json).apply()
        Log.d(TAG, "Saved pending expense: ₹${expense.amount} at ${expense.merchant}")
    }

    /**
     * Restore the pending expense from storage.
     * Returns null and clears the store if parsing fails.
     */
    fun loadPending(): PendingExpense? {
        val json = prefs.getString(KEY_PENDING_JSON, null) ?: return null
        return try {
            val obj = JSONObject(json)
            PendingExpense(
                amount    = obj.getDouble("amount"),
                merchant  = obj.getString("merchant"),
                account   = obj.optString("account", ""),
                notes     = obj.optString("notes", ""),
                rawSms    = obj.optString("rawSms", ""),
                timestamp = obj.getLong("timestamp")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse pending expense JSON — clearing store", e)
            clearPending()
            null
        }
    }

    /** Remove the pending expense (call after save, discard, or user dismissal). */
    fun clearPending() {
        prefs.edit().remove(KEY_PENDING_JSON).apply()
    }

    companion object {
        private const val TAG             = "PendingExpenseStore"
        private const val PREFS_NAME      = "trackit_pending_expense"
        private const val KEY_PENDING_JSON = "pending_expense_json"
    }
}

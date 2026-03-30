package com.trackit.expense.domain.model

import java.util.UUID

/**
 * Domain model for a single expense transaction.
 *
 * ## Amount
 * Stored as **Double (INR)** — no paise conversion at domain or data layers.
 * Conversion to display currency (e.g., formatting ₹1,200.50) is a UI concern only.
 *
 * ## ID
 * String UUID generated at creation time. Because IDs are client-generated before
 * the record reaches the server, a UUID avoids collisions across devices without a
 * round-trip to a server sequence.
 *
 * ## Category
 * Deliberately **denormalised** as a plain String (e.g., "Food & Drinks"). This
 * eliminates FK joins for every list query and keeps the offline-first sync simple.
 *
 * @property id            Client-generated UUID.
 * @property amount        Transaction amount in INR (Double).
 * @property merchant      Merchant name or UPI VPA, editable by the user in the overlay.
 * @property category      Denormalised category label from [ExpenseCategory].
 * @property account       Account/card identifier (e.g., "XX1234"), from SMS.
 * @property notes         Optional user-added note.
 * @property rawSms        The original SMS body — stored for audit and re-parsing.
 * @property isLogged      True once the user has reviewed and saved via the overlay popup.
 * @property isSynced      True once successfully POSTed to the remote API.
 * @property transactionAt Unix epoch millis of the payment (from SMS timestamp or user input).
 * @property loggedAt      Epoch millis when the user confirmed the expense in the overlay.
 * @property createdAt     Epoch millis when the record was first created locally.
 */
data class Expense(
    val id: String            = UUID.randomUUID().toString(),
    val amount: Double        = 0.0,
    val merchant: String      = "",
    val category: String      = "Others",
    val account: String       = "",
    val notes: String?        = null,
    val rawSms: String        = "",
    val isLogged: Boolean     = false,
    val isSynced: Boolean     = false,
    val transactionAt: Long   = System.currentTimeMillis(),
    val loggedAt: Long?       = null,
    val createdAt: Long       = System.currentTimeMillis()
) {
    /** Formatted amount string with ₹ symbol, rounded to 2 decimals. */
    val amountDisplay: String get() = "₹%.2f".format(amount)

    /** True if this is a UPI transaction (VPA-style merchant or UPI category). */
    val isUpi: Boolean get() = merchant.contains('@') || category.contains("UPI", ignoreCase = true)
}

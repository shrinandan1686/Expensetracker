package com.trackit.expense.sms

/**
 * Holds all data extracted from a single bank/UPI SMS.
 *
 * @property amount          Transaction amount in **INR** (Double, NOT paise — display-ready).
 * @property merchant        Merchant name or UPI VPA extracted from the message; null if undetectable.
 * @property accountLast4    Last 4 digits of the account/card mentioned in the SMS; null if absent.
 * @property paymentMode     Payment channel: "UPI", "Credit Card", "PhonePe", etc.; "Unknown" if absent.
 * @property transactionType Debit keyword found: "debited", "paid", "spent", "purchased", or "unknown".
 * @property rawSms          The original unmodified SMS body for audit / debugging.
 * @property timestamp       Unix epoch millis — either the device-received time or parsed from SMS.
 */
data class ParsedTransaction(
    val amount: Double,
    val merchant: String?,
    val accountLast4: String?,
    val paymentMode: String,
    val transactionType: String,
    val rawSms: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Amount converted to paise (Long) for Room storage.
     * Rounded to avoid floating-point drift.
     */
    val amountInPaise: Long get() = (amount * 100).toLong()

    /** True if this could be a UPI-based transaction. */
    val isUpi: Boolean get() =
        paymentMode.contains("UPI", ignoreCase = true) ||
        paymentMode.contains("PhonePe", ignoreCase = true) ||
        paymentMode.contains("GPay", ignoreCase = true) ||
        paymentMode.contains("Paytm", ignoreCase = true) ||
        merchant?.contains("@") == true   // VPA always has @
}

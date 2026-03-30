package com.trackit.expense.sms

import java.util.regex.Pattern

/**
 * Stateless SMS parser that extracts payment information from Indian bank/UPI messages.
 *
 * ## Design
 * - All [Pattern]s are compiled once as constants (thread-safe, no recompilation cost).
 * - Each `parseXxx()` function is public so it can be unit-tested independently.
 * - [parse] is the single entry point for callers; it returns null for non-debit messages.
 * - Amount is returned as Double (INR) — the caller stores it in paise via [ParsedTransaction.amountInPaise].
 *
 * ## Supported SMS formats
 * ```
 * "INR 450.00 debited from A/c XX1234 to UPI VPA swiggy@icici"
 * "Rs.1,200 spent on HDFC Credit Card at Amazon"
 * "₹89 paid to Zomato via PhonePe UPI"
 * "Your A/c XX5678 debited Rs 500 on 30-03-26"
 * "UPI txn: ₹200 to merchant@paytm from SBI"
 * ```
 */
object SmsParser {

    // ─────────────────────────────────────────────────────────────────────────────
    // AMOUNT PATTERNS
    //
    // Matches: ₹450  |  Rs.1,200  |  Rs 500  |  INR 450.00
    // Group 1 = the numeric part (may include commas)
    // ─────────────────────────────────────────────────────────────────────────────

    private val AMOUNT_PATTERN: Pattern = Pattern.compile(
        """(?:₹|[Rr][Ss]\.?|INR)\s*([\d,]+(?:\.\d{1,2})?)""",
        Pattern.CASE_INSENSITIVE
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // MERCHANT PATTERNS  (applied in priority order: VPA → TO → AT)
    //
    // Pattern 1 – VPA (highest priority):
    //   "to UPI VPA swiggy@icici"  |  "VPA merchant@paytm"
    //   Group 1 = VPA handle (e.g. swiggy@icici)
    //
    // Pattern 2 – Transferred TO a named recipient:
    //   "paid to Zomato"  |  "to merchant@paytm"
    //   Excludes false positives: "to UPI", "to VPA", "to PhonePe", "to SBI"
    //   (bare bank names / known gateways are excluded via negative lookahead)
    //   Group 1 = merchant name / VPA (single token, may contain @ or .)
    //
    // Pattern 3 – Purchase AT a merchant:
    //   "spent on HDFC Credit Card at Amazon"  |  "purchase at BigBasket"
    //   Lazy quantifier stops before common trailing phrases.
    //   Group 1 = merchant name (1–3 words)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Matches "to [UPI ]VPA <handle>" or standalone "VPA <handle>". */
    private val MERCHANT_VPA_PATTERN: Pattern = Pattern.compile(
        """(?:to\s+(?:UPI\s+)?VPA\s+|(?<!\w)VPA\s+)(\S+)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Matches "to <merchant>" where the merchant is a single word/VPA handle.
     *
     * Negative lookahead prevents matching:
     *   - "to UPI"   – payment gateway keyword
     *   - "to VPA"   – already handled by MERCHANT_VPA_PATTERN
     *   - "to SBI", "to HDFC", "to your" – bank/filler words
     */
    private val MERCHANT_TO_PATTERN: Pattern = Pattern.compile(
        """(?<!\w)to\s+(?!(?:UPI|VPA|PhonePe|Google|GPay|Paytm|SBI|HDFC|ICICI|AXIS|KOTAK|YES|PNB|BOI|your|the)\b)([A-Za-z][A-Za-z0-9]*(?:[@._-][A-Za-z0-9]+)*)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Matches "at <merchant>" (1–3 words, lazy).
     * Stops before: "on" / "for" / "via" / "using" / amount tokens / end of string.
     */
    private val MERCHANT_AT_PATTERN: Pattern = Pattern.compile(
        """(?<!\w)at\s+([A-Za-z][A-Za-z0-9&'._-]*(?:\s+[A-Za-z0-9&'._-]+){0,2}?)(?=\s+(?:on|for|via|using|Rs|INR|₹|\d)|[.,\n]|$)""",
        Pattern.CASE_INSENSITIVE
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // ACCOUNT PATTERNS
    //
    // Matches: "A/c XX1234"  |  "a/c xx5678"  |  "Card XX9012"
    // Group 1 = last 4 digits only
    // ─────────────────────────────────────────────────────────────────────────────

    private val ACCOUNT_PATTERN: Pattern = Pattern.compile(
        """(?:[Aa]/[Cc]|[Cc]ard)\s+[Xx]{1,4}(\d{4})"""
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // TRANSACTION TYPE
    //
    // Matches the debit verb present in the message.
    // Group 1 = matched keyword (lowercased by caller)
    // ─────────────────────────────────────────────────────────────────────────────

    private val TXN_TYPE_PATTERN: Pattern = Pattern.compile(
        """\b(debited|paid|spent|purchased?)\b""",
        Pattern.CASE_INSENSITIVE
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // PAYMENT MODE
    //
    // Ordered from most-specific to least-specific so "PhonePe" is preferred over "UPI"
    // if both appear.
    // ─────────────────────────────────────────────────────────────────────────────

    private val PAYMENT_MODE_PATTERN: Pattern = Pattern.compile(
        """\b(PhonePe|Google\s*Pay|GPay|Paytm|NEFT|RTGS|IMPS|Credit\s*Card|Debit\s*Card|UPI)\b""",
        Pattern.CASE_INSENSITIVE
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Parse a raw bank/UPI SMS into a [ParsedTransaction].
     *
     * Returns **null** if:
     * - No recognisable amount is found, OR
     * - The message shows no signs of being a debit transaction.
     *
     * @param sms        Raw SMS body.
     * @param timestamp  Time the SMS was received (system millis).
     */
    fun parse(sms: String, timestamp: Long = System.currentTimeMillis()): ParsedTransaction? {
        // Must contain a parseable amount
        val amount = parseAmount(sms) ?: return null

        // Must look like a debit (has debit keyword OR UPI/txn indicator)
        if (!looksLikeDebitSms(sms)) return null

        val merchant        = parseMerchant(sms)
        val accountLast4    = parseAccountLast4(sms)
        val paymentMode     = parsePaymentMode(sms) ?: inferPaymentMode(sms)
        val transactionType = parseTransactionType(sms)

        return ParsedTransaction(
            amount          = amount,
            merchant        = merchant,
            accountLast4    = accountLast4,
            paymentMode     = paymentMode,
            transactionType = transactionType,
            rawSms          = sms,
            timestamp       = timestamp
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INDIVIDUAL PARSERS  (public for unit testing)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Extract transaction amount in INR (Double).
     *
     * Strips commas before parsing so "1,200" → 1200.0.
     * Returns null if no amount pattern matches.
     */
    fun parseAmount(sms: String): Double? {
        val m = AMOUNT_PATTERN.matcher(sms)
        if (!m.find()) return null
        val raw = m.group(1) ?: return null
        return raw.replace(",", "").toDoubleOrNull()
    }

    /**
     * Extract merchant name or UPI VPA from the SMS.
     *
     * Priority order:
     * 1. Explicit VPA handle (most reliable — e.g. "swiggy@icici")
     * 2. "to <merchant>" recipient
     * 3. "at <merchant>" location
     *
     * Returns null if no merchant pattern matches.
     */
    fun parseMerchant(sms: String): String? {
        // 1. VPA pattern (highest confidence)
        val vpaM = MERCHANT_VPA_PATTERN.matcher(sms)
        if (vpaM.find()) return vpaM.group(1)?.trim()

        // 2. "to <merchant>"
        val toM = MERCHANT_TO_PATTERN.matcher(sms)
        if (toM.find()) return toM.group(1)?.trim()

        // 3. "at <merchant>"
        val atM = MERCHANT_AT_PATTERN.matcher(sms)
        if (atM.find()) return atM.group(1)?.trim()

        return null
    }

    /**
     * Extract the last 4 digits of the account or card number.
     *
     * Matches: "A/c XX1234", "a/c xx5678", "Card XX9012".
     * Returns null if not present.
     */
    fun parseAccountLast4(sms: String): String? {
        val m = ACCOUNT_PATTERN.matcher(sms)
        return if (m.find()) m.group(1) else null
    }

    /**
     * Extract payment mode.
     *
     * Returns the first match from [PAYMENT_MODE_PATTERN] (multi-word modes like
     * "Credit Card" have internal spaces normalised), or null if nothing matches.
     */
    fun parsePaymentMode(sms: String): String? {
        val m = PAYMENT_MODE_PATTERN.matcher(sms)
        return if (m.find()) m.group(1)?.replace(Regex("\\s+"), " ") else null
    }

    /**
     * Extract transaction type keyword.
     *
     * Returns one of: "debited", "paid", "spent", "purchased", or "unknown".
     */
    fun parseTransactionType(sms: String): String {
        val m = TXN_TYPE_PATTERN.matcher(sms)
        return if (m.find()) (m.group(1) ?: "unknown").lowercase() else "unknown"
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if the SMS looks like a debit notification via:
     * - A debit keyword (debited / paid / spent / purchase)
     * - OR a UPI-specific keyword (UPI / VPA / txn) alongside an amount
     *
     * This intentionally accepts "UPI txn: ₹200 to merchant@paytm from SBI"
     * which lacks an explicit debit keyword but is clearly a UPI debit.
     */
    private fun looksLikeDebitSms(sms: String): Boolean {
        if (TXN_TYPE_PATTERN.matcher(sms).find()) return true
        val upper = sms.uppercase()
        return upper.contains("UPI") || upper.contains("VPA") || upper.contains("TXN")
    }

    /**
     * Infer payment mode when no explicit keyword is found.
     * Falls back to "UPI" for VPA-style SMSs, "Unknown" otherwise.
     */
    private fun inferPaymentMode(sms: String): String {
        val upper = sms.uppercase()
        return when {
            upper.contains("UPI") || upper.contains("VPA") || sms.contains("@") -> "UPI"
            upper.contains("NEFT") -> "NEFT"
            upper.contains("IMPS") -> "IMPS"
            else -> "Unknown"
        }
    }
}

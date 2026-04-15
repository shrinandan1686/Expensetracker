package com.trackit.expense.domain.usecase

import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.overlay.ExpenseCategory
import com.trackit.expense.sms.SmsParser
import com.trackit.expense.util.SmsInboxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads historical bank/UPI SMS messages from the device inbox, parses them,
 * deduplicates against the local Room database, and saves new ones as
 * unlogged expenses (awaiting user review).
 *
 * ## Deduplication
 * An SMS is considered a duplicate if an existing expense has the **same amount**
 * and a `transaction_at` within ±30 seconds of the SMS timestamp.  The 30-second
 * window is generous because the SMS timestamp and any previously-saved expense
 * (auto-saved by [com.trackit.expense.sms.SmsReceiver]) should be identical, but
 * avoids fragility due to slight clock differences.
 *
 * ## Thread safety
 * The entire import runs on [Dispatchers.IO].  The [onProgress] callback is
 * invoked on IO as well — callers that update UI state (e.g. MutableStateFlow)
 * are safe because StateFlow mutations are thread-safe.
 */
class BulkSmsImportUseCase @Inject constructor(
    private val smsInboxReader: SmsInboxReader,
    private val expenseDao: ExpenseDao,
    private val addExpenseUseCase: AddExpenseUseCase
) {
    data class ImportResult(val imported: Int, val skipped: Int, val total: Int)

    suspend operator fun invoke(
        fromMillis: Long,
        onProgress: suspend (read: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val rawMessages = smsInboxReader.readSince(fromMillis)
            val total       = rawMessages.size
            var imported    = 0
            var skipped     = 0

            rawMessages.forEachIndexed { index, raw ->
                onProgress(index + 1, total)

                // ── 1. Parse ─────────────────────────────────────────────────
                val parsed = SmsParser.parse(raw.body, raw.timestamp) ?: run {
                    skipped++
                    return@forEachIndexed
                }

                // ── 2. Deduplication: same amount within ±30 s ───────────────
                val exists = expenseDao.existsByAmountAndTime(
                    amount  = parsed.amount,
                    timeMin = parsed.timestamp - 30_000L,
                    timeMax = parsed.timestamp + 30_000L
                )
                if (exists) {
                    skipped++
                    return@forEachIndexed
                }

                // ── 3. Save as unlogged ──────────────────────────────────────
                val expense = Expense(
                    amount        = parsed.amount,
                    merchant      = parsed.merchant ?: "",
                    category      = ExpenseCategory.inferFrom(parsed.merchant).displayName,
                    account       = parsed.accountLast4?.let { "XX$it" } ?: "",
                    notes         = parsed.paymentMode
                        .takeIf { it.isNotBlank() && it != "Unknown" }
                        ?.let { "via $it" },
                    rawSms        = raw.body,
                    isLogged      = false,
                    isSynced      = false,
                    transactionAt = parsed.timestamp
                )

                addExpenseUseCase(expense)
                    .onSuccess { imported++ }
                    .onFailure { skipped++ }
            }

            ImportResult(imported, skipped, total)
        }
    }
}

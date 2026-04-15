package com.trackit.expense.sms

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import com.trackit.expense.util.NotificationHelper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.work.WorkManager
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import com.trackit.expense.overlay.ExpenseCategory
import com.trackit.expense.worker.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.trackit.expense.util.LocationHelper
import com.trackit.expense.BuildConfig
import javax.inject.Inject

/**
 * BroadcastReceiver that intercepts incoming SMS messages and device boot events.
 *
 * ## Responsibilities
 * 1. **SMS_RECEIVED** – Filters messages from known bank/UPI sender IDs,
 *    delegates to [SmsParser], performs a **duplicate check** against Room DB,
 *    then silently auto-saves and posts a notification.
 *    - If a potential duplicate is detected (same merchant, ±₹[AMOUNT_TOLERANCE_INR],
 *      within [TIME_WINDOW_MS]), the notification deep link includes `duplicateId`
 *      and `isDuplicate=true` so AddExpenseScreen can show a warning.
 * 2. **BOOT_COMPLETED** – Re-enqueues [SyncWorker] and re-enables OEM-killed receiver
 *    components on known aggressive OEMs (Xiaomi, Samsung, OnePlus, etc.).
 *
 * ## Registration
 * Declared statically in `AndroidManifest.xml` so the receiver survives app death.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var addExpenseUseCase: AddExpenseUseCase
    @Inject lateinit var locationHelper: LocationHelper
    @Inject lateinit var expenseDao: ExpenseDao

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSmsReceived(context, intent)
            Intent.ACTION_BOOT_COMPLETED              -> handleBootCompleted(context)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SMS RECEIVED
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleSmsReceived(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val grouped  = messages.groupBy { it.originatingAddress }

        for ((sender, parts) in grouped) {
            val body      = parts.joinToString(separator = "") { it.messageBody }
            val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            Log.v(TAG, "Incoming SMS from [$sender]: $body")

            if (!isKnownBankSender(sender)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "DEBUG: Allowing unknown sender [$sender] for testing.")
                } else {
                    Log.d(TAG, "Skipping unknown sender [$sender].")
                    continue
                }
            }

            val pendingResult = goAsync()
            receiverScope.launch {
                try {
                    processSmS(context, sender!!, body, timestamp)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SMS from $sender", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun processSmS(context: Context, sender: String, body: String, timestamp: Long) {
        receiverScope.launch {
            val location = runCatching { locationHelper.getCurrentLocation() }.getOrNull()

            val parsed = SmsParser.parse(body, timestamp) ?: run {
                Log.w(TAG, "REJECTED: SMS from $sender did not match any debit pattern.")
                return@launch
            }
            Log.i(TAG, "Detected: ₹${parsed.amount} at ${parsed.merchant}")

            // ── 1. Duplicate check ────────────────────────────────────────────
            val duplicateResult = checkForDuplicate(parsed)
            if (duplicateResult is DuplicateCheckResult.PossibleDuplicate) {
                Log.i(TAG, "Possible duplicate detected: existing id=${duplicateResult.existingExpense.id}")
            }

            // ── 2. Silent backup save (isLogged = false) ──────────────────────
            val expense = Expense(
                amount          = parsed.amount,
                merchant        = parsed.merchant ?: "",
                category        = ExpenseCategory.inferFrom(parsed.merchant).displayName,
                account         = parsed.accountLast4?.let { "XX$it" } ?: "",
                notes           = parsed.paymentMode
                                    .takeIf { it.isNotBlank() && it != "Unknown" }
                                    ?.let { "via $it" },
                rawSms          = body,
                isLogged        = false,
                isSynced        = false,
                transactionAt   = parsed.timestamp,
                latitude        = location?.latitude,
                longitude       = location?.longitude,
                locationAddress = location?.address
            )

            addExpenseUseCase(expense)
                .onSuccess { id ->
                    Log.i(TAG, "Auto-saved: id=$id ₹${parsed.amount} ${parsed.merchant}")
                    // ── 3. Notify with duplicate context ──────────────────────
                    postNotification(context, parsed, expense.id, duplicateResult)
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to auto-save expense", e)
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DUPLICATE CHECK
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun checkForDuplicate(parsed: ParsedTransaction): DuplicateCheckResult {
        val merchant = parsed.merchant ?: return DuplicateCheckResult.NoDuplicate
        if (merchant.isBlank()) return DuplicateCheckResult.NoDuplicate

        val candidates = expenseDao.findPotentialDuplicates(
            merchant   = merchant,
            amountMin  = parsed.amount - AMOUNT_TOLERANCE_INR,
            amountMax  = parsed.amount + AMOUNT_TOLERANCE_INR,
            startTime  = parsed.timestamp - TIME_WINDOW_MS,
            endTime    = parsed.timestamp + TIME_WINDOW_MS
        )
        val existing = candidates.firstOrNull()
            ?: return DuplicateCheckResult.NoDuplicate

        return DuplicateCheckResult.PossibleDuplicate(existing)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFICATION
    // ─────────────────────────────────────────────────────────────────────────

    private fun postNotification(
        context: Context,
        parsed: ParsedTransaction,
        expenseId: String,
        duplicateResult: DuplicateCheckResult
    ) {
        val encodedMerch = URLEncoder.encode(
            parsed.merchant ?: "", StandardCharsets.UTF_8.toString()
        )

        val deepLink = buildString {
            append("trackit://add_expense")
            append("?amount=${parsed.amount}")
            append("&merchant=$encodedMerch")
            append("&account=${parsed.accountLast4 ?: ""}")
            append("&expenseId=$expenseId")
            if (duplicateResult is DuplicateCheckResult.PossibleDuplicate) {
                append("&isDuplicate=true")
                append("&duplicateId=${duplicateResult.existingExpense.id}")
            }
        }

        val title = if (duplicateResult is DuplicateCheckResult.PossibleDuplicate) {
            "⚠ Possible duplicate: ₹${parsed.amount}"
        } else {
            "Payment Detected: ₹${parsed.amount}"
        }

        NotificationHelper.showNotification(
            context        = context,
            channelId      = NotificationHelper.CHANNEL_EXPENSE_ENTRY,
            notificationId = System.currentTimeMillis().toInt(),
            title          = title,
            message        = "Tap to log at ${parsed.merchant ?: "Unknown Merchant"}",
            deepLink       = deepLink
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BOOT COMPLETED
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Boot completed — re-enqueueing SyncWorker.")
        SyncWorker.enqueue(WorkManager.getInstance(context))

        // Re-enable our own component in case an OEM battery manager disabled it.
        ensureReceiverEnabled(context)
    }

    /**
     * Re-enables the SmsReceiver component if an OEM has disabled it.
     *
     * Xiaomi MIUI and Samsung OneUI can set a component to DISABLED_USER (3) or
     * DISABLED (2) state via their battery optimisation managers. Re-enabling it
     * here on boot restores SMS detection without requiring user action.
     */
    private fun ensureReceiverEnabled(context: Context) {
        val comp  = ComponentName(context, SmsReceiver::class.java)
        val state = context.packageManager.getComponentEnabledSetting(comp)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        ) {
            Log.w(TAG, "SmsReceiver was disabled by OEM — re-enabling.")
            context.packageManager.setComponentEnabledSetting(
                comp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun isKnownBankSender(sender: String?): Boolean {
        sender ?: return false
        val normalized = sender.uppercase()
            .replace(Regex("^[A-Z]{2}-"), "")
            .trim()
        val isKnown = normalized in KNOWN_SENDERS
        if (!isKnown) Log.i(TAG, "Unknown sender: $sender (normalized: $normalized)")
        return isKnown
    }

    companion object {
        private const val TAG = "SmsReceiver"

        val KNOWN_SENDERS: Set<String> = setOf(
            // Banks
            "HDFCBK", "HDFCSMS", "HDFCBNK", "HDFCBK-T",
            "ICICIB", "ICICIP", "ICICIBANK",
            "SBIINB", "SBISMS", "SBIUPI", "SBIPAY", "SBICRD",
            "AXISBK", "AXISBN", "AXISPAY",
            "KOTAKB", "KOTAKM",
            "YESBNK", "YESBCAS",
            "INDBNK", "IDFCBK", "IDFCFB",
            "PNBSMS", "PNBBNK",
            "BOIIND", "BOISMS",
            "CANBK", "CNRBK",
            "UNIONB", "UBISMS",
            "BARBKG", "BOBSMS",
            "CENBNK", "CBIIND",
            "IDBIBK", "IDBINS",
            "RBLBNK", "FEDERAL",
            "UCOBNK", "INDIAB",
            "MAHABK", "SYNDBK",
            // UPI Gateways / Apps
            "PHONEP", "PAYTMB", "PAYTM",
            "GPAY", "GOOGLE", "GSPAY",
            "AMAZON", "AMZNPAY",
            "NXGSPY", "BHIMUP",
            "PHNPAY", "CREDIT"
        )
    }
}

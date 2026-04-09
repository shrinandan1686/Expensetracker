package com.trackit.expense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.WorkManager
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import com.trackit.expense.overlay.ExpenseCategory
import com.trackit.expense.overlay.ExpenseOverlayService
import com.trackit.expense.overlay.OverlayPermissionHelper
import com.trackit.expense.worker.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver that intercepts incoming SMS messages and device boot events.
 *
 * ## Responsibilities
 * 1. **SMS_RECEIVED** – Filters messages from known bank/UPI sender IDs,
 *    delegates to [SmsParser], then either:
 *    - Starts [ExpenseOverlayService] if SYSTEM_ALERT_WINDOW is granted, OR
 *    - Falls back to silent auto-save (isLogged = false) if the permission is absent.
 * 2. **BOOT_COMPLETED** – Re-enqueues the [SyncWorker] periodic job so background
 *    sync survives device restarts (WorkManager jobs are cleared on reboot).
 *
 * ## Registration
 * Declared statically in `AndroidManifest.xml` so the receiver is invoked even when
 * the app is not in the foreground. `android:exported="true"` is required for system broadcasts.
 *
 * ## Hilt Integration
 * Annotated with [@AndroidEntryPoint] to enable field injection. Heavy work is moved to
 * coroutines via [goAsync] to survive the 10-second BroadcastReceiver budget.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var addExpenseUseCase: AddExpenseUseCase

    /**
     * Private coroutine scope tied to [SupervisorJob] so individual child failures
     * don't cancel sibling operations (e.g., if two SMSs arrive together).
     * **Not** tied to any lifecycle — this is intentional for a BroadcastReceiver.
     */
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSmsReceived(context, intent)
            Intent.ACTION_BOOT_COMPLETED              -> handleBootCompleted(context)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SMS RECEIVED
    // ─────────────────────────────────────────────────────────────────────────────

    private fun handleSmsReceived(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Group all parts of multi-part SMS by originating address
        val grouped = messages.groupBy { it.originatingAddress }

        for ((sender, parts) in grouped) {
            // Skip if sender is not a known bank/UPI ID
            if (!isKnownBankSender(sender)) continue

            // Concatenate multi-part messages into a single body
            val body = parts.joinToString(separator = "") { it.messageBody }
            val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            Log.d(TAG, "Bank SMS from [$sender]: $body")

            val pendingResult = goAsync()

            receiverScope.launch {
                try {
                    processSmS(context, body, timestamp)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SMS from $sender", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * Core dispatch: show overlay if permission exists, otherwise silently auto-save.
     *
     * The overlay flow gives the user a chance to review, categorise and add notes.
     * The fallback saves with [Expense.isLogged] = false so the user can find and
     * review it later in History → Skipped.
     */
    private fun processSmS(context: Context, body: String, timestamp: Long) {
        val parsed = SmsParser.parse(body, timestamp) ?: run {
            Log.d(TAG, "SMS did not match any payment pattern, skipping.")
            return
        }

        if (OverlayPermissionHelper.hasOverlayPermission(context)) {
            // ── Happy path: show the expense entry popup ──────────────────────
            Log.i(TAG, "Overlay permission granted — starting ExpenseOverlayService")
            val intent = ExpenseOverlayService.createIntent(context, parsed)
            context.startForegroundService(intent)
        } else {
            // ── Fallback: silently save as unreviewed ─────────────────────────
            Log.w(TAG, "No overlay permission — falling back to silent auto-save")
            receiverScope.launch {
                val expense = Expense(
                    amount        = parsed.amount,         // Double INR
                    merchant      = parsed.merchant ?: "",
                    category      = ExpenseCategory.inferFrom(parsed.merchant).displayName,
                    account       = parsed.accountLast4?.let { "XX$it" } ?: "",
                    notes         = parsed.paymentMode
                                        .takeIf { it.isNotBlank() && it != "Unknown" }
                                        ?.let { "via $it" },
                    rawSms        = body,
                    isLogged      = false,
                    isSynced      = false,
                    transactionAt = parsed.timestamp
                )
                addExpenseUseCase(expense)
                    .onSuccess { id ->
                        Log.i(TAG, "Silent auto-save: id=$id ₹${parsed.amount} ${parsed.merchant}")
                    }
                    .onFailure { e ->
                        Log.e(TAG, "Failed to auto-save expense", e)
                    }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BOOT COMPLETED
    // ─────────────────────────────────────────────────────────────────────────────

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Device boot detected — re-enqueueing SyncWorker.")
        SyncWorker.enqueue(WorkManager.getInstance(context))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if [sender] matches any known bank/wallet sender ID.
     *
     * Indian telecom sender IDs arrive as alphanumeric strings (e.g. "HDFCBK"),
     * sometimes prefixed with "AD-" (Airtel/Docomo) or "VK-" (Vi/Vodafone-Idea).
     * The check strips common carrier prefixes before comparing.
     *
     * @param sender Originating address from the SMS PDU; may be null (unknown sender).
     */
    private fun isKnownBankSender(sender: String?): Boolean {
        sender ?: return false
        // Strip carrier prefixes like "AD-", "VK-", "TM-" etc.
        val normalized = sender.uppercase()
            .replace(Regex("^[A-Z]{2}-"), "")
            .trim()
        return normalized in KNOWN_SENDERS
    }

    companion object {
        private const val TAG = "SmsReceiver"

        /**
         * Canonical set of known bank/wallet sender IDs (after carrier-prefix stripping).
         *
         * Sources:
         * - HDFC Bank  → HDFCBK
         * - ICICI Bank → ICICIB
         * - SBI        → SBIINB
         * - Axis Bank  → AXISBK
         * - Kotak      → KOTAKB
         * - Paytm      → PAYTMB
         * - Yes Bank   → YESBNK
         * - Indusind   → INDBNK
         * - PNB        → PNBSMS
         * - BOI        → BOIIND
         *
         * Extend this set as new banks/wallets are onboarded.
         */
        val KNOWN_SENDERS: Set<String> = setOf(
            "HDFCBK",
            "ICICIB",
            "SBIINB",
            "AXISBK",
            "KOTAKB",
            "PAYTMB",
            "YESBNK",
            "INDBNK",
            "PNBSMS",
            "BOIIND"
        )
    }
}

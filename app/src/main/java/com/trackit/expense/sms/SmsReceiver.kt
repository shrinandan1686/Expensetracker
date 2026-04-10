package com.trackit.expense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.trackit.expense.util.NotificationHelper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.work.WorkManager
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

    @Inject
    lateinit var locationHelper: LocationHelper

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
            // Concatenate multi-part messages into a single body
            val body = parts.joinToString(separator = "") { it.messageBody }
            val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            Log.v(TAG, "Incoming SMS from [$sender]: $body")

            // Skip if sender is not a known bank/UPI ID (Bypass allowed in DEBUG)
            if (!isKnownBankSender(sender)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "DEBUG: Allowing unknown sender [$sender] for testing purposes.")
                } else {
                    Log.d(TAG, "Skipping unknown sender [$sender]. Use a known bank ID (e.g., HDFCSMS) for testing.")
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

    /**
     * Core dispatch: show overlay if permission exists, otherwise silently auto-save.
     *
     * The overlay flow gives the user a chance to review, categorise and add notes.
     * The fallback saves with [Expense.isLogged] = false so the user can find and
     * review it later in History → Skipped.
     */
    private fun processSmS(context: Context, sender: String, body: String, timestamp: Long) {
        receiverScope.launch {
            val location = try {
                locationHelper.getCurrentLocation()
            } catch (e: Exception) {
                null
            }
            
            val parsed = SmsParser.parse(body, timestamp) ?: run {
                Log.w(TAG, "REJECTED: SMS content from $sender did not match any debt/payment patterns.")
                return@launch
            }

            Log.i(TAG, "Detection Result: Amt=${parsed.amount}, Merch=${parsed.merchant}")

            // 1. Silent save as a backup (unreviewed)
            val expense = Expense(
                amount        = parsed.amount,
                merchant      = parsed.merchant ?: "",
                category      = ExpenseCategory.inferFrom(parsed.merchant).displayName,
                account       = parsed.accountLast4?.let { "XX$it" } ?: "",
                notes         = parsed.paymentMode
                                    .takeIf { it.isNotBlank() && it != "Unknown" }
                                    ?.let { "via $it" },
                rawSms        = body,
                isLogged      = false,
                isSynced      = false,
                transactionAt = parsed.timestamp,
                latitude      = location?.latitude,
                longitude     = location?.longitude,
                locationAddress = location?.address
            )
            
            addExpenseUseCase(expense)
                .onSuccess { id ->
                    Log.i(TAG, "Backup auto-save: id=$id ₹${parsed.amount} ${parsed.merchant}")
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to auto-save expense backup", e)
                }
            
            // 2. Post notification
            postNotification(context, parsed)
        }
    }

    private fun postNotification(context: Context, parsed: ParsedTransaction) {
        val encodedMerch = URLEncoder.encode(parsed.merchant ?: "", StandardCharsets.UTF_8.toString())
        val deepLink = "trackit://add_expense?" +
            "amount=${parsed.amount}" +
            "&merchant=$encodedMerch" +
            "&account=${parsed.accountLast4 ?: ""}"

        NotificationHelper.showNotification(
            context = context,
            channelId = NotificationHelper.CHANNEL_EXPENSE_ENTRY,
            notificationId = System.currentTimeMillis().toInt(),
            title = "Payment Detected: ₹${parsed.amount}",
            message = "Tap to log expense at ${parsed.merchant ?: "Unknown Merchant"}",
            deepLink = deepLink
        )
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
        
        val isKnown = normalized in KNOWN_SENDERS
        if (!isKnown) {
            Log.i(TAG, "Unknown sender: $sender (normalized: $normalized)")
        }
        return isKnown
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

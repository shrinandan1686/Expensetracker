package com.trackit.expense.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.trackit.expense.MainActivity
import com.trackit.expense.R
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import com.trackit.expense.presentation.theme.TrackItTheme
import com.trackit.expense.sms.ParsedTransaction
import com.trackit.expense.worker.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that renders the UPI expense entry popup over all apps.
 *
 * ## Lifecycle
 * 1. [SmsReceiver] detects a bank SMS → calls [createIntent] → starts this service.
 * 2. Service calls `startForeground()` immediately (required on API 26+).
 * 3. A [ComposeView] is inflated and attached to [WindowManager] using
 *    [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY].
 * 4. The user fills the popup and taps Save / Skip.
 * 5. The expense is saved to Room via [AddExpenseUseCase].
 * 6. A [SyncWorker] sync is triggered, the overlay is removed, and the service stops.
 *
 * ## Hilt
 * Annotated with [@AndroidEntryPoint]. Uses a [SupervisorJob]-backed scope for
 * coroutines so that individual save failures don't cancel the scope.
 *
 * ## Back Button
 * A custom [BackInterceptorLayout] intercepts [KeyEvent.KEYCODE_BACK] at the view level
 * and updates [backPressCount] — a Compose State that the popup observes to show a
 * Snackbar without dismissing itself.
 */
@AndroidEntryPoint
class ExpenseOverlayService : Service() {

    @Inject
    lateinit var addExpenseUseCase: AddExpenseUseCase

    private lateinit var windowManager: WindowManager
    private var overlayRootView: BackInterceptorLayout? = null
    private val composeOwner = OverlayComposeOwner()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Compose-observable state shared between service and ComposeView
    private var backPressCount by mutableIntStateOf(0)
    private var saveSuccess   by mutableStateOf(false)

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        composeOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // ── Parse Intent extras ──
        val amount        = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val merchant      = intent.getStringExtra(EXTRA_MERCHANT)
        val accountLast4  = intent.getStringExtra(EXTRA_ACCOUNT_LAST4)
        val paymentMode   = intent.getStringExtra(EXTRA_PAYMENT_MODE) ?: "UPI"
        val timestamp     = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val rawSms        = intent.getStringExtra(EXTRA_RAW_SMS) ?: ""

        if (amount <= 0.0) { stopSelf(); return START_NOT_STICKY }

        showOverlay(
            amount       = amount,
            merchant     = merchant,
            accountLast4 = accountLast4,
            paymentMode  = paymentMode,
            timestamp    = timestamp,
            rawSms       = rawSms
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        composeOwner.onDestroy()
        safeRemoveOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Overlay setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun showOverlay(
        amount: Double,
        merchant: String?,
        accountLast4: String?,
        paymentMode: String,
        timestamp: Long,
        rawSms: String
    ) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not FLAG_NOT_TOUCH_MODAL → modal (blocks touches to the app behind)
            // Not FLAG_NOT_FOCUSABLE  → receives key events (KEYCODE_BACK)
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        val root = BackInterceptorLayout(this).apply {
            onBackPressed = { handleBackPressed() }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // Attach lifecycle owners BEFORE setContent
            composeOwner.attachToView(this)
            setContent {
                TrackItTheme {
                    ExpenseEntryPopup(
                        amount         = amount,
                        detectedMerch  = merchant,
                        accountLast4   = accountLast4,
                        paymentMode    = paymentMode,
                        timestamp      = timestamp,
                        backPressCount = backPressCount,
                        showSuccess    = saveSuccess,
                        onSave         = { data -> handleSave(data) },
                        onSkip         = { data -> handleSkip(data) }
                    )
                }
            }
        }

        root.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        overlayRootView = root
        windowManager.addView(root, params)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save / Skip handlers
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleSave(data: ExpenseEntryData) {
        serviceScope.launch {
            val expense = data.toExpense(isLogged = true)
            addExpenseUseCase(expense)
                .onSuccess {
                    saveSuccess = true              // triggers green-checkmark animation
                    delay(POST_SAVE_DISMISS_MS)
                    enqueueSyncAndDismiss()
                }
                .onFailure {
                    // Keep overlay open; popup observes saveSuccess = false
                    // TODO: expose error state to popup for snackbar
                }
        }
    }

    private fun handleSkip(data: ExpenseEntryData?) {
        serviceScope.launch {
            val expense = data?.toExpense(isLogged = false)
                ?: Expense(
                    amount   = 0.0,
                    notes    = "Skipped auto-detection",
                    isLogged = false,
                    isSynced = false
                )
            addExpenseUseCase(expense)
            dismissOverlay()
        }
    }

    private fun handleBackPressed() {
        // Incrementing triggers LaunchedEffect in the composable → Snackbar shown
        backPressCount++
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dismiss
    // ─────────────────────────────────────────────────────────────────────────

    private fun enqueueSyncAndDismiss() {
        SyncWorker.enqueue(WorkManager.getInstance(applicationContext))
        dismissOverlay()
    }

    private fun dismissOverlay() {
        safeRemoveOverlay()
        stopSelf()
    }

    private fun safeRemoveOverlay() {
        overlayRootView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayRootView = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Foreground notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Expense Detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while the UPI expense popup is active"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TrackIt — Payment Detected")
            .setContentText("Tap to open the expense entry popup")
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BackInterceptorLayout — inner view that captures KEYCODE_BACK
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A [FrameLayout] subclass that intercepts [KeyEvent.KEYCODE_BACK] at the view level.
     *
     * The window must NOT have [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE] set
     * for key events to be delivered here (see [showOverlay] params).
     *
     * Returning `true` from [dispatchKeyEvent] consumes the event so the system
     * does NOT dismiss the overlay on back press.
     */
    inner class BackInterceptorLayout(context: Context) : FrameLayout(context) {

        var onBackPressed: (() -> Unit)? = null

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                event.action == KeyEvent.ACTION_UP
            ) {
                onBackPressed?.invoke()
                return true   // consume — prevents system from dismissing the overlay
            }
            return super.dispatchKeyEvent(event)
        }

        // Required for the window to become focusable and receive key events
        override fun onCreateInputConnection(outAttrs: EditorInfo) = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Companion — Intent factory + constants
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val CHANNEL_ID              = "trackit_overlay_channel"
        private const val NOTIFICATION_ID         = 1001
        private const val POST_SAVE_DISMISS_MS    = 1_500L

        const val EXTRA_AMOUNT        = "extra_amount"
        const val EXTRA_MERCHANT      = "extra_merchant"
        const val EXTRA_ACCOUNT_LAST4 = "extra_account_last4"
        const val EXTRA_PAYMENT_MODE  = "extra_payment_mode"
        const val EXTRA_TIMESTAMP     = "extra_timestamp"
        const val EXTRA_RAW_SMS       = "extra_raw_sms"

        /**
         * Factory method for [SmsReceiver] to start the overlay service.
         * Bundles all [ParsedTransaction] fields into Intent extras.
         */
        fun createIntent(context: Context, parsed: ParsedTransaction): Intent =
            Intent(context, ExpenseOverlayService::class.java).apply {
                putExtra(EXTRA_AMOUNT,        parsed.amount)
                putExtra(EXTRA_MERCHANT,      parsed.merchant)
                putExtra(EXTRA_ACCOUNT_LAST4, parsed.accountLast4)
                putExtra(EXTRA_PAYMENT_MODE,  parsed.paymentMode)
                putExtra(EXTRA_TIMESTAMP,     parsed.timestamp)
                putExtra(EXTRA_RAW_SMS,       parsed.rawSms)
            }
    }
}

/**
 * Carries the user-filled form data from [ExpenseEntryPopup] back to [ExpenseOverlayService].
 */
data class ExpenseEntryData(
    val amount: Double,
    val merchant: String,
    val category: ExpenseCategory,
    val accountLast4: String?,
    val notes: String,
    val timestamp: Long,
    val paymentMode: String
) {
    /**
     * Convert to domain [Expense] for persistence.
     *
     * - [amount] is already in **INR Double** — no paise conversion.
     * - Payment mode is embedded in [account] for display context
     *   (e.g. "UPI · XX1234") since the Expense entity has no dedicated paymentMode column.
     * - [loggedAt] is set to now when [isLogged] is true (user reviewed and saved).
     */
    fun toExpense(isLogged: Boolean): Expense = Expense(
        amount        = amount,
        merchant      = merchant.trim(),
        category      = category.displayName,
        account       = buildAccount(),
        notes         = notes.takeIf { it.isNotBlank() },
        rawSms        = "",           // raw SMS not available at overlay level
        isLogged      = isLogged,
        isSynced      = false,
        transactionAt = timestamp,
        loggedAt      = if (isLogged) System.currentTimeMillis() else null
    )

    /**
     * Builds the account display string from payment mode and last-4 digits.
     * Examples:
     *  - paymentMode=UPI,  accountLast4=null  → "UPI"
     *  - paymentMode=UPI,  accountLast4=1234  → "UPI · XX1234"
     *  - paymentMode=NEFT, accountLast4=5678  → "NEFT · XX5678"
     */
    private fun buildAccount(): String {
        val modePart    = paymentMode.takeIf { it.isNotBlank() && it != "Unknown" } ?: ""
        val accountPart = accountLast4?.let { "XX$it" } ?: ""
        return listOf(modePart, accountPart)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
}

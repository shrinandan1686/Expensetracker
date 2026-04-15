package com.trackit.expense

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.trackit.expense.worker.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Application entry point for TrackIt — UPI Expense Tracker.
 *
 * ## Hilt
 * [@HiltAndroidApp] triggers Hilt's code generation and starts the dependency graph.
 *
 * ## WorkManager + Hilt
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory]
 * instead of the default factory. This enables [@HiltWorker] annotations in workers
 * like [SyncWorker].
 *
 * **Important:** When using a custom [Configuration.Provider] you must disable
 * WorkManager's default initializer in AndroidManifest.xml:
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false"
 *     tools:node="merge">
 *   <meta-data
 *       android:name="androidx.work.WorkManagerInitializer"
 *       android:value="androidx.startup"
 *       tools:node="remove" />
 * </provider>
 * ```
 *
 * ## Periodic Sync
 * A 15-minute periodic [SyncWorker] is enqueued on every app launch with
 * [ExistingPeriodicWorkPolicy.KEEP] — idempotent, so multiple cold starts
 * don't create duplicate chains.
 */
@HiltAndroidApp
class TrackItApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var expenseRepository: com.trackit.expense.domain.repository.ExpenseRepository

    private val applicationScope = kotlinx.coroutines.MainScope()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialise Notification Channels
        com.trackit.expense.util.NotificationHelper.createNotificationChannels(this)

        // 2. Schedule Workers (Idempotent)
        val workManager = WorkManager.getInstance(this)
        com.trackit.expense.worker.SyncWorker.enqueuePeriodic(workManager)
        com.trackit.expense.worker.BudgetAlertWorker.enqueueDaily(workManager)
        com.trackit.expense.worker.UnloggedReminderWorker.enqueuePeriodic(workManager)
        com.trackit.expense.worker.WeeklySummaryWorker.enqueueWeekly(workManager)
        com.trackit.expense.widget.WidgetRefreshWorker.enqueuePeriodic(workManager)

        // 3. Real-time Badge Update (Observe unlogged expenses)
        applicationScope.launch {
            expenseRepository.getUnlogged().collect { list ->
                me.leolin.shortcutbadger.ShortcutBadger.applyCount(applicationContext, list.size)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        applicationScope.cancel()
    }
}

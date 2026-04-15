package com.trackit.expense.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that refreshes all active [TrackItWidget] instances.
 *
 * Two scheduling modes:
 * 1. **Periodic** — [enqueuePeriodic]: runs every 30 minutes (minimum period).
 * 2. **Immediate** — [enqueueNow]: one-shot, fired after any expense is logged.
 *
 * Both are idempotent: [ExistingPeriodicWorkPolicy.KEEP] / [ExistingWorkPolicy.REPLACE].
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val widgetDataRepository: WidgetDataRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        widgetDataRepository.refreshWidget()
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME   = "trackit_widget_refresh_periodic"
        private const val IMMEDIATE_WORK_NAME  = "trackit_widget_refresh_now"

        /** Enqueues a 30-minute periodic refresh.  Safe to call on every app start. */
        fun enqueuePeriodic(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            )
        }

        /**
         * Fires a one-shot widget refresh immediately.
         * Called from [com.trackit.expense.data.repository.ExpenseRepositoryImpl]
         * after any expense is saved or updated.
         */
        fun enqueueNow(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            )
        }
    }
}

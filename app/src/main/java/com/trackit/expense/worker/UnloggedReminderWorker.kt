package com.trackit.expense.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import me.leolin.shortcutbadger.ShortcutBadger
import java.util.concurrent.TimeUnit

/**
 * Worker that reminds users to review auto-detected transactions.
 * Runs every 4 hours. Updates app badge count as well.
 */
@HiltWorker
class UnloggedReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val expenseDao: ExpenseDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val unlogged = expenseDao.getUnlogged().first()
        val count = unlogged.size
        
        // Sync badge count
        ShortcutBadger.applyCount(applicationContext, count)
        
        if (count > 0) {
            NotificationHelper.showNotification(
                applicationContext,
                NotificationHelper.CHANNEL_REMINDERS,
                ID_REMINDER,
                "Logging Pending",
                "You have $count unlogged expense(s). Click to review.",
                "trackit://unlogged"
            )
        }
        
        return Result.success()
    }

    companion object {
        private const val ID_REMINDER = 99

        fun enqueuePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<UnloggedReminderWorker>(4, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "unlogged_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

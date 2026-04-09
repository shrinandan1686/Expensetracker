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
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker that sends a summary of expenditures for the past week.
 * Triggered on Sundays at 6 PM.
 */
@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val expenseDao: ExpenseDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val calendar = Calendar.getInstance()
        val endMs = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startMs = calendar.timeInMillis

        val categoryTotals = expenseDao.getTotalByCategoryForRange(startMs, endMs)
        
        if (categoryTotals.isEmpty()) return Result.success()

        val grandTotal = categoryTotals.sumOf { it.total }
        val topCategory = categoryTotals.first() // Already sorted DESC by DAO query

        NotificationHelper.showNotification(
            applicationContext,
            NotificationHelper.CHANNEL_BUDGET_ALERTS,
            ID_WEEKLY_SUMMARY,
            "Weekly Roundup 🗓️",
            "You spent ₹${grandTotal.toInt()} this week. Top category: ${topCategory.category} (₹${topCategory.total.toInt()})",
            "trackit://analytics"
        )

        return Result.success()
    }

    companion object {
        private const val ID_WEEKLY_SUMMARY = 101

        fun enqueueWeekly(workManager: WorkManager) {
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            
            // Find next Sunday 6 PM
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 18)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
            
            val initialDelay = calendar.timeInMillis - now
            
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "weekly_summary",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

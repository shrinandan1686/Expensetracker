package com.trackit.expense.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.trackit.expense.data.local.dao.BudgetDao
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.domain.repository.PreferenceRepository
import com.trackit.expense.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Worker that runs daily to check if monthly spending has crossed budget thresholds.
 * Thresholds: 50%, 80%, 100%, 120%.
 * Alerts are sent once per threshold per month.
 */
@HiltWorker
class BudgetAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    private val preferenceRepository: PreferenceRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        
        val budget = budgetDao.getByMonthOneShot(monthKey) ?: return Result.success()
        val totalSpent = expenseDao.getTotalByMonthOneShot(monthKey)
        
        if (budget.overall <= 0) return Result.success()
        
        val percentage = (totalSpent / budget.overall * 100).toInt()
        
        // Define thresholds top-down to find the highest triggered one
        val thresholds = listOf(120, 100, 80, 50)
        
        for (t in thresholds) {
            if (percentage >= t) {
                if (!preferenceRepository.hasThresholdBeenTriggered(monthKey, t)) {
                    sendThresholdNotification(t, totalSpent, budget.overall)
                    preferenceRepository.markThresholdTriggered(monthKey, t)
                    // We only send one notification (the highest threshold reached) per run
                    break
                }
            }
        }
        
        return Result.success()
    }

    private fun sendThresholdNotification(threshold: Int, spent: Double, budget: Double) {
        val title = when (threshold) {
            120 -> "Budget Blown! 🚨"
            100 -> "Budget Reached 🛑"
            80 -> "Budget Warning ⚠️"
            else -> "Halfway Point 📊"
        }
        
        val message = when (threshold) {
            120 -> "You've exceeded your budget by ${((spent/budget - 1) * 100).toInt()}%. Total: ₹${spent.toInt()}."
            100 -> "You have reached your ₹${budget.toInt()} limit. Time to tighten the belt!"
            80 -> "You've used 80% of your ₹${budget.toInt()} budget. Just ₹${(budget - spent).toInt()} left."
            else -> "You've used 50% of your ₹${budget.toInt()} budget."
        }
        
        NotificationHelper.showNotification(
            applicationContext,
            NotificationHelper.CHANNEL_BUDGET_ALERTS,
            threshold, // Unique ID per threshold
            title,
            message,
            "trackit://home" // Deep link to dashboard
        )
    }

    companion object {
        fun enqueueDaily(workManager: WorkManager) {
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            
            // Set to today at 9 AM
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val initialDelay = calendar.timeInMillis - now
            
            val request = PeriodicWorkRequestBuilder<BudgetAlertWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "budget_daily_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

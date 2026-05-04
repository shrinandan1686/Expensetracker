package com.trackit.expense.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.data.remote.api.TrackItApiService
import com.trackit.expense.data.remote.dto.ExpenseDto
import com.trackit.expense.data.remote.dto.SyncRequestDto
import com.trackit.expense.util.SyncStateStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs locally-unsynced expenses to the Cloudflare backend.
 *
 * ## Custom Backoff Schedule
 * On network failures the worker schedules a new one-off run with an increasing delay
 * instead of relying on WorkManager's built-in exponential backoff.  Delays are:
 *
 * | Attempt | Initial delay |
 * |---------|--------------|
 * | 1st     | 1 minute     |
 * | 2nd     | 5 minutes    |
 * | 3rd     | 15 minutes   |
 * | 4th+    | 1 hour       |
 *
 * If the first failure happened more than [MAX_RETRY_DURATION_MS] (24 hours) ago,
 * the worker stops retrying and writes to [SyncStateStore] so the UI can show a
 * persistent "Sync failed — tap to retry" banner with a manual retry button.
 *
 * ## UI Visibility
 * The Dashboard observes [WorkManager.getWorkInfosForUniqueWorkFlow] for [WORK_NAME_ONE_OFF]
 * to display a non-blocking "Sync pending" indicator. The UI is never blocked.
 *
 * ## Scheduling
 * - **One-off** ([enqueue]): triggered after every local write.
 * - **Periodic** ([enqueuePeriodic]): 15-minute catch-up. Keeps syncing while
 *   the one-off retry chain is idle (e.g., between device restarts).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val expenseDao: ExpenseDao,
    private val apiService: TrackItApiService,
    private val syncStateStore: SyncStateStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val attempt     = inputData.getInt(KEY_ATTEMPT, 0)
        val firstFailed = inputData.getLong(KEY_FIRST_FAILED_AT, 0L)
        Log.d(TAG, "SyncWorker started (attempt $attempt)")

        // ── 1. Nothing to sync ────────────────────────────────────────────────
        val unsynced = expenseDao.getUnsynced()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "Nothing to sync — success.")
            syncStateStore.clearFailure()
            return Result.success()
        }
        Log.i(TAG, "Found ${unsynced.size} unsynced expense(s).")

        // ── 2. Map to DTOs ────────────────────────────────────────────────────
        val dtos = unsynced.map { e ->
            ExpenseDto(
                id            = e.id,
                amount        = e.amount,
                merchant      = e.merchant,
                category      = e.category,
                account       = e.account,
                notes         = e.notes,
                rawSms        = e.rawSms,
                isLogged      = e.isLogged,
                transactionAt = e.transactionAt,
                loggedAt      = e.loggedAt,
                createdAt     = e.createdAt,
                updatedAt     = e.updatedAt
            )
        }

        // ── 3. POST to /api/sync ──────────────────────────────────────────────
        return try {
            val response = apiService.syncExpenses(SyncRequestDto(expenses = dtos))

            if (response.isSuccessful) {
                val body      = response.body()
                val syncedIds = body?.syncedIds ?: dtos.map { it.id }

                syncedIds.chunked(SQLITE_IN_LIMIT).forEach { chunk ->
                    expenseDao.markSynced(chunk)
                }

                val failedCount = body?.failedIds?.size ?: 0
                Log.i(TAG, "Sync done — ${syncedIds.size} synced, $failedCount failed.")
                syncStateStore.clearFailure()
                Result.success()

            } else {
                val code = response.code()
                Log.w(TAG, "Server returned HTTP $code.")
                if (code in 400..499) {
                    Log.e(TAG, "Non-retriable HTTP $code — giving up.")
                    Result.failure()
                } else {
                    scheduleRetry(attempt, firstFailed)
                    // Return failure so WorkManager marks this work unit as done;
                    // the retry is a freshly-enqueued separate work item.
                    Result.failure()
                }
            }

        } catch (e: IOException) {
            Log.w(TAG, "Network error: ${e.message}")
            scheduleRetry(attempt, firstFailed)
            Result.failure()

        } catch (e: HttpException) {
            Log.e(TAG, "HTTP exception: ${e.code()}")
            scheduleRetry(attempt, firstFailed)
            Result.failure()

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            Result.failure()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETRY SCHEDULING
    // ─────────────────────────────────────────────────────────────────────────

    private fun scheduleRetry(attempt: Int, firstFailedParam: Long) {
        val now         = System.currentTimeMillis()
        val firstFailed = if (firstFailedParam == 0L) now else firstFailedParam

        // 24-hour cap exceeded → mark as permanently failed
        if (now - firstFailed > MAX_RETRY_DURATION_MS) {
            Log.e(TAG, "Sync has been failing for >24h — marking as failed.")
            syncStateStore.markSyncFailed()
            return
        }

        val nextAttempt = attempt + 1
        // Backoff ladder: attempt index → delay (minutes)
        val delayMinutes = BACKOFF_DELAYS_MINUTES.getOrElse(attempt) {
            BACKOFF_DELAYS_MINUTES.last()
        }

        Log.i(TAG, "Scheduling retry attempt $nextAttempt in ${delayMinutes}min.")

        val data = workDataOf(
            KEY_ATTEMPT       to nextAttempt,
            KEY_FIRST_FAILED_AT to firstFailed
        )
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInputData(data)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        // REPLACE: override any already-queued retry with this fresh one
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(WORK_NAME_ONE_OFF, ExistingWorkPolicy.REPLACE, request)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMPANION
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "SyncWorker"

        // Unique work names (public so HomeViewModel can observe them)
        const val WORK_NAME_ONE_OFF  = "trackit_sync_one_off"
        const val WORK_NAME_PERIODIC = "trackit_sync_periodic"

        // Input data keys (used when scheduling retries)
        private const val KEY_ATTEMPT         = "attempt"
        private const val KEY_FIRST_FAILED_AT = "first_failed_at"

        /** Backoff ladder (minutes): 1 → 5 → 15 → 60 (then stays at 60). */
        private val BACKOFF_DELAYS_MINUTES = listOf(1L, 5L, 15L, 60L)

        /** After 24 h of consecutive failures the sync is declared definitively failed. */
        private val MAX_RETRY_DURATION_MS = TimeUnit.HOURS.toMillis(24)

        private const val SQLITE_IN_LIMIT = 900

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Enqueue a one-off sync with [ExistingWorkPolicy.KEEP].
         *
         * Uses KEEP so concurrent local writes don't flood the queue — one queued
         * sync is sufficient to drain all pending records.
         * The initial attempt uses no delay; the backoff ladder starts on first failure.
         */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                // Minimal fallback backoff (WorkManager requires one to be set).
                // Our custom retry logic overrides this in practice.
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30L, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_ONE_OFF,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Enqueue a periodic catch-up sync (15-minute interval).
         * Complements the one-off retry chain — syncs whenever the device is online
         * even if the one-off chain has been exhausted (app restarted, etc.).
         */
        fun enqueuePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

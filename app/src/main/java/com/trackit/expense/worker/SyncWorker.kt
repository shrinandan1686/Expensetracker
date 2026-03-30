package com.trackit.expense.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.data.remote.api.TrackItApiService
import com.trackit.expense.data.remote.dto.ExpenseDto
import com.trackit.expense.data.remote.dto.SyncRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs locally-unsynced expenses to the Cloudflare backend.
 *
 * ## Flow
 * 1. Query Room for all [ExpenseEntity] records where `is_synced = 0`.
 * 2. If the list is empty → return [Result.success] immediately (nothing to do).
 * 3. Map records to [ExpenseDto] and POST to `POST /api/sync` via [TrackItApiService].
 * 4. Parse [SyncResponseDto.syncedIds] — mark only server-confirmed IDs as synced.
 * 5. On any network or server error → return [Result.retry] (WorkManager applies
 *    exponential backoff automatically).
 *
 * ## Retry / Backoff
 * - Policy:       [BackoffPolicy.EXPONENTIAL]
 * - Initial delay: [BACKOFF_DELAY_SECONDS] = 30 s
 * - Max retries:   WorkManager default (10 retries before [Result.failure]).
 *
 * ## Scheduling
 * Two flavours:
 * - **One-shot** ([enqueueOneOff]): triggered immediately after every local write.
 *   Uses [ExistingWorkPolicy.KEEP] so concurrent writes don't flood the queue.
 * - **Periodic** ([enqueuePeriodic]): 15-minute interval for catch-up syncs
 *   (e.g. after the device comes back online). Enqueued from [TrackItApp.onCreate].
 *
 * ## Hilt
 * [@HiltWorker] + [@AssistedInject] gives full dependency injection without
 * a custom [WorkerFactory].
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val expenseDao: ExpenseDao,
    private val apiService: TrackItApiService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started (attempt ${runAttemptCount + 1})")

        // ── 1. Bail early if nothing to sync ──────────────────────────────────
        val unsynced = expenseDao.getUnsynced()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "Nothing to sync — returning success.")
            return Result.success()
        }
        Log.i(TAG, "Found ${unsynced.size} unsynced expense(s).")

        // ── 2. Map to DTOs ────────────────────────────────────────────────────
        val dtos = unsynced.map { entity ->
            ExpenseDto(
                id            = entity.id,
                amount        = entity.amount,
                merchant      = entity.merchant,
                category      = entity.category,
                account       = entity.account,
                notes         = entity.notes,
                rawSms        = entity.rawSms,
                isLogged      = entity.isLogged,
                transactionAt = entity.transactionAt,
                loggedAt      = entity.loggedAt,
                createdAt     = entity.createdAt
            )
        }

        // ── 3. POST to /api/sync ──────────────────────────────────────────────
        return try {
            val response = apiService.syncExpenses(SyncRequestDto(expenses = dtos))

            if (response.isSuccessful) {
                val body = response.body()
                val syncedIds = body?.syncedIds ?: dtos.map { it.id }

                if (syncedIds.isNotEmpty()) {
                    // Batch-update in chunks (SQLite IN clause limit ≈ 999)
                    syncedIds.chunked(SQLITE_IN_LIMIT).forEach { chunk ->
                        expenseDao.markSynced(chunk)
                    }
                }

                val failedCount = body?.failedIds?.size ?: 0
                Log.i(TAG, "Sync complete — ${syncedIds.size} synced, $failedCount failed. ${body?.message}")
                Result.success()

            } else {
                val code = response.code()
                Log.w(TAG, "Server returned HTTP $code — scheduling retry.")
                if (code in 400..499) {
                    // Client error (bad auth, validation) — don't retry indefinitely
                    Log.e(TAG, "Non-retriable HTTP $code — giving up.")
                    Result.failure()
                } else {
                    // Server error (5xx) — retry with backoff
                    Result.retry()
                }
            }

        } catch (e: IOException) {
            // Network unavailable, timeout, etc.
            Log.w(TAG, "Network error during sync: ${e.message} — will retry.")
            Result.retry()

        } catch (e: HttpException) {
            Log.e(TAG, "HTTP exception during sync: ${e.code()} — ${e.message()}")
            Result.retry()

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG                  = "SyncWorker"
        private const val WORK_NAME_ONE_OFF    = "trackit_sync_one_off"
        private const val WORK_NAME_PERIODIC   = "trackit_sync_periodic"
        private const val BACKOFF_DELAY_SECONDS = 30L
        private const val SQLITE_IN_LIMIT       = 900   // safe below SQLite's 999 limit

        /** Network constraint shared by both work requests.  */
        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Enqueues a one-off sync with [ExistingWorkPolicy.KEEP].
         *
         * [KEEP] means: if a sync is already queued and waiting for network,
         * don't enqueue another one — let the existing one handle all pending records.
         * Called from [ExpenseRepositoryImpl] after every local write.
         */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_ONE_OFF,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Enqueues a periodic catch-up sync (15-minute interval).
         * Uses [ExistingWorkPolicy.KEEP] so this is idempotent — safe to call from
         * [TrackItApp.onCreate] and from [SmsReceiver.handleBootCompleted].
         */
        fun enqueuePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

package com.trackit.expense.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight SharedPreferences store that tracks offline sync failure state.
 *
 * Written by [com.trackit.expense.worker.SyncWorker] when retries are exhausted,
 * read by [com.trackit.expense.presentation.home.HomeViewModel] to drive the
 * "Sync failed — tap to retry" banner.
 *
 * ## Fields
 * - [syncFailedAt]   – epoch millis when sync was definitively marked failed (0 = not failed).
 * - [firstFailedAt]  – epoch millis of the first failure in the current retry run.
 * - [retryAttempt]   – number of retry attempts completed in the current run.
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────────────────────
    // Public state
    // ─────────────────────────────────────────────────────────────────────────

    var syncFailedAt: Long
        get()      = prefs.getLong(KEY_SYNC_FAILED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_SYNC_FAILED_AT, value).apply()

    var firstFailedAt: Long
        get()      = prefs.getLong(KEY_FIRST_FAILED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_FIRST_FAILED_AT, value).apply()

    var retryAttempt: Int
        get()      = prefs.getInt(KEY_RETRY_ATTEMPT, 0)
        set(value) = prefs.edit().putInt(KEY_RETRY_ATTEMPT, value).apply()

    /** True if sync has been definitively failed (cap exceeded). */
    val isSyncFailed: Boolean get() = syncFailedAt > 0L

    /**
     * `updated_at` watermark of the last successful pull from the server.
     *
     * 0 means "never pulled", which makes the next sync do a full restore — that is
     * what recovers a user's history after a reinstall. Only advanced after the
     * pulled rows have actually been written to Room, so a crash mid-pull retries
     * the same window rather than skipping it.
     */
    var lastPullAt: Long
        get() = prefs.getLong(KEY_LAST_PULL_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PULL_AT, value).apply()

    // ─────────────────────────────────────────────────────────────────────────
    // Mutations
    // ─────────────────────────────────────────────────────────────────────────

    /** Call when a sync attempt first encounters a network error. */
    fun recordFirstFailure() {
        if (firstFailedAt == 0L) {
            firstFailedAt = System.currentTimeMillis()
        }
        retryAttempt++
    }

    /**
     * Call when the 24-hour retry cap has been exceeded.
     * Sets [syncFailedAt] and resets the transient retry counters.
     */
    fun markSyncFailed() {
        syncFailedAt  = System.currentTimeMillis()
        firstFailedAt = 0L
        retryAttempt  = 0
    }

    /**
     * Call after a successful sync or when the user triggers a manual retry.
     * Clears all failure state.
     */
    fun clearFailure() {
        prefs.edit()
            .remove(KEY_SYNC_FAILED_AT)
            .remove(KEY_FIRST_FAILED_AT)
            .remove(KEY_RETRY_ATTEMPT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME          = "trackit_sync_state"
        private const val KEY_SYNC_FAILED_AT  = "sync_failed_at"
        private const val KEY_FIRST_FAILED_AT = "first_failed_at"
        private const val KEY_RETRY_ATTEMPT   = "retry_attempt"
        private const val KEY_LAST_PULL_AT    = "last_pull_at"
    }
}

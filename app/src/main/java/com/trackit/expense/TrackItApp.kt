package com.trackit.expense

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for TrackIt - UPI Expense Tracker.
 *
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation
 * and provides the application-level dependency graph.
 *
 * Also implements [Configuration.Provider] to integrate Hilt with WorkManager,
 * enabling [HiltWorker] annotations in background sync workers.
 */
@HiltAndroidApp
class TrackItApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

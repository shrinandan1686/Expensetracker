package com.trackit.expense.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.trackit.expense.data.local.dao.BudgetDao
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.data.local.db.DatabaseMigrations
import com.trackit.expense.data.local.db.TrackItDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database, DAO, and WorkManager instances.
 *
 * Installed in [SingletonComponent] so all provided objects are app-scoped singletons.
 *
 * ## Migration strategy
 * Currently uses [fallbackToDestructiveMigration] for development convenience —
 * the database is recreated from scratch whenever the schema version increments.
 *
 * **Before any production release**, replace the fallback with explicit migrations:
 * ```kotlin
 * .addMigrations(
 *     DatabaseMigrations.MIGRATION_1_2,
 *     DatabaseMigrations.MIGRATION_2_3
 * )
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTrackItDatabase(
        @ApplicationContext context: Context
    ): TrackItDatabase = Room.databaseBuilder(
        context,
        TrackItDatabase::class.java,
        TrackItDatabase.DATABASE_NAME
    )
        // DEV: fallback to destructive migration — uncomment addMigrations() for production
        .fallbackToDestructiveMigration()
        // PROD: .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
        .build()

    @Provides
    @Singleton
    fun provideExpenseDao(database: TrackItDatabase): ExpenseDao = database.expenseDao()

    @Provides
    @Singleton
    fun provideBudgetDao(database: TrackItDatabase): BudgetDao = database.budgetDao()

    /**
     * Provides a singleton [WorkManager] instance.
     * Required by [com.trackit.expense.data.repository.ExpenseRepositoryImpl]
     * to enqueue [com.trackit.expense.worker.SyncWorker] after local writes.
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager = WorkManager.getInstance(context)
}

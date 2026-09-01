package com.trackit.expense.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.trackit.expense.data.local.dao.AccountDao
import com.trackit.expense.data.local.dao.BudgetDao
import com.trackit.expense.data.local.dao.CategoryDao
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.data.local.dao.GroupDao
import com.trackit.expense.data.local.dao.SplitDao
import com.trackit.expense.BuildConfig
import com.trackit.expense.data.local.db.DatabaseMigrations
import com.trackit.expense.data.local.db.TrackItDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hilt module providing Room database, DAO, and WorkManager instances.
 *
 * Installed in [SingletonComponent] so all provided objects are app-scoped singletons.
 *
 * ## Migration strategy
 * [DatabaseMigrations.ALL] supplies an explicit migration for every consecutive
 * version pair. Destructive fallback is enabled in **debug builds only**, so a
 * release build with a missing migration fails at open time rather than dropping
 * the user's expense history without telling anyone.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTrackItDatabase(
        @ApplicationContext context: Context,
        categoryDaoProvider: Provider<CategoryDao>
    ): TrackItDatabase = Room.databaseBuilder(
        context,
        TrackItDatabase::class.java,
        TrackItDatabase.DATABASE_NAME
    )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-seed categories on creation
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = categoryDaoProvider.get()
                    if (dao.getCategoryCount() == 0) {
                        dao.insertCategories(TrackItDatabase.getDefaultCategories())
                    }
                }
            }
        })
        .addMigrations(*DatabaseMigrations.ALL)
        .apply {
            // Debug only. In a release build a missing migration must fail loudly at
            // open time — the alternative is Room silently dropping every expense
            // the user has ever recorded.
            if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
        }
        .build()

    @Provides
    @Singleton
    fun provideExpenseDao(database: TrackItDatabase): ExpenseDao = database.expenseDao()

    @Provides
    @Singleton
    fun provideBudgetDao(database: TrackItDatabase): BudgetDao = database.budgetDao()

    @Provides
    @Singleton
    fun provideAccountDao(database: TrackItDatabase): AccountDao = database.accountDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: TrackItDatabase): CategoryDao = database.categoryDao()

    @Provides
    @Singleton
    fun provideGroupDao(database: TrackItDatabase): GroupDao = database.groupDao()

    @Provides
    @Singleton
    fun provideSplitDao(database: TrackItDatabase): SplitDao = database.splitDao()

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

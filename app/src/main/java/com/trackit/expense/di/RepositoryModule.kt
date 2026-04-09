package com.trackit.expense.di

import com.google.gson.Gson
import com.trackit.expense.data.repository.BudgetRepositoryImpl
import com.trackit.expense.data.repository.ExpenseRepositoryImpl
import com.trackit.expense.data.repository.PreferenceRepositoryImpl
import com.trackit.expense.domain.repository.BudgetRepository
import com.trackit.expense.domain.repository.ExpenseRepository
import com.trackit.expense.domain.repository.PreferenceRepository
import com.trackit.expense.domain.repository.SettingsRepository
import com.trackit.expense.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds domain repository interfaces to their concrete implementations.
 *
 * Uses [@Binds] for implementations that are directly injectable (no extra wiring).
 * Uses [@Provides] for [Gson] since it's created by [NetworkModule] (shared instance).
 *
 * Installed in [SingletonComponent] — repositories are app-wide singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        impl: ExpenseRepositoryImpl
    ): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(
        impl: BudgetRepositoryImpl
    ): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindPreferenceRepository(
        impl: PreferenceRepositoryImpl
    ): PreferenceRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    companion object {
        /**
         * Gson instance shared between [NetworkModule] (Retrofit converter) and
         * [BudgetRepositoryImpl] (categoryBudgets JSON serialisation).
         * Declared here as a companion @Provides to avoid a separate module.
         */
        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()
    }
}

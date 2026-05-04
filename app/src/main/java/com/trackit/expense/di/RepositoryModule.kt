package com.trackit.expense.di

import com.google.gson.Gson
import com.trackit.expense.data.repository.AuthRepositoryImpl
import com.trackit.expense.data.repository.BudgetRepositoryImpl
import com.trackit.expense.data.repository.ExpenseRepositoryImpl
import com.trackit.expense.data.repository.GroupRepositoryImpl
import com.trackit.expense.data.repository.PreferenceRepositoryImpl
import com.trackit.expense.data.repository.SettingsRepositoryImpl
import com.trackit.expense.data.repository.SplitRepositoryImpl
import com.trackit.expense.data.repository.UserRepositoryImpl
import com.trackit.expense.domain.repository.AuthRepository
import com.trackit.expense.domain.repository.BudgetRepository
import com.trackit.expense.domain.repository.ExpenseRepository
import com.trackit.expense.domain.repository.GroupRepository
import com.trackit.expense.domain.repository.PreferenceRepository
import com.trackit.expense.domain.repository.SettingsRepository
import com.trackit.expense.domain.repository.SplitRepository
import com.trackit.expense.domain.repository.UserRepository
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
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

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

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        impl: GroupRepositoryImpl
    ): GroupRepository

    @Binds
    @Singleton
    abstract fun bindSplitRepository(
        impl: SplitRepositoryImpl
    ): SplitRepository

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

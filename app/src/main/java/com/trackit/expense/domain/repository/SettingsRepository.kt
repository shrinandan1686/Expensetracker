package com.trackit.expense.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing application settings and user preferences.
 */
interface SettingsRepository {

    /** Notification settings */
    val budgetAlertsEnabled: Flow<Boolean>
    val weeklySummaryEnabled: Flow<Boolean>
    val unloggedRemindersEnabled: Flow<Boolean>
    val reminderIntervalHours: Flow<Int> // 2, 4, or 8

    /** Appearance settings */
    val themeMode: Flow<ThemeMode>
    val dynamicColorEnabled: Flow<Boolean>

    suspend fun setBudgetAlertsEnabled(enabled: Boolean)
    suspend fun setWeeklySummaryEnabled(enabled: Boolean)
    suspend fun setUnloggedRemindersEnabled(enabled: Boolean)
    suspend fun setReminderIntervalHours(hours: Int)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColorEnabled(enabled: Boolean)
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

package com.trackit.expense.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.trackit.expense.domain.repository.SettingsRepository
import com.trackit.expense.domain.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
        val WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("weekly_summary_enabled")
        val UNLOGGED_REMINDERS_ENABLED = booleanPreferencesKey("unlogged_reminders_enabled")
        val REMINDER_INTERVAL_HOURS = intPreferencesKey("reminder_interval_hours")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
    }

    override val budgetAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.BUDGET_ALERTS_ENABLED] ?: true }
    override val weeklySummaryEnabled: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.WEEKLY_SUMMARY_ENABLED] ?: true }
    override val unloggedRemindersEnabled: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.UNLOGGED_REMINDERS_ENABLED] ?: true }
    override val reminderIntervalHours: Flow<Int> = context.dataStore.data.map { it[PreferencesKeys.REMINDER_INTERVAL_HOURS] ?: 4 }
    override val themeMode: Flow<ThemeMode> = context.dataStore.data.map { 
        val modeStr = it[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        ThemeMode.valueOf(modeStr)
    }
    override val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.DYNAMIC_COLOR_ENABLED] ?: true }

    override suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BUDGET_ALERTS_ENABLED] = enabled }
    }

    override suspend fun setWeeklySummaryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.WEEKLY_SUMMARY_ENABLED] = enabled }
    }

    override suspend fun setUnloggedRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.UNLOGGED_REMINDERS_ENABLED] = enabled }
    }

    override suspend fun setReminderIntervalHours(hours: Int) {
        context.dataStore.edit { it[PreferencesKeys.REMINDER_INTERVAL_HOURS] = hours }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled }
    }
}

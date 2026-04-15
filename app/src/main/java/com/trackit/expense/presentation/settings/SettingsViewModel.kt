package com.trackit.expense.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.data.local.dao.AccountDao
import com.trackit.expense.data.local.dao.CategoryDao
import com.trackit.expense.data.local.entity.AccountEntity
import com.trackit.expense.data.local.entity.AccountType
import com.trackit.expense.data.local.entity.CategoryEntity
import com.trackit.expense.domain.repository.SettingsRepository
import com.trackit.expense.domain.repository.ThemeMode
import com.trackit.expense.domain.usecase.BulkSmsImportUseCase
import com.trackit.expense.domain.usecase.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportState {
    object Idle    : ImportState()
    data class Loading(val read: Int, val total: Int) : ImportState()
    data class Done(val imported: Int, val skipped: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

data class SettingsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val budgetAlertsEnabled: Boolean = true,
    val weeklySummaryEnabled: Boolean = true,
    val unloggedRemindersEnabled: Boolean = true,
    val reminderIntervalHours: Int = 4,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val exportStatus: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val bulkSmsImportUseCase: BulkSmsImportUseCase
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        accountDao.getAllAccounts(),
        categoryDao.getAllCategories(),
        settingsRepository.budgetAlertsEnabled,
        settingsRepository.weeklySummaryEnabled,
        settingsRepository.unloggedRemindersEnabled,
        settingsRepository.reminderIntervalHours,
        settingsRepository.themeMode,
        settingsRepository.dynamicColorEnabled
    ) { params ->
        val accounts = params[0] as List<AccountEntity>
        val categories = params[1] as List<CategoryEntity>
        val budgetAlerts = params[2] as Boolean
        val weeklySummary = params[3] as Boolean
        val unloggedReminders = params[4] as Boolean
        val interval = params[5] as Int
        val theme = params[6] as ThemeMode
        val dynamicColor = params[7] as Boolean

        SettingsUiState(
            accounts = accounts,
            categories = categories,
            budgetAlertsEnabled = budgetAlerts,
            weeklySummaryEnabled = weeklySummary,
            unloggedRemindersEnabled = unloggedReminders,
            reminderIntervalHours = interval,
            themeMode = theme,
            dynamicColorEnabled = dynamicColor
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage = _exportMessage.asStateFlow()

    private val _shareUri = MutableStateFlow<Uri?>(null)
    val shareUri = _shareUri.asStateFlow()

    fun clearShareUri() { _shareUri.value = null }

    fun addAccount(name: String, last4: String, type: AccountType, colorHex: String) {
        viewModelScope.launch {
            val account = AccountEntity(
                name = name,
                last4 = last4,
                type = type,
                colorHex = colorHex
            )
            accountDao.insertAccount(account)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            accountDao.deleteAccount(account)
        }
    }

    fun addCategory(name: String, emoji: String) {
        viewModelScope.launch {
            val category = CategoryEntity(
                name = name,
                emoji = emoji,
                isCustom = true,
                sortOrder = 99 // Put at end
            )
            categoryDao.insertCategory(category)
        }
    }

    fun toggleCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryDao.updateEnabledStatus(category.id, !category.isEnabled)
        }
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBudgetAlertsEnabled(enabled) }
    }

    fun setWeeklySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWeeklySummaryEnabled(enabled) }
    }

    fun setUnloggedRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUnloggedRemindersEnabled(enabled) }
    }

    fun setReminderInterval(hours: Int) {
        viewModelScope.launch { settingsRepository.setReminderIntervalHours(hours) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColorEnabled(enabled) }
    }

    fun exportData(month: String? = null) {
        viewModelScope.launch {
            exportDataUseCase(month).onSuccess { uri ->
                _shareUri.value = uri
            }.onFailure { e ->
                _exportMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    // ── SMS Bulk Import ───────────────────────────────────────────────────────

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    fun startImport(fromMillis: Long) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading(0, 0)
            bulkSmsImportUseCase(fromMillis) { read, total ->
                _importState.value = ImportState.Loading(read, total)
            }.onSuccess { result ->
                _importState.value = ImportState.Done(result.imported, result.skipped)
            }.onFailure { e ->
                _importState.value = ImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun clearImportState() {
        _importState.value = ImportState.Idle
    }
}

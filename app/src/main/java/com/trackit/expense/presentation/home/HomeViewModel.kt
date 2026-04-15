package com.trackit.expense.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.trackit.expense.data.local.dao.CategoryTotal
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetAllExpensesUseCase
import com.trackit.expense.domain.usecase.GetCategoryTotalsUseCase
import com.trackit.expense.domain.usecase.GetMonthlyBudgetUseCase
import com.trackit.expense.domain.usecase.GetMonthlyTotalUseCase
import com.trackit.expense.domain.usecase.GetUnloggedExpensesUseCase
import com.trackit.expense.domain.usecase.SyncExpensesUseCase
import com.trackit.expense.util.SyncStateStore
import com.trackit.expense.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Status of the background sync operation, shown as a non-blocking banner on
 * the Dashboard. The UI never blocks on sync state.
 */
enum class SyncStatus {
    /** No pending/failed sync — banner is hidden. */
    IDLE,
    /** A sync work item is queued (e.g. waiting for network). */
    PENDING,
    /** A sync is actively running right now. */
    SYNCING,
    /**
     * All retries exhausted after 24 h of failures.
     * Shows a "Sync failed — tap to retry" banner with a manual retry button.
     */
    FAILED
}

/**
 * UI state for the Home screen.
 *
 * @property recentExpenses  Most recent 20 logged expenses for the feed.
 * @property unloggedCount   Badge count — auto-detected but not yet reviewed.
 * @property monthlyTotal    Total spend in INR for the current month.
 * @property syncStatus      Sync pipeline health, drives the sync status banner.
 * @property currentMonth    ISO month string "YYYY-MM" shown in the summary header.
 * @property isLoading       True while first data emission is awaited.
 * @property errorMessage    Non-null when a delete or sync error has occurred.
 */
data class HomeUiState(
    val recentExpenses: List<Expense>   = emptyList(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val monthlyBudget: Double           = 0.0,
    val unloggedCount: Int              = 0,
    val monthlyTotal: Double            = 0.0,
    val syncStatus: SyncStatus          = SyncStatus.IDLE,
    val currentMonth: String            = "",
    val isLoading: Boolean              = true,
    val errorMessage: String?           = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllExpensesUseCase:     GetAllExpensesUseCase,
    private val getUnloggedUseCase:       GetUnloggedExpensesUseCase,
    private val getMonthlyTotalUseCase:   GetMonthlyTotalUseCase,
    private val getBudgetUseCase:         GetMonthlyBudgetUseCase,
    private val getCategoryTotalsUseCase: GetCategoryTotalsUseCase,
    private val deleteExpenseUseCase:     DeleteExpenseUseCase,
    private val syncExpensesUseCase:      SyncExpensesUseCase,
    private val workManager:              WorkManager,
    private val syncStateStore:           SyncStateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val monthKey: String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    init {
        _uiState.update { it.copy(currentMonth = monthKey) }
        observeExpenses()
        observeUnlogged()
        observeMonthlyTotal()
        observeBudget()
        observeCategoryTotals()
        observeSyncStatus()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observers
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeExpenses() {
        viewModelScope.launch {
            getAllExpensesUseCase().collect { list ->
                _uiState.update { it.copy(recentExpenses = list.take(20), isLoading = false) }
            }
        }
    }

    private fun observeUnlogged() {
        viewModelScope.launch {
            getUnloggedUseCase().collect { list ->
                _uiState.update { it.copy(unloggedCount = list.size) }
            }
        }
    }

    private fun observeMonthlyTotal() {
        viewModelScope.launch {
            getMonthlyTotalUseCase(monthKey).collect { total ->
                _uiState.update { it.copy(monthlyTotal = total) }
            }
        }
    }

    private fun observeBudget() {
        viewModelScope.launch {
            getBudgetUseCase(monthKey).collect { budget ->
                _uiState.update { it.copy(monthlyBudget = budget?.overall ?: 0.0) }
            }
        }
    }

    private fun observeCategoryTotals() {
        viewModelScope.launch {
            getCategoryTotalsUseCase(monthKey).collect { totals ->
                _uiState.update { it.copy(categoryTotals = totals) }
            }
        }
    }

    /**
     * Observes WorkManager's live [WorkInfo] for the one-off sync job.
     *
     * Maps WorkInfo states to [SyncStatus]:
     * - RUNNING  → [SyncStatus.SYNCING]
     * - ENQUEUED / BLOCKED → [SyncStatus.PENDING]
     * - [SyncStateStore.isSyncFailed] = true → [SyncStatus.FAILED]  (checked after any state)
     * - anything else → [SyncStatus.IDLE]
     *
     * The [SyncStateStore.isSyncFailed] flag is set inside [SyncWorker] when the 24-hour
     * retry cap is exceeded. WorkInfo transitions to FAILED at that point, so the two
     * reads are naturally coordinated.
     */
    private fun observeSyncStatus() {
        viewModelScope.launch {
            workManager
                .getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_ONE_OFF)
                .collect { workInfos ->
                    val info   = workInfos.firstOrNull()
                    val status = when {
                        syncStateStore.isSyncFailed              -> SyncStatus.FAILED
                        info?.state == WorkInfo.State.RUNNING    -> SyncStatus.SYNCING
                        info?.state == WorkInfo.State.ENQUEUED   -> SyncStatus.PENDING
                        info?.state == WorkInfo.State.BLOCKED    -> SyncStatus.PENDING
                        else                                     -> SyncStatus.IDLE
                    }
                    _uiState.update { it.copy(syncStatus = status) }
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User actions
    // ─────────────────────────────────────────────────────────────────────────

    fun onDeleteExpense(expense: Expense) {
        viewModelScope.launch {
            deleteExpenseUseCase(expense.id).onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** Trigger an immediate one-off sync (called from the toolbar Sync icon). */
    fun onSyncNow() {
        syncExpensesUseCase()
    }

    /**
     * Manual retry after the sync has been marked FAILED.
     * Clears [SyncStateStore] failure state and enqueues a fresh one-off sync.
     */
    fun onRetrySync() {
        syncStateStore.clearFailure()
        SyncWorker.enqueue(workManager)
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

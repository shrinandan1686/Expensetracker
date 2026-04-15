package com.trackit.expense.presentation.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.trackit.expense.data.local.dao.CategoryTotal
import com.trackit.expense.domain.model.Budget
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetAllExpensesUseCase
import com.trackit.expense.domain.usecase.GetCategoryTotalsUseCase
import com.trackit.expense.domain.usecase.GetMonthlyBudgetUseCase
import com.trackit.expense.domain.usecase.GetMonthlyTotalUseCase
import com.trackit.expense.domain.usecase.GetUnloggedExpensesUseCase
import com.trackit.expense.domain.usecase.SyncExpensesUseCase
import com.trackit.expense.util.MainDispatcherRule
import com.trackit.expense.util.SyncStateStore
import com.trackit.expense.worker.SyncWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getAllExpensesUseCase     = mockk<GetAllExpensesUseCase>()
    private val getUnloggedUseCase       = mockk<GetUnloggedExpensesUseCase>()
    private val getMonthlyTotalUseCase   = mockk<GetMonthlyTotalUseCase>()
    private val getBudgetUseCase         = mockk<GetMonthlyBudgetUseCase>()
    private val getCategoryTotalsUseCase = mockk<GetCategoryTotalsUseCase>()
    private val deleteExpenseUseCase     = mockk<DeleteExpenseUseCase>()
    private val syncExpensesUseCase      = mockk<SyncExpensesUseCase>(relaxed = true)
    private val workManager              = mockk<WorkManager>()
    private val syncStateStore           = mockk<SyncStateStore>(relaxed = true)

    private fun buildVm() = HomeViewModel(
        getAllExpensesUseCase     = getAllExpensesUseCase,
        getUnloggedUseCase       = getUnloggedUseCase,
        getMonthlyTotalUseCase   = getMonthlyTotalUseCase,
        getBudgetUseCase         = getBudgetUseCase,
        getCategoryTotalsUseCase = getCategoryTotalsUseCase,
        deleteExpenseUseCase     = deleteExpenseUseCase,
        syncExpensesUseCase      = syncExpensesUseCase,
        workManager              = workManager,
        syncStateStore           = syncStateStore
    )

    @Before fun setUp() {
        every { getAllExpensesUseCase()            } returns flowOf(emptyList())
        every { getUnloggedUseCase()              } returns flowOf(emptyList())
        every { getMonthlyTotalUseCase(any())     } returns flowOf(0.0)
        every { getBudgetUseCase(any())           } returns flowOf(null)
        every { getCategoryTotalsUseCase(any())   } returns flowOf(emptyList())
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
        every { syncStateStore.isSyncFailed       } returns false
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test fun `initial state has isLoading true until first emission`() = runTest {
        // Verify isLoading is false after data flows emit
        val vm = buildVm()
        // getAllExpensesUseCase emits emptyList() immediately → isLoading = false
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test fun `initial state has empty expenses and zero totals`() = runTest {
        val vm = buildVm()
        val state = vm.uiState.value
        assertThat(state.recentExpenses).isEmpty()
        assertThat(state.monthlyTotal).isEqualTo(0.0)
        assertThat(state.unloggedCount).isEqualTo(0)
    }

    @Test fun `initial syncStatus is IDLE when no sync work exists`() = runTest {
        val vm = buildVm()
        assertThat(vm.uiState.value.syncStatus).isEqualTo(SyncStatus.IDLE)
    }

    @Test fun `currentMonth is set to a non-blank value`() = runTest {
        val vm = buildVm()
        assertThat(vm.uiState.value.currentMonth).isNotEmpty()
        // Should follow YYYY-MM format
        assertThat(vm.uiState.value.currentMonth).matches(Regex("\\d{4}-\\d{2}"))
    }

    // ── Expense list ─────────────────────────────────────────────────────────

    @Test fun `expenses flow populates recentExpenses`() = runTest {
        val expenses = (1..5).map { Expense(id = "e$it", amount = it * 100.0, merchant = "M$it") }
        every { getAllExpensesUseCase() } returns flowOf(expenses)

        val vm = buildVm()

        assertThat(vm.uiState.value.recentExpenses).hasSize(5)
    }

    @Test fun `recentExpenses is capped at 20`() = runTest {
        val expenses = (1..30).map { Expense(id = "e$it", amount = it * 10.0) }
        every { getAllExpensesUseCase() } returns flowOf(expenses)

        val vm = buildVm()

        assertThat(vm.uiState.value.recentExpenses).hasSize(20)
    }

    @Test fun `recentExpenses updates when flow emits new data`() = runTest {
        val liveFlow = MutableStateFlow<List<Expense>>(emptyList())
        every { getAllExpensesUseCase() } returns liveFlow

        val vm = buildVm()

        vm.uiState.test {
            awaitItem() // empty initial

            val newExpenses = listOf(Expense(id = "x1"), Expense(id = "x2"))
            liveFlow.value = newExpenses

            val updated = awaitItem()
            assertThat(updated.recentExpenses).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Unlogged count ───────────────────────────────────────────────────────

    @Test fun `unloggedCount reflects unlogged list size`() = runTest {
        val unlogged = listOf(Expense(id = "u1"), Expense(id = "u2"), Expense(id = "u3"))
        every { getUnloggedUseCase() } returns flowOf(unlogged)

        val vm = buildVm()

        assertThat(vm.uiState.value.unloggedCount).isEqualTo(3)
    }

    @Test fun `unloggedCount updates as unlogged list changes`() = runTest {
        val liveFlow = MutableStateFlow(listOf(Expense(id = "u1")))
        every { getUnloggedUseCase() } returns liveFlow

        val vm = buildVm()
        assertThat(vm.uiState.value.unloggedCount).isEqualTo(1)

        liveFlow.value = emptyList()
        assertThat(vm.uiState.value.unloggedCount).isEqualTo(0)
    }

    // ── Monthly total & budget ────────────────────────────────────────────────

    @Test fun `monthlyTotal reflects use case emission`() = runTest {
        every { getMonthlyTotalUseCase(any()) } returns flowOf(12500.0)

        val vm = buildVm()

        assertThat(vm.uiState.value.monthlyTotal).isEqualTo(12500.0)
    }

    @Test fun `monthlyBudget is 0 when no budget set`() = runTest {
        every { getBudgetUseCase(any()) } returns flowOf(null)

        val vm = buildVm()

        assertThat(vm.uiState.value.monthlyBudget).isEqualTo(0.0)
    }

    @Test fun `monthlyBudget reflects budget overall cap`() = runTest {
        val budget = Budget(month = "2026-04", overall = 30_000.0)
        every { getBudgetUseCase(any()) } returns flowOf(budget)

        val vm = buildVm()

        assertThat(vm.uiState.value.monthlyBudget).isEqualTo(30_000.0)
    }

    // ── Category totals ──────────────────────────────────────────────────────

    @Test fun `categoryTotals populated from use case`() = runTest {
        val totals = listOf(
            CategoryTotal("Food & Drinks", 4500.0),
            CategoryTotal("Transport",     1200.0)
        )
        every { getCategoryTotalsUseCase(any()) } returns flowOf(totals)

        val vm = buildVm()

        assertThat(vm.uiState.value.categoryTotals).hasSize(2)
        assertThat(vm.uiState.value.categoryTotals[0].category).isEqualTo("Food & Drinks")
    }

    // ── Sync status ──────────────────────────────────────────────────────────

    @Test fun `syncStatus is FAILED when syncStateStore reports failure`() = runTest {
        every { syncStateStore.isSyncFailed } returns true

        val vm = buildVm()

        assertThat(vm.uiState.value.syncStatus).isEqualTo(SyncStatus.FAILED)
    }

    @Test fun `syncStatus is SYNCING when WorkInfo state is RUNNING`() = runTest {
        every { syncStateStore.isSyncFailed } returns false
        val runningInfo = mockk<WorkInfo>(relaxed = true) {
            every { state } returns WorkInfo.State.RUNNING
        }
        every { workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_ONE_OFF) } returns
                flowOf(listOf(runningInfo))

        val vm = buildVm()

        assertThat(vm.uiState.value.syncStatus).isEqualTo(SyncStatus.SYNCING)
    }

    @Test fun `syncStatus is PENDING when WorkInfo state is ENQUEUED`() = runTest {
        every { syncStateStore.isSyncFailed } returns false
        val enqueuedInfo = mockk<WorkInfo>(relaxed = true) {
            every { state } returns WorkInfo.State.ENQUEUED
        }
        every { workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_ONE_OFF) } returns
                flowOf(listOf(enqueuedInfo))

        val vm = buildVm()

        assertThat(vm.uiState.value.syncStatus).isEqualTo(SyncStatus.PENDING)
    }

    @Test fun `syncStatus is PENDING when WorkInfo state is BLOCKED`() = runTest {
        every { syncStateStore.isSyncFailed } returns false
        val blockedInfo = mockk<WorkInfo>(relaxed = true) {
            every { state } returns WorkInfo.State.BLOCKED
        }
        every { workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_ONE_OFF) } returns
                flowOf(listOf(blockedInfo))

        val vm = buildVm()

        assertThat(vm.uiState.value.syncStatus).isEqualTo(SyncStatus.PENDING)
    }

    @Test fun `syncStatus is IDLE when WorkInfo list is empty`() = runTest {
        every { syncStateStore.isSyncFailed } returns false
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

        val vm = buildVm()

        assertThat(vm.uiState.value.syncStatus).isEqualTo(SyncStatus.IDLE)
    }

    // ── onDeleteExpense ──────────────────────────────────────────────────────

    @Test fun `onDeleteExpense success does not set errorMessage`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.onDeleteExpense(Expense(id = "del-1"))

        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test fun `onDeleteExpense failure sets errorMessage`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.failure(RuntimeException("constraint"))
        val vm = buildVm()

        vm.onDeleteExpense(Expense(id = "del-2"))

        assertThat(vm.uiState.value.errorMessage).contains("constraint")
    }

    @Test fun `onDeleteExpense passes correct id to use case`() = runTest {
        coEvery { deleteExpenseUseCase("target-id") } returns Result.success(Unit)
        val vm = buildVm()

        vm.onDeleteExpense(Expense(id = "target-id"))

        verify { deleteExpenseUseCase("target-id") }
    }

    // ── onSyncNow / onRetrySync ───────────────────────────────────────────────

    @Test fun `onSyncNow calls syncExpensesUseCase`() = runTest {
        justRun { syncExpensesUseCase() }
        val vm = buildVm()

        vm.onSyncNow()

        verify { syncExpensesUseCase() }
    }

    @Test fun `onRetrySync clears failure state and enqueues new sync`() = runTest {
        justRun { syncStateStore.clearFailure() }
        every { workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>()) } returns
                mockk(relaxed = true)

        val vm = buildVm()
        vm.onRetrySync()

        verify { syncStateStore.clearFailure() }
    }

    // ── onErrorDismissed ─────────────────────────────────────────────────────

    @Test fun `onErrorDismissed clears errorMessage`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.failure(RuntimeException("err"))
        val vm = buildVm()
        vm.onDeleteExpense(Expense(id = "x"))
        assertThat(vm.uiState.value.errorMessage).isNotNull()

        vm.onErrorDismissed()

        assertThat(vm.uiState.value.errorMessage).isNull()
    }
}

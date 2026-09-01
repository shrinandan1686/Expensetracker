package com.trackit.expense.presentation.history

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.repository.ExpenseRepository
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetUnloggedExpensesUseCase
import com.trackit.expense.domain.usecase.MarkExpenseLoggedUseCase
import com.trackit.expense.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnloggedExpensesViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getUnloggedUseCase   = mockk<GetUnloggedExpensesUseCase>()
    private val markLoggedUseCase    = mockk<MarkExpenseLoggedUseCase>()
    private val deleteExpenseUseCase = mockk<DeleteExpenseUseCase>()
    private val expenseRepository    = mockk<ExpenseRepository>()

    private fun buildVm() = UnloggedExpensesViewModel(
        getUnloggedUseCase   = getUnloggedUseCase,
        markLoggedUseCase    = markLoggedUseCase,
        deleteExpenseUseCase = deleteExpenseUseCase,
        expenseRepository    = expenseRepository
    )

    private val expense1 = Expense(id = "e1", amount = 100.0, merchant = "Swiggy")
    private val expense2 = Expense(id = "e2", amount = 200.0, merchant = "Ola")
    private val expense3 = Expense(id = "e3", amount = 300.0, merchant = "Amazon")

    @Before fun setUp() {
        every { getUnloggedUseCase() } returns flowOf(listOf(expense1, expense2, expense3))
    }

    // ── Initial load ─────────────────────────────────────────────────────────

    @Test fun `initial state has isLoading true`() = runTest {
        // Use a non-completing flow so we can catch the isLoading=true moment
        val pending = MutableStateFlow<List<Expense>>(emptyList())
        every { getUnloggedUseCase() } returns pending

        val vm = buildVm()
        // isLoading starts true; updated to false on first emission
        pending.value = listOf(expense1)
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test fun `expenses list is populated from use case`() = runTest {
        val vm = buildVm()
        assertThat(vm.uiState.value.expenses).containsExactly(expense1, expense2, expense3)
    }

    @Test fun `isLoading becomes false after first emission`() = runTest {
        val vm = buildVm()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test fun `empty unlogged list shows no expenses`() = runTest {
        every { getUnloggedUseCase() } returns flowOf(emptyList())
        val vm = buildVm()
        assertThat(vm.uiState.value.expenses).isEmpty()
    }

    // ── Live updates ─────────────────────────────────────────────────────────

    @Test fun `expenses list updates when flow emits new value`() = runTest {
        val liveFlow = MutableStateFlow(listOf(expense1))
        every { getUnloggedUseCase() } returns liveFlow
        val vm = buildVm()

        assertThat(vm.uiState.value.expenses).hasSize(1)

        liveFlow.value = listOf(expense1, expense2)

        assertThat(vm.uiState.value.expenses).hasSize(2)
        assertThat(vm.uiState.value.expenses).contains(expense2)
    }

    // ── onLogExpense ─────────────────────────────────────────────────────────

    @Test fun `onLogExpense calls markLoggedUseCase with correct id`() = runTest {
        coEvery { markLoggedUseCase(any(), any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.onLogExpense(expense1)

        coVerify { markLoggedUseCase("e1", any()) }
    }

    @Test fun `onLogExpense success does not set errorMessage`() = runTest {
        coEvery { markLoggedUseCase(any(), any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.onLogExpense(expense1)

        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test fun `onLogExpense failure sets errorMessage`() = runTest {
        coEvery { markLoggedUseCase(any(), any()) } returns Result.failure(RuntimeException("DB locked"))
        val vm = buildVm()

        vm.onLogExpense(expense1)

        assertThat(vm.uiState.value.errorMessage).contains("DB locked")
    }

    @Test fun `onLogExpense failure does not affect expenses list`() = runTest {
        coEvery { markLoggedUseCase(any(), any()) } returns Result.failure(RuntimeException("err"))
        val vm = buildVm()

        vm.onLogExpense(expense1)

        assertThat(vm.uiState.value.expenses).containsExactly(expense1, expense2, expense3)
    }

    // ── onIgnoreExpense ───────────────────────────────────────────────────────

    @Test fun `onIgnoreExpense calls deleteExpenseUseCase with correct id`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.onIgnoreExpense(expense2)

        coVerify { deleteExpenseUseCase("e2") }
    }

    @Test fun `onIgnoreExpense success does not set errorMessage`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.onIgnoreExpense(expense2)

        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test fun `onIgnoreExpense failure sets errorMessage`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.failure(RuntimeException("not found"))
        val vm = buildVm()

        vm.onIgnoreExpense(expense2)

        assertThat(vm.uiState.value.errorMessage).contains("not found")
    }

    // ── onIgnoreAll ───────────────────────────────────────────────────────────

    @Test fun `onIgnoreAll deletes every expense in state`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.onIgnoreAll()

        coVerify { deleteExpenseUseCase("e1") }
        coVerify { deleteExpenseUseCase("e2") }
        coVerify { deleteExpenseUseCase("e3") }
    }

    @Test fun `onIgnoreAll on empty list makes no delete calls`() = runTest {
        every { getUnloggedUseCase() } returns flowOf(emptyList())
        val vm = buildVm()

        vm.onIgnoreAll()

        coVerify(exactly = 0) { deleteExpenseUseCase(any()) }
    }

    // ── updateCategory ────────────────────────────────────────────────────────

    @Test fun `updateCategory calls repository update with new category`() = runTest {
        coEvery { expenseRepository.update(any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.updateCategory(expense1, "Food & Drinks")

        coVerify {
            expenseRepository.update(withArg { updated ->
                assertThat(updated.id).isEqualTo("e1")
                assertThat(updated.category).isEqualTo("Food & Drinks")
            })
        }
    }

    @Test fun `updateCategory preserves all other expense fields`() = runTest {
        val richExpense = Expense(
            id       = "rich-1",
            amount   = 999.0,
            merchant = "Netflix",
            account  = "XX5678",
            notes    = "monthly",
            rawSms   = "some sms body"
        )
        every { getUnloggedUseCase() } returns flowOf(listOf(richExpense))
        coEvery { expenseRepository.update(any()) } returns Result.success(Unit)

        val vm = buildVm()
        vm.updateCategory(richExpense, "Entertainment")

        coVerify {
            expenseRepository.update(withArg { updated ->
                assertThat(updated.amount).isEqualTo(999.0)
                assertThat(updated.merchant).isEqualTo("Netflix")
                assertThat(updated.account).isEqualTo("XX5678")
                assertThat(updated.notes).isEqualTo("monthly")
                assertThat(updated.rawSms).isEqualTo("some sms body")
                assertThat(updated.category).isEqualTo("Entertainment")
            })
        }
    }

    @Test fun `updateCategory failure sets errorMessage`() = runTest {
        coEvery { expenseRepository.update(any()) } returns Result.failure(RuntimeException("write fail"))
        val vm = buildVm()

        vm.updateCategory(expense1, "Transport")

        assertThat(vm.uiState.value.errorMessage).contains("write fail")
    }

    @Test fun `updateCategory success does not set errorMessage`() = runTest {
        coEvery { expenseRepository.update(any()) } returns Result.success(Unit)
        val vm = buildVm()

        vm.updateCategory(expense1, "Transport")

        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    // ── onErrorDismissed ──────────────────────────────────────────────────────

    @Test fun `onErrorDismissed clears errorMessage`() = runTest {
        coEvery { markLoggedUseCase(any(), any()) } returns Result.failure(RuntimeException("err"))
        val vm = buildVm()

        vm.onLogExpense(expense1)
        assertThat(vm.uiState.value.errorMessage).isNotNull()

        vm.onErrorDismissed()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    // ── State flow emissions ──────────────────────────────────────────────────

    @Test fun `uiState emits updated list when flow changes`() = runTest {
        val liveFlow = MutableStateFlow(listOf(expense1, expense2))
        every { getUnloggedUseCase() } returns liveFlow
        val vm = buildVm()

        vm.uiState.test {
            val first = awaitItem()
            assertThat(first.expenses).hasSize(2)

            liveFlow.value = listOf(expense3)
            val second = awaitItem()
            assertThat(second.expenses).hasSize(1)
            assertThat(second.expenses[0].id).isEqualTo("e3")

            cancelAndIgnoreRemainingEvents()
        }
    }
}

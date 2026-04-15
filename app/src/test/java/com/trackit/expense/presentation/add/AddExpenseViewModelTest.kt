package com.trackit.expense.presentation.add

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetExpenseByIdUseCase
import com.trackit.expense.overlay.ExpenseCategory
import com.trackit.expense.util.LocationHelper
import com.trackit.expense.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddExpenseViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val addExpenseUseCase    = mockk<AddExpenseUseCase>()
    private val getExpenseByIdUseCase = mockk<GetExpenseByIdUseCase>()
    private val deleteExpenseUseCase = mockk<DeleteExpenseUseCase>()
    private val locationHelper       = mockk<LocationHelper>(relaxed = true)

    private fun buildVm(savedStateMap: Map<String, String?> = emptyMap()) =
        AddExpenseViewModel(
            addExpenseUseCase    = addExpenseUseCase,
            getExpenseByIdUseCase = getExpenseByIdUseCase,
            deleteExpenseUseCase = deleteExpenseUseCase,
            locationHelper       = locationHelper,
            savedStateHandle     = SavedStateHandle(savedStateMap)
        )

    @Before fun setUp() {
        // Default: no expense to load, location returns null
        coEvery { getExpenseByIdUseCase(any()) } returns flowOf(null)
        coEvery { locationHelper.getCurrentLocation() } returns null
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test fun `initial state has empty amount and merchant`() = runTest {
        val vm = buildVm()
        val state = vm.uiState.value
        assertThat(state.amountInput).isEmpty()
        assertThat(state.merchant).isEmpty()
        assertThat(state.isSaved).isFalse()
        assertThat(state.isSubmitting).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test fun `savedState amount pre-fills amountInput`() = runTest {
        val vm = buildVm(mapOf("amount" to "450.0"))
        assertThat(vm.uiState.value.amountInput).isEqualTo("450.0")
    }

    @Test fun `savedState merchant pre-fills merchant field`() = runTest {
        val vm = buildVm(mapOf("merchant" to "Swiggy"))
        assertThat(vm.uiState.value.merchant).isEqualTo("Swiggy")
    }

    @Test fun `savedState merchant auto-infers category`() = runTest {
        val vm = buildVm(mapOf("merchant" to "Zomato"))
        assertThat(vm.uiState.value.selectedCategory).isEqualTo(ExpenseCategory.FOOD)
    }

    @Test fun `savedState account gets XX prefix`() = runTest {
        val vm = buildVm(mapOf("account" to "1234"))
        assertThat(vm.uiState.value.account).isEqualTo("XX1234")
    }

    // ── Duplicate warning ────────────────────────────────────────────────────

    @Test fun `isDuplicate true with duplicateId sets showDuplicateWarning`() = runTest {
        val vm = buildVm(mapOf("isDuplicate" to "true", "duplicateId" to "some-uuid"))
        assertThat(vm.uiState.value.showDuplicateWarning).isTrue()
        assertThat(vm.uiState.value.duplicateExpenseId).isEqualTo("some-uuid")
    }

    @Test fun `isDuplicate false does not show warning`() = runTest {
        val vm = buildVm(mapOf("isDuplicate" to "false", "duplicateId" to "some-uuid"))
        assertThat(vm.uiState.value.showDuplicateWarning).isFalse()
    }

    @Test fun `onDismissDuplicateWarning hides warning`() = runTest {
        val vm = buildVm(mapOf("isDuplicate" to "true", "duplicateId" to "uuid-1"))
        vm.onDismissDuplicateWarning()
        assertThat(vm.uiState.value.showDuplicateWarning).isFalse()
    }

    // ── Input handlers ───────────────────────────────────────────────────────

    @Test fun `onAmountChanged updates amountInput`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("1500")
        assertThat(vm.uiState.value.amountInput).isEqualTo("1500")
    }

    @Test fun `onAmountChanged clears errorMessage`() = runTest {
        val vm = buildVm()
        // Trigger an error first
        vm.onSaveExpense()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        // Now change amount
        vm.onAmountChanged("100")
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test fun `onMerchantChanged updates merchant`() = runTest {
        val vm = buildVm()
        vm.onMerchantChanged("Netflix")
        assertThat(vm.uiState.value.merchant).isEqualTo("Netflix")
    }

    @Test fun `onCategorySelected updates selectedCategory`() = runTest {
        val vm = buildVm()
        vm.onCategorySelected(ExpenseCategory.TRANSPORT)
        assertThat(vm.uiState.value.selectedCategory).isEqualTo(ExpenseCategory.TRANSPORT)
    }

    @Test fun `onNotesChanged updates notes`() = runTest {
        val vm = buildVm()
        vm.onNotesChanged("team lunch")
        assertThat(vm.uiState.value.notes).isEqualTo("team lunch")
    }

    // ── Save validation ──────────────────────────────────────────────────────

    @Test fun `onSaveExpense with empty amount sets error`() = runTest {
        val vm = buildVm()
        vm.onSaveExpense()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.errorMessage).contains("valid amount")
    }

    @Test fun `onSaveExpense with zero amount sets error`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("0")
        vm.onMerchantChanged("Swiggy")
        vm.onSaveExpense()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test fun `onSaveExpense with negative amount sets error`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("-100")
        vm.onMerchantChanged("Swiggy")
        vm.onSaveExpense()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test fun `onSaveExpense with blank merchant sets error`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("500")
        vm.onMerchantChanged("")
        vm.onSaveExpense()
        assertThat(vm.uiState.value.errorMessage).contains("Merchant")
    }

    @Test fun `onSaveExpense with comma-formatted amount parses correctly`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("1,200")
        vm.onMerchantChanged("Amazon")
        coEvery { addExpenseUseCase(any()) } returns Result.success("id-1")

        vm.onSaveExpense()

        val slot = slot<Expense>()
        coVerify { addExpenseUseCase(capture(slot)) }
        assertThat(slot.captured.amount).isEqualTo(1200.0)
    }

    // ── Successful save ──────────────────────────────────────────────────────

    @Test fun `onSaveExpense success sets isSaved true`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("500")
        vm.onMerchantChanged("Swiggy")
        coEvery { addExpenseUseCase(any()) } returns Result.success("new-id")

        vm.uiState.test {
            awaitItem() // initial
            vm.onSaveExpense()
            val submitting = awaitItem()
            assertThat(submitting.isSubmitting).isTrue()
            val saved = awaitItem()
            assertThat(saved.isSaved).isTrue()
            assertThat(saved.isSubmitting).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `onSaveExpense saved expense has isLogged true`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("250")
        vm.onMerchantChanged("Zomato")
        coEvery { addExpenseUseCase(any()) } returns Result.success("id-1")

        val slot = slot<Expense>()
        coEvery { addExpenseUseCase(capture(slot)) } returns Result.success("id-1")
        vm.onSaveExpense()

        assertThat(slot.captured.isLogged).isTrue()
        assertThat(slot.captured.amount).isEqualTo(250.0)
        assertThat(slot.captured.merchant).isEqualTo("Zomato")
    }

    // ── Save failure ─────────────────────────────────────────────────────────

    @Test fun `onSaveExpense failure sets errorMessage`() = runTest {
        val vm = buildVm()
        vm.onAmountChanged("500")
        vm.onMerchantChanged("Swiggy")
        coEvery { addExpenseUseCase(any()) } returns Result.failure(RuntimeException("DB error"))

        vm.onSaveExpense()

        assertThat(vm.uiState.value.errorMessage).contains("DB error")
        assertThat(vm.uiState.value.isSubmitting).isFalse()
    }

    // ── Load existing expense ────────────────────────────────────────────────

    @Test fun `expenseId in savedState loads existing expense into form`() = runTest {
        val existing = Expense(
            id       = "existing-id",
            amount   = 750.0,
            merchant = "Netflix",
            account  = "XX9876",
            notes    = "monthly sub",
            isLogged = true
        )
        coEvery { getExpenseByIdUseCase("existing-id") } returns flowOf(existing)

        val vm = buildVm(mapOf("expenseId" to "existing-id"))

        val state = vm.uiState.value
        assertThat(state.amountInput).isEqualTo("750.0")
        assertThat(state.merchant).isEqualTo("Netflix")
        assertThat(state.account).isEqualTo("XX9876")
        assertThat(state.notes).isEqualTo("monthly sub")
    }

    // ── Discard duplicate ────────────────────────────────────────────────────

    @Test fun `onDiscardDuplicate deletes expense and sets isSaved`() = runTest {
        coEvery { deleteExpenseUseCase(any()) } returns Result.success(Unit)

        val vm = buildVm(mapOf(
            "isDuplicate" to "true",
            "duplicateId" to "dup-id",
            "expenseId"   to "dup-id"
        ))
        vm.onDiscardDuplicate()

        coVerify { deleteExpenseUseCase("dup-id") }
        assertThat(vm.uiState.value.isSaved).isTrue()
    }

    // ── Error dismissed ──────────────────────────────────────────────────────

    @Test fun `onErrorDismissed clears errorMessage`() = runTest {
        val vm = buildVm()
        vm.onSaveExpense() // triggers "valid amount" error
        assertThat(vm.uiState.value.errorMessage).isNotNull()

        vm.onErrorDismissed()

        assertThat(vm.uiState.value.errorMessage).isNull()
    }
}

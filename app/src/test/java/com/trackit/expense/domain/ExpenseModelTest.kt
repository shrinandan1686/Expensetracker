package com.trackit.expense.domain

import com.google.common.truth.Truth.assertThat
import com.trackit.expense.domain.model.Expense
import org.junit.Test

/**
 * Tests for [Expense] domain model — computed properties and default values.
 */
class ExpenseModelTest {

    // ── amountDisplay ────────────────────────────────────────────────────────

    @Test fun `amountDisplay formats whole rupees with two decimal places`() {
        assertThat(Expense(amount = 500.0).amountDisplay).isEqualTo("₹500.00")
    }

    @Test fun `amountDisplay formats paise correctly`() {
        assertThat(Expense(amount = 99.50).amountDisplay).isEqualTo("₹99.50")
    }

    @Test fun `amountDisplay formats large lakh amounts`() {
        assertThat(Expense(amount = 100_000.0).amountDisplay).isEqualTo("₹100000.00")
    }

    @Test fun `amountDisplay formats zero amount`() {
        assertThat(Expense(amount = 0.0).amountDisplay).isEqualTo("₹0.00")
    }

    @Test fun `amountDisplay rounds to two decimal places`() {
        // 1/3 = 0.333... → should format as ₹0.33
        assertThat(Expense(amount = 1.0 / 3.0).amountDisplay).isEqualTo("₹0.33")
    }

    // ── isUpi ────────────────────────────────────────────────────────────────

    @Test fun `isUpi is true for VPA merchant containing @`() {
        assertThat(Expense(merchant = "swiggy@icici").isUpi).isTrue()
    }

    @Test fun `isUpi is true for UPI keyword in category`() {
        assertThat(Expense(merchant = "Amazon", category = "UPI Payment").isUpi).isTrue()
    }

    @Test fun `isUpi is false for non-UPI expense`() {
        assertThat(Expense(merchant = "Amazon", category = "Shopping").isUpi).isFalse()
    }

    @Test fun `isUpi check is case-insensitive for category`() {
        assertThat(Expense(merchant = "Swiggy", category = "upi").isUpi).isTrue()
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test fun `default id is a non-blank UUID`() {
        val e = Expense()
        assertThat(e.id).isNotEmpty()
        assertThat(e.id).hasLength(36) // UUID format: 8-4-4-4-12
    }

    @Test fun `two distinct Expense() instances have different ids`() {
        assertThat(Expense().id).isNotEqualTo(Expense().id)
    }

    @Test fun `default isLogged is false`() {
        assertThat(Expense().isLogged).isFalse()
    }

    @Test fun `default isSynced is false`() {
        assertThat(Expense().isSynced).isFalse()
    }

    @Test fun `default category is Others`() {
        assertThat(Expense().category).isEqualTo("Others")
    }

    // ── copy semantics ───────────────────────────────────────────────────────

    @Test fun `copy with new category preserves all other fields`() {
        val original = Expense(
            amount = 250.0,
            merchant = "Swiggy",
            account = "XX1234",
            notes = "lunch"
        )
        val updated = original.copy(category = "Food & Drinks")
        assertThat(updated.amount).isEqualTo(250.0)
        assertThat(updated.merchant).isEqualTo("Swiggy")
        assertThat(updated.account).isEqualTo("XX1234")
        assertThat(updated.notes).isEqualTo("lunch")
        assertThat(updated.category).isEqualTo("Food & Drinks")
        assertThat(updated.id).isEqualTo(original.id)
    }

    @Test fun `loggedAt is null by default`() {
        assertThat(Expense().loggedAt).isNull()
    }

    @Test fun `notes is null by default`() {
        assertThat(Expense().notes).isNull()
    }
}

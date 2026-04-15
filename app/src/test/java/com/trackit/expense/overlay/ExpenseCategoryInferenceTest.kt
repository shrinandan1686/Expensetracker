package com.trackit.expense.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ExpenseCategory.inferFrom] — keyword-to-category mapping.
 *
 * Each test covers one real-world merchant name, a VPA handle, and a
 * case-variant to verify [CASE_INSENSITIVE] is working.
 */
class ExpenseCategoryInferenceTest {

    // ── Known category keywords ──────────────────────────────────────────────

    @Test fun `swiggy maps to FOOD`() =
        assertThat(ExpenseCategory.inferFrom("swiggy")).isEqualTo(ExpenseCategory.FOOD)

    @Test fun `zomato maps to FOOD`() =
        assertThat(ExpenseCategory.inferFrom("Zomato")).isEqualTo(ExpenseCategory.FOOD)

    @Test fun `SWIGGY uppercase maps to FOOD`() =
        assertThat(ExpenseCategory.inferFrom("SWIGGY")).isEqualTo(ExpenseCategory.FOOD)

    @Test fun `swiggy VPA handle maps to FOOD`() =
        assertThat(ExpenseCategory.inferFrom("swiggy@icici")).isEqualTo(ExpenseCategory.FOOD)

    @Test fun `ola maps to TRANSPORT`() =
        assertThat(ExpenseCategory.inferFrom("ola")).isEqualTo(ExpenseCategory.TRANSPORT)

    @Test fun `uber maps to TRANSPORT`() =
        assertThat(ExpenseCategory.inferFrom("Uber")).isEqualTo(ExpenseCategory.TRANSPORT)

    @Test fun `rapido maps to TRANSPORT`() =
        assertThat(ExpenseCategory.inferFrom("Rapido")).isEqualTo(ExpenseCategory.TRANSPORT)

    @Test fun `irctc maps to TRANSPORT`() =
        assertThat(ExpenseCategory.inferFrom("IRCTC")).isEqualTo(ExpenseCategory.TRANSPORT)

    @Test fun `amazon maps to SHOPPING`() =
        assertThat(ExpenseCategory.inferFrom("Amazon")).isEqualTo(ExpenseCategory.SHOPPING)

    @Test fun `flipkart maps to SHOPPING`() =
        assertThat(ExpenseCategory.inferFrom("Flipkart")).isEqualTo(ExpenseCategory.SHOPPING)

    @Test fun `myntra maps to SHOPPING`() =
        assertThat(ExpenseCategory.inferFrom("MYNTRA")).isEqualTo(ExpenseCategory.SHOPPING)

    @Test fun `bigbasket maps to GROCERIES`() =
        assertThat(ExpenseCategory.inferFrom("BigBasket")).isEqualTo(ExpenseCategory.GROCERIES)

    @Test fun `dmart maps to GROCERIES`() =
        assertThat(ExpenseCategory.inferFrom("DMart")).isEqualTo(ExpenseCategory.GROCERIES)

    @Test fun `airtel maps to BILLS`() =
        assertThat(ExpenseCategory.inferFrom("Airtel")).isEqualTo(ExpenseCategory.BILLS)

    @Test fun `jio maps to BILLS`() =
        assertThat(ExpenseCategory.inferFrom("JIO")).isEqualTo(ExpenseCategory.BILLS)

    @Test fun `electricity bill maps to BILLS`() =
        assertThat(ExpenseCategory.inferFrom("electricity")).isEqualTo(ExpenseCategory.BILLS)

    @Test fun `apollo maps to HEALTHCARE`() =
        assertThat(ExpenseCategory.inferFrom("Apollo")).isEqualTo(ExpenseCategory.HEALTHCARE)

    @Test fun `medplus maps to HEALTHCARE`() =
        assertThat(ExpenseCategory.inferFrom("medplus")).isEqualTo(ExpenseCategory.HEALTHCARE)

    @Test fun `netflix maps to ENTERTAINMENT`() =
        assertThat(ExpenseCategory.inferFrom("Netflix")).isEqualTo(ExpenseCategory.ENTERTAINMENT)

    @Test fun `spotify maps to ENTERTAINMENT`() =
        assertThat(ExpenseCategory.inferFrom("Spotify")).isEqualTo(ExpenseCategory.ENTERTAINMENT)

    @Test fun `pvr maps to ENTERTAINMENT`() =
        assertThat(ExpenseCategory.inferFrom("PVR")).isEqualTo(ExpenseCategory.ENTERTAINMENT)

    @Test fun `udemy maps to EDUCATION`() =
        assertThat(ExpenseCategory.inferFrom("Udemy")).isEqualTo(ExpenseCategory.EDUCATION)

    @Test fun `byju maps to EDUCATION`() =
        assertThat(ExpenseCategory.inferFrom("BYJUs")).isEqualTo(ExpenseCategory.EDUCATION)

    @Test fun `indigo maps to TRAVEL`() =
        assertThat(ExpenseCategory.inferFrom("Indigo")).isEqualTo(ExpenseCategory.TRAVEL)

    @Test fun `oyo maps to TRAVEL`() =
        assertThat(ExpenseCategory.inferFrom("OYO")).isEqualTo(ExpenseCategory.TRAVEL)

    @Test fun `makemytrip maps to TRAVEL`() =
        assertThat(ExpenseCategory.inferFrom("MakeMyTrip")).isEqualTo(ExpenseCategory.TRAVEL)

    @Test fun `zerodha maps to INVESTMENTS`() =
        assertThat(ExpenseCategory.inferFrom("zerodha")).isEqualTo(ExpenseCategory.INVESTMENTS)

    @Test fun `groww maps to INVESTMENTS`() =
        assertThat(ExpenseCategory.inferFrom("Groww")).isEqualTo(ExpenseCategory.INVESTMENTS)

    // ── Fallback ─────────────────────────────────────────────────────────────

    @Test fun `unknown merchant maps to OTHERS`() =
        assertThat(ExpenseCategory.inferFrom("randommerchant123")).isEqualTo(ExpenseCategory.OTHERS)

    @Test fun `empty string maps to OTHERS`() =
        assertThat(ExpenseCategory.inferFrom("")).isEqualTo(ExpenseCategory.OTHERS)

    @Test fun `null maps to OTHERS`() =
        assertThat(ExpenseCategory.inferFrom(null)).isEqualTo(ExpenseCategory.OTHERS)

    // ── label property ───────────────────────────────────────────────────────

    @Test fun `label contains emoji and displayName`() {
        val cat = ExpenseCategory.FOOD
        assertThat(cat.label).contains(cat.emoji)
        assertThat(cat.label).contains(cat.displayName)
    }

    // ── All entries have non-blank displayName and emoji ─────────────────────

    @Test fun `all categories have non-blank displayName`() {
        ExpenseCategory.entries.forEach { cat ->
            assertThat(cat.displayName).isNotEmpty()
        }
    }

    @Test fun `all categories have non-blank emoji`() {
        ExpenseCategory.entries.forEach { cat ->
            assertThat(cat.emoji).isNotEmpty()
        }
    }
}

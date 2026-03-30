package com.trackit.expense.domain.model

import java.util.UUID

/**
 * Domain model representing a monthly spending budget.
 *
 * @property id               Client-generated UUID.
 * @property month            ISO month string, e.g. "2026-03" (YYYY-MM). Used as the
 *                            natural key for all month-based budget lookups.
 * @property overall          Total monthly budget cap in INR.
 * @property categoryBudgets  Per-category budget limits keyed by category label
 *                            (e.g. "Food & Drinks" → 5000.0). Serialised as a JSON
 *                            string in the Room entity via [BudgetEntity.categoryBudgets].
 */
data class Budget(
    val id: String                           = UUID.randomUUID().toString(),
    val month: String                        = "",
    val overall: Double                      = 0.0,
    val categoryBudgets: Map<String, Double> = emptyMap()
) {
    /**
     * Returns the budget cap for [category], or 0.0 if no specific limit is set.
     */
    fun budgetFor(category: String): Double = categoryBudgets[category] ?: 0.0

    /**
     * Returns true if a per-category budget exists for [category].
     */
    fun hasCategoryBudget(category: String): Boolean = categoryBudgets.containsKey(category)
}

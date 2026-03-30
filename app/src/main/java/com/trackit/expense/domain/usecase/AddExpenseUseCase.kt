package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.repository.ExpenseRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case: validate and persist a new expense (offline-first).
 *
 * ## Validation
 * - [Expense.amount] must be > 0.
 * - At least one of [Expense.merchant] or [Expense.rawSms] must be non-blank
 *   (ensures the record has enough context to be useful).
 *
 * ## ID
 * If the caller provides an empty [Expense.id], a new UUID is generated here.
 * This handles cases where the caller builds the `Expense` without knowing the ID
 * in advance (e.g., the overlay popup before the ViewModel assigns one).
 *
 * @return [Result.success] containing the UUID String on success, or
 *         [Result.failure] with [IllegalArgumentException] on validation failure.
 */
class AddExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(expense: Expense): Result<String> {
        // ── Validation ────────────────────────────────────────────────────────
        if (expense.amount <= 0.0) {
            return Result.failure(
                IllegalArgumentException("amount must be > 0, was ${expense.amount}")
            )
        }
        if (expense.merchant.isBlank() && expense.rawSms.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Either merchant or rawSms must be non-blank")
            )
        }

        // ── Ensure ID ─────────────────────────────────────────────────────────
        val finalExpense = if (expense.id.isBlank()) {
            expense.copy(id = UUID.randomUUID().toString())
        } else {
            expense
        }

        // ── Persist (offline-first — Room write + WorkManager enqueue) ────────
        return repository.save(finalExpense)
    }
}

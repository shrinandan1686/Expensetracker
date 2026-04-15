package com.trackit.expense.domain.usecase

import android.net.Uri
import com.trackit.expense.domain.repository.ExpenseRepository
import com.trackit.expense.util.ExportHelper
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Exports all (or a single month's) expenses to a CSV file and returns its content:// URI.
 *
 * @param month Optional "YYYY-MM" string. When null, all logged expenses are exported.
 */
class ExportDataUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val exportHelper: ExportHelper
) {
    suspend operator fun invoke(month: String? = null): Result<Uri> = runCatching {
        val expenses = if (month != null) {
            expenseRepository.getByMonth(month).firstOrNull() ?: emptyList()
        } else {
            expenseRepository.getAll().firstOrNull() ?: emptyList()
        }
        // Only export logged (user-confirmed) expenses
        val logged = expenses.filter { it.isLogged }
        exportHelper.exportToCSV(logged, month)
    }
}

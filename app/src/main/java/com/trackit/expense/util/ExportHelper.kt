package com.trackit.expense.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.trackit.expense.domain.model.Expense
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a list of [Expense] objects to a CSV file in the app's external documents directory
 * and returns a shareable [Uri] via FileProvider.
 *
 * ## CSV columns
 * Date, Time, Merchant, Category, Amount, Account, Notes, Source
 *
 * ## Source column
 * "SMS Auto-detected" when [Expense.rawSms] is non-blank; "Manual" otherwise.
 */
@Singleton
class ExportHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Export [expenses] to a CSV file and return its content:// URI.
     *
     * @param expenses  List of domain expenses to export.
     * @param month     Optional "YYYY-MM" label appended to the filename.
     */
    fun exportToCSV(expenses: List<Expense>, month: String? = null): Uri {
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: throw IllegalStateException("External files dir is unavailable")

        val suffix = month?.replace("-", "") ?: fileNameFormat.format(Date())
        val file = File(docsDir, "trackit_expenses_$suffix.csv")

        file.bufferedWriter().use { writer ->
            // Header
            writer.write("Date,Time,Merchant,Category,Amount,Account,Notes,Source\n")
            // Rows
            expenses.forEach { e ->
                val date = dateFormat.format(Date(e.transactionAt))
                val time = timeFormat.format(Date(e.transactionAt))
                writer.write(
                    "${date.csv()},${time.csv()},${e.merchant.csv()}," +
                    "${e.category.csv()},${e.amount}," +
                    "${e.account.csv()},${(e.notes ?: "").csv()}," +
                    "${if (e.rawSms.isNotBlank()) "SMS Auto-detected" else "Manual"}\n"
                )
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Escape a CSV field: wrap in double-quotes and double any internal quotes.
     */
    private fun String.csv(): String {
        val escaped = replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}

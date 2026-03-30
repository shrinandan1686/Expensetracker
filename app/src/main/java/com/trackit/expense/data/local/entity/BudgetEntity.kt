package com.trackit.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the **budgets** table.
 *
 * A budget is scoped to a single calendar month.  [month] has a UNIQUE index
 * so there can only ever be one budget row per month — [BudgetDao.upsert] uses
 * [OnConflictStrategy.REPLACE] to enforce this.
 *
 * [categoryBudgets] stores a JSON-serialised [Map<String, Double>] where keys
 * are category labels (e.g. "Food & Drinks") and values are INR budget caps.
 * Serialisation/deserialisation is done in the repository layer using Gson so
 * Room never needs a custom TypeConverter.
 *
 * @property id               Client-generated UUID.
 * @property month            ISO month string: "YYYY-MM" (e.g. "2026-03").
 * @property overall          Total monthly spending cap in INR.
 * @property categoryBudgets  JSON string — `{"Food & Drinks":5000.0,"Transport":2000.0}`.
 */
@Entity(
    tableName = "budgets",
    indices = [Index("month", unique = true)]
)
data class BudgetEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "month")
    val month: String = "",

    @ColumnInfo(name = "overall")
    val overall: Double = 0.0,

    @ColumnInfo(name = "category_budgets")
    val categoryBudgets: String = "{}"   // JSON string
)

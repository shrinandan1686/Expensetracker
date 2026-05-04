package com.trackit.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "splits",
    indices = [Index("group_id")]
)
data class SplitEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "group_id")
    val groupId: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "total_amount")
    val totalAmount: Double,

    @ColumnInfo(name = "paid_by")
    val paidBy: String,

    // JSON-serialised List<SplitParticipant>
    @ColumnInfo(name = "participants_json")
    val participantsJson: String = "[]",

    @ColumnInfo(name = "expense_id")
    val expenseId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

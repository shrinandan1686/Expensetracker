package com.trackit.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Supported account types in TrackIt.
 */
enum class AccountType {
    CREDIT, DEBIT, UPI, WALLET
}

/**
 * Room entity for the **accounts** table.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "last_4")
    val last4: String,

    @ColumnInfo(name = "type")
    val type: AccountType,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#6200EE",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

package com.trackit.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the **expenses** table.
 *
 * ## Schema v3 changes from v2
 * - [id] is now a **UUID String** (client-generated) instead of auto-increment Long.
 *   This enables offline record creation before any server round-trip.
 * - [amount] is **Double (INR)** instead of Long (paise). Paise conversion was an
 *   implementation detail that leaked into every layer; INR Double is simpler.
 * - [category] is **denormalised String** — no FK to a categories table.
 * - [rawSms] is stored on the entity for audit and re-parsing.
 * - [transactionAt] replaces `timestamp` (more descriptive).
 * - [loggedAt] is new — records when the user reviewed the overlay popup.
 *
 * ## Indices
 * - [transactionAt]: primary sort key for all list queries.
 * - [isSynced]: filtered rapidly by [SyncWorker] on every sync pass.
 * - [isLogged]: filtered for the "Unreviewed" list.
 * - [category]: supports per-category aggregation queries.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index("transaction_at"),
        Index("is_synced"),
        Index("is_logged"),
        Index("category")
    ]
)
data class ExpenseEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "amount")
    val amount: Double = 0.0,

    @ColumnInfo(name = "merchant")
    val merchant: String = "",

    @ColumnInfo(name = "category")
    val category: String = "Others",

    @ColumnInfo(name = "account")
    val account: String = "",

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "raw_sms")
    val rawSms: String = "",

    @ColumnInfo(name = "is_logged")
    val isLogged: Boolean = false,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "transaction_at")
    val transactionAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "logged_at")
    val loggedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "location_address")
    val locationAddress: String? = null
)

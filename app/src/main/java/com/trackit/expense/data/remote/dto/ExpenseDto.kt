package com.trackit.expense.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.trackit.expense.data.local.entity.ExpenseEntity

// ─────────────────────────────────────────────────────────────────────────────
// Expense DTO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Network DTO for a single expense sent to / received from the Cloudflare API.
 *
 * All fields mirror [com.trackit.expense.data.local.entity.ExpenseEntity] so
 * the sync worker can map them 1:1 without lossy conversion.
 *
 * [id] is the client-generated UUID — the server uses it as the idempotency key,
 * so re-posting the same expense is safe.
 */
data class ExpenseDto(
    @SerializedName("id")             val id: String,
    @SerializedName("amount")         val amount: Double,
    @SerializedName("merchant")       val merchant: String,
    @SerializedName("category")       val category: String,
    @SerializedName("account")        val account: String,
    @SerializedName("notes")          val notes: String?,
    @SerializedName("raw_sms")        val rawSms: String,
    @SerializedName("is_logged")      val isLogged: Boolean,
    @SerializedName("transaction_at") val transactionAt: Long,
    @SerializedName("logged_at")      val loggedAt: Long?,
    @SerializedName("created_at")     val createdAt: Long,
    @SerializedName("updated_at")     val updatedAt: Long = createdAt,
    /**
     * Soft-delete tombstone. Sent so the server learns about deletions, and
     * received so a deletion made on another device is applied locally.
     */
    @SerializedName("is_deleted")     val isDeleted: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// Sync request / response
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Request body for `POST /api/sync`.
 * Wraps a batch of [ExpenseDto] records in a named JSON envelope so the server
 * can version the payload schema independently.
 */
data class SyncRequestDto(
    @SerializedName("expenses")     val expenses: List<ExpenseDto>,
    @SerializedName("device_id")    val deviceId: String = "",  // TODO: populate with stable device ID
    @SerializedName("client_ts")    val clientTs: Long = System.currentTimeMillis()
)

/**
 * Response from `POST /api/sync`.
 *
 * [syncedIds] — IDs the server has accepted and persisted.
 * [failedIds]  — IDs the server rejected (validation errors, duplicates, etc.).
 *                These are **not** marked as synced; the worker will retry them.
 * [message]    — Human-readable status string for logging.
 */
data class SyncResponseDto(
    @SerializedName("synced_ids")   val syncedIds: List<String> = emptyList(),
    @SerializedName("failed_ids")   val failedIds: List<String> = emptyList(),
    @SerializedName("message")      val message: String         = ""
)

/**
 * Response from `GET /api/expenses`.
 *
 * [documents] mirrors the MongoDB driver's own result shape, which is what the
 * Worker returns. Tombstoned expenses are included when `include_deleted=true`
 * so a pull can apply deletions made on another device.
 */
data class ExpenseListResponseDto(
    @SerializedName("documents") val documents: List<ExpenseDto> = emptyList()
)

/**
 * Maps a server expense onto a Room entity for the sync pull.
 *
 * @param isSynced marks the row as already confirmed by the server, so the next
 *        push does not immediately send back what was just pulled.
 */
fun ExpenseDto.toEntity(isSynced: Boolean = true): ExpenseEntity = ExpenseEntity(
    id            = id,
    amount        = amount,
    merchant      = merchant,
    category      = category,
    account       = account,
    notes         = notes,
    rawSms        = rawSms,
    isLogged      = isLogged,
    isSynced      = isSynced,
    transactionAt = transactionAt,
    loggedAt      = loggedAt,
    createdAt     = createdAt,
    updatedAt     = updatedAt,
    isDeleted     = isDeleted
)

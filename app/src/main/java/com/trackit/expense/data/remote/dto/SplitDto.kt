package com.trackit.expense.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SplitDto(
    @SerializedName("_id") val id: String?,
    val groupId: String?,
    val description: String?,
    val totalAmount: Double?,
    val paidBy: String?,
    val participants: List<SplitParticipantDto>?,
    val expenseId: String?,
    val createdAt: Long?
)

data class SplitParticipantDto(
    val userId: String?,
    val amount: Double?,
    val isPaid: Boolean?
)

data class CreateSplitRequestDto(
    val description: String,
    val totalAmount: Double,
    val paidBy: String,
    val participants: List<SplitParticipantDto>,
    val expenseId: String? = null
)

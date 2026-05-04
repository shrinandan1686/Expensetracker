package com.trackit.expense.data.remote.dto

data class BalanceDto(
    val from: String?,
    val to: String?,
    val amount: Double?
)

data class BalancesResponseDto(
    val balances: List<BalanceDto>?,
    val net: Map<String, Double>?
)

data class CreateSettlementRequestDto(
    val toUserId: String,
    val amount: Double
)

data class SettlementResponseDto(
    @com.google.gson.annotations.SerializedName("_id") val id: String?
)

package com.trackit.expense.domain.model

data class Balance(
    val fromUserId: String,
    val toUserId: String,
    val amount: Double
)

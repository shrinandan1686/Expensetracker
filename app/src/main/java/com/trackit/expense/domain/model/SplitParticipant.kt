package com.trackit.expense.domain.model

data class SplitParticipant(
    val userId: String,
    val amount: Double,
    val isPaid: Boolean
)

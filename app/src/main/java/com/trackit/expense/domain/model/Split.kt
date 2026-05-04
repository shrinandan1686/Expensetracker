package com.trackit.expense.domain.model

data class Split(
    val id: String,
    val groupId: String,
    val description: String,
    val totalAmount: Double,
    val paidBy: String,
    val participants: List<SplitParticipant>,
    val expenseId: String?,
    val createdAt: Long
)

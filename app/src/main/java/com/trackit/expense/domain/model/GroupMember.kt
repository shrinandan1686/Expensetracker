package com.trackit.expense.domain.model

data class GroupMember(
    val userId: String,
    val name: String,
    val phone: String,
    val upiId: String?
)

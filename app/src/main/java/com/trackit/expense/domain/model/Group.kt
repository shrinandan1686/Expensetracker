package com.trackit.expense.domain.model

data class Group(
    val id: String,
    val name: String,
    val emoji: String?,
    val createdBy: String,
    val members: List<GroupMember>,
    val createdAt: Long
)

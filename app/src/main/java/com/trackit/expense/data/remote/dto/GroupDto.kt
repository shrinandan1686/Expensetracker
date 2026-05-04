package com.trackit.expense.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GroupDto(
    @SerializedName("_id") val id: String?,
    val name: String?,
    val emoji: String?,
    val createdBy: String?,
    val members: List<GroupMemberDto>?,
    val createdAt: Long?
)

data class GroupMemberDto(
    val userId: String?,
    val name: String?,
    val phone: String?,
    val upiId: String?
)

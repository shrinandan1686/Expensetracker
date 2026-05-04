package com.trackit.expense.domain.model

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String,
    val upiId: String
)

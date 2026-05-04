package com.trackit.expense.domain.repository

import com.trackit.expense.domain.model.UserProfile

interface UserRepository {
    suspend fun getProfile(): Result<UserProfile>
    suspend fun updateProfile(name: String, upiId: String): Result<Unit>
}

package com.trackit.expense.domain.repository

import com.trackit.expense.domain.model.UserProfile

interface UserRepository {
    suspend fun getProfile(): Result<UserProfile>
    suspend fun updateProfile(name: String, upiId: String): Result<Unit>

    /**
     * Deletes the account: server-side data, the local database, and the Firebase
     * user. Irreversible.
     */
    suspend fun deleteAccount(): Result<Unit>
}

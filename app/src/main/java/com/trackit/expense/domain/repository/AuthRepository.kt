package com.trackit.expense.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?
    val isAuthenticated: Boolean
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    suspend fun signOut()
    fun authStateFlow(): Flow<Boolean>
}

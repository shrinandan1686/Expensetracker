package com.trackit.expense.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.trackit.expense.data.remote.api.TrackItApiService
import com.trackit.expense.domain.model.UserProfile
import com.trackit.expense.domain.repository.UserRepository
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val apiService: TrackItApiService,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    override suspend fun getProfile(): Result<UserProfile> {
        val firebaseUser = firebaseAuth.currentUser
        val fallback = UserProfile(
            uid      = firebaseUser?.uid ?: "",
            name     = firebaseUser?.displayName ?: "",
            email    = firebaseUser?.email ?: "",
            photoUrl = firebaseUser?.photoUrl?.toString() ?: "",
            upiId    = ""
        )
        return runCatching {
            val response = apiService.getProfile()
            // 404 means user record not yet bootstrapped — use Firebase data
            if (response.code() == 404) return Result.success(fallback)
            if (!response.isSuccessful) error("Failed to fetch profile: ${response.code()}")
            val dto = response.body() ?: return Result.success(fallback)
            UserProfile(
                uid      = dto.uid ?: fallback.uid,
                name     = dto.name?.takeIf { it.isNotBlank() } ?: fallback.name,
                email    = dto.email?.takeIf { it.isNotBlank() } ?: fallback.email,
                photoUrl = dto.photoUrl ?: fallback.photoUrl,
                upiId    = dto.upiId ?: ""
            )
        }.recover { fallback }
    }

    override suspend fun updateProfile(name: String, upiId: String): Result<Unit> = runCatching {
        val response = apiService.updateProfile(mapOf("name" to name, "upiId" to upiId))
        if (!response.isSuccessful) error("Failed to update profile: ${response.code()}")
    }
}

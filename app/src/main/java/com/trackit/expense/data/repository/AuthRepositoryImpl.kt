package com.trackit.expense.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.trackit.expense.data.remote.api.TrackItApiService
import com.trackit.expense.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val apiService: TrackItApiService
) : AuthRepository {

    override val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    override val isAuthenticated: Boolean
        get() = firebaseAuth.currentUser != null

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential).await()

        // Best-effort: sync user profile to backend. Firebase sign-in already
        // succeeded above, so a backend failure must not block the user.
        val user = firebaseAuth.currentUser ?: return@runCatching
        runCatching {
            apiService.upsertProfile(
                mapOf(
                    "name"     to (user.displayName ?: ""),
                    "email"    to (user.email ?: ""),
                    "photoUrl" to (user.photoUrl?.toString() ?: "")
                )
            )
        }.onFailure { Log.w("AuthRepo", "Profile upsert failed (backend unreachable?): $it") }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    override fun authStateFlow(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }
}

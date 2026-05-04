package com.trackit.expense.data.remote.interceptor

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that attaches the Firebase ID token to every outgoing request.
 *
 * Firebase tokens expire after 1 hour but the SDK refreshes them automatically.
 * Passing `false` to getIdToken skips a forced refresh — the SDK returns a cached
 * token if it's still valid, which avoids a network round-trip on every API call.
 *
 * runBlocking is safe here because OkHttp already runs interceptors on IO threads.
 */
class AuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

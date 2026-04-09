package com.trackit.expense.data.remote.api

import com.trackit.expense.data.remote.dto.ExpenseDto
import com.trackit.expense.data.remote.dto.SyncRequestDto
import com.trackit.expense.data.remote.dto.SyncResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API service interface for the TrackIt Cloudflare Workers backend.
 *
 * All functions are `suspend` for coroutine compatibility. Callers should wrap
 * responses in `runCatching { }` to handle [retrofit2.HttpException] and
 * [java.io.IOException] in one place.
 *
 * ## Base URL
 * Configured in [com.trackit.expense.di.NetworkModule].
 * Replace `https://api.trackit.example.com/` with the actual Cloudflare Workers URL.
 *
 * ## Authentication
 * TODO: Add a Bearer token interceptor in [NetworkModule.provideOkHttpClient].
 */
interface TrackItApiService {

    // ──────────────────────────── EXPENSES ──────────────────────────────────

    /**
     * Fetch paginated expenses from the server.
     * Used for initial device setup or data restoration after reinstall.
     * @param page 1-based page number.
     * @param limit Number of records per page (max 100 recommended).
     */
    @GET("api/expenses")
    suspend fun getExpenses(
        @Query("page")  page: Int  = 1,
        @Query("limit") limit: Int = 50
    ): Response<List<ExpenseDto>>

    // ──────────────────────────── SYNC ──────────────────────────────────────

    /**
     * Batch-upload unsynced local expenses to the Cloudflare backend.
     *
     * The server processes each expense idempotently using its UUID as the
     * deduplication key. The response indicates which IDs were accepted
     * ([SyncResponseDto.syncedIds]) or rejected ([SyncResponseDto.failedIds]).
     *
     * Called exclusively by [com.trackit.expense.worker.SyncWorker] — never
     * directly from the UI thread.
     *
     * @param request Batch request containing all unsynced [ExpenseDto] records.
     * @return HTTP 200 with [SyncResponseDto] on success.
     *         HTTP 4xx/5xx results in a [retrofit2.HttpException].
     */
    @POST("api/sync")
    suspend fun syncExpenses(
        @Body request: SyncRequestDto
    ): Response<SyncResponseDto>
}

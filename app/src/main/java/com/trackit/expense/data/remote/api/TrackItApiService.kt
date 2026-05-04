package com.trackit.expense.data.remote.api

import com.trackit.expense.data.remote.dto.BalancesResponseDto
import com.trackit.expense.data.remote.dto.CreateSettlementRequestDto
import com.trackit.expense.data.remote.dto.CreateSplitRequestDto
import com.trackit.expense.data.remote.dto.ExpenseDto
import com.trackit.expense.data.remote.dto.GroupDto
import com.trackit.expense.data.remote.dto.SettlementResponseDto
import com.trackit.expense.data.remote.dto.SplitDto
import com.trackit.expense.data.remote.dto.SyncRequestDto
import com.trackit.expense.data.remote.dto.SyncResponseDto
import com.trackit.expense.data.remote.dto.UserProfileDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
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

    // ──────────────────────────── AUTH ──────────────────────────────────────

    /**
     * Upserts the user's profile in MongoDB after first Google Sign-In.
     * Uses $setOnInsert on the backend so createdAt is only written once.
     */
    @POST("api/auth/profile")
    suspend fun upsertProfile(@Body body: Map<String, String>): Response<Map<String, String>>

    @GET("api/me")
    suspend fun getProfile(): Response<UserProfileDto>

    @PUT("api/me")
    suspend fun updateProfile(@Body body: Map<String, String>): Response<Unit>

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

    // ──────────────────────────── GROUPS ────────────────────────────────────

    @POST("api/groups")
    suspend fun createGroup(@Body body: Map<String, String>): Response<GroupDto>

    @GET("api/groups")
    suspend fun getGroups(): Response<List<GroupDto>>

    @GET("api/groups/{id}")
    suspend fun getGroup(@Path("id") groupId: String): Response<GroupDto>

    @POST("api/groups/{id}/members")
    suspend fun addGroupMember(@Path("id") groupId: String, @Body body: Map<String, String>): Response<Unit>

    @POST("api/groups/{id}/splits")
    suspend fun addSplit(@Path("id") groupId: String, @Body body: CreateSplitRequestDto): Response<SplitDto>

    @GET("api/groups/{id}/splits")
    suspend fun getSplits(@Path("id") groupId: String): Response<List<SplitDto>>

    @GET("api/groups/{id}/balances")
    suspend fun getGroupBalances(@Path("id") groupId: String): Response<BalancesResponseDto>

    @POST("api/groups/{id}/settle")
    suspend fun recordSettlement(@Path("id") groupId: String, @Body body: CreateSettlementRequestDto): Response<SettlementResponseDto>

    @PUT("api/settlements/{id}/confirm")
    suspend fun confirmSettlement(@Path("id") settlementId: String): Response<Unit>
}

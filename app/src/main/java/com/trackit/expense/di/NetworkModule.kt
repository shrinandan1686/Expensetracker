package com.trackit.expense.di

import com.google.gson.Gson
import com.trackit.expense.BuildConfig
import com.trackit.expense.data.remote.api.TrackItApiService
import com.trackit.expense.data.remote.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Supplied at build time from `trackit.api.baseUrl` in local.properties (see
    // README → Local Development Setup). Kept out of source control so the repo
    // never carries anyone's LAN address or deployment URL.
    private val BASE_URL = BuildConfig.API_BASE_URL
    private const val TIMEOUT_SECS  = 30L

    /**
     * Body-level logging is debug-only: in a release build it would write every
     * request body — and the `Authorization: Bearer <Firebase ID token>` header —
     * into logcat, where any crash reporter or log collector could pick it up.
     * The header is redacted even in debug so a shared logcat never leaks a
     * usable token.
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)       // auth before logging so token appears in logs
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT_SECS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson                    // injected from RepositoryModule — shared singleton
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideTrackItApiService(retrofit: Retrofit): TrackItApiService =
        retrofit.create(TrackItApiService::class.java)
}

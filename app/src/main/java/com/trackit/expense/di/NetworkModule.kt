package com.trackit.expense.di

import com.google.gson.Gson
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

    // Local wrangler dev: "http://192.168.31.185:8787/"
    // Deployed:          "https://<your-worker>.workers.dev/"
    private const val BASE_URL = "http://192.168.31.185:8787/"
    private const val TIMEOUT_SECS  = 30L

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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

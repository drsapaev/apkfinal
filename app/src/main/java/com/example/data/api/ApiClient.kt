package com.example.data.api

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiClient handles the initialization and creation of the Retrofit HTTP client.
 */
object ApiClient {
    // Default fallback to standard Android Emulator localhost address pointing to the FastAPI backend port
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:18000/"

    private val moshi: Moshi = Moshi.Builder()
        .build()

    // A dynamic provider to fetch the bearer token on demand, preventing hard dependencies on Context
    var tokenProvider: () -> String? = { null }

    // Callback to trigger when a 401 Unauthorized response is intercepted
    var onUnauthorized: () -> Unit = {}

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = AuthInterceptor({ tokenProvider() }, { onUnauthorized() })

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val service: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /**
     * Determines the baseUrl based on the environment configuration or build options.
     * Keeps code flexible for either production staging endpoints or local developer setups.
     */
    private fun getBaseUrl(): String {
        return try {
            // Check if there is an env-injected environment variable via BuildConfig.
            // Under normal circumstances, the secrets plugin exposes this from the .env property file.
            val url = com.example.BuildConfig.BACKEND_URL
            if (!url.isNullOrBlank() && url != "?") {
                val cleaned = if (url.contains("localhost") || url.contains("127.0.0.1")) {
                    url.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2")
                } else {
                    url
                }
                if (cleaned.endsWith("/")) cleaned else "$cleaned/"
            } else {
                DEFAULT_BASE_URL
            }
        } catch (e: Exception) {
            DEFAULT_BASE_URL
        }
    }
}

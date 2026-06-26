package com.aistudio.clinicsystem.data.api

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiClient handles the initialization and creation of the Retrofit HTTP client.
 *
 * E1.4 (M0): HttpLoggingInterceptor is now gated behind BuildConfig.DEBUG.
 * Release builds do NOT log HTTP traffic — prevents leaking JWT tokens and
 * PHI-bearing request/response bodies to Logcat.
 */
object ApiClient {
    private val moshi: Moshi = Moshi.Builder()
        .build()

    // A dynamic provider to fetch the bearer token on demand, preventing hard dependencies on Context
    var tokenProvider: () -> String? = { null }

    // Callback to trigger when a 401 Unauthorized response is intercepted
    var onUnauthorized: () -> Unit = {}

    private val authInterceptor = AuthInterceptor({ tokenProvider() }, { onUnauthorized() })

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .apply {
            // E1.4: only attach HTTP body logging in debug builds.
            if (com.aistudio.clinicsystem.BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(loggingInterceptor)
            }
        }
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
     * Returns the backend base URL.
     *
     * E2.7 (M0): the URL is now defined per build type in app/build.gradle.kts:
     *   - debug:   http://10.0.2.2:18000/
     *   - release: https://api.clinic.example.com/  (must be overridden before release)
     *
     * The previous implementation read BACKEND_URL from the secrets-gradle-plugin
     * (.env / .env.example). That made the production URL ambiguous and depended
     * on a plaintext .env file shipping with the app. Now the URL is baked into
     * BuildConfig at build time — no runtime file lookup, no ambiguity.
     */
    private fun getBaseUrl(): String {
        val url = com.aistudio.clinicsystem.BuildConfig.BASE_URL
        return if (url.endsWith("/")) url else "$url/"
    }
}

package com.aistudio.clinicsystem.data.api

import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.utils.SessionManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Stage 2.6: ApiClient is now a @Singleton Hilt-managed class — NOT an
 * `object` with mutable `var tokenProvider` / `var onUnauthorized`.
 *
 * This closes audit findings H-9, M-5, NET-3, NET-16.
 *
 * The class provides:
 *   - [service] — legacy [ApiService] (staff-facing endpoints)
 *   - [mobileService] — [MobileApiService] (patient-facing endpoints)
 *   - [tokenAuthenticator] — exposed so the refresh client can be built
 *     without recursion
 *
 * The refresh OkHttpClient is a SEPARATE singleton (qualified with
 * `@Named("refresh")`) so it does NOT carry the [TokenAuthenticator]
 * (avoids infinite recursion: authenticate → refresh → 401 → ...).
 */
@Singleton
class ApiClient @javax.inject.Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository,
    private val moshi: Moshi,
) {
    /** Dynamic token provider — reads from SessionRepository (SSOT). */
    val tokenProvider: () -> String? = { sessionRepository.accessToken }

    /** The TokenAuthenticator — calls sessionRepository.invalidate() on refresh failure. */
    val tokenAuthenticator: TokenAuthenticator = TokenAuthenticator(
        sessionManager = sessionManager,
        moshi = moshi,
        baseUrlProvider = ::getBaseUrl,
        onSessionInvalidated = { sessionRepository.invalidate() },
    )

    private val authInterceptor = AuthInterceptor(tokenProvider)

    // Stage 3.3 (H-1 fix): Idempotency-Key interceptor — adds the header
    // to POST/PUT/PATCH/DELETE requests that carry an IdempotencyKey tag.
    private val idempotencyInterceptor = IdempotencyInterceptor()

    private val okHttpClient: OkHttpClient by lazy { buildOkHttpClient() }

    private val retrofit: Retrofit by lazy { buildRetrofit() }

    /** Legacy staff-facing API. */
    val service: ApiService by lazy { retrofit.create(ApiService::class.java) }

    /** Patient-facing mobile API. */
    val mobileService: MobileApiService by lazy { retrofit.create(MobileApiService::class.java) }

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            // Stage 3.3: idempotency interceptor runs AFTER auth so it can
            // see the final request URL + method. It only adds a header —
            // no request body inspection, no logging.
            .addInterceptor(idempotencyInterceptor)
            .authenticator(tokenAuthenticator)
            .apply {
                // Stage 4.3 (H-3 build fix): Certificate Pinning.
                // No-op in debug builds (no pins configured); active in
                // release builds where `clinic.certPin1` is set via CI secret.
                CertificatePinningConfig.buildPinner()?.let { pinner ->
                    certificatePinner(pinner)
                }

                // Stage 4.1 (NET-16 fix): HTTP logging in DEBUG only.
                // Authorization + Cookie headers are redacted (never logged).
                // In release builds, no HTTP logging interceptor is attached
                // at all — PHI in request/response bodies never reaches Logcat.
                if (com.aistudio.clinicsystem.BuildConfig.DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("Authorization")
                        redactHeader("Cookie")
                        redactHeader("Set-Cookie")
                        redactHeader("Idempotency-Key")
                    }
                    // Route OkHttp logs through Timber so the ReleaseTree
                    // redaction also applies (defense in depth).
                    loggingInterceptor.logger = HttpLoggingInterceptor.Logger { msg ->
                        timber.log.Timber.d("HTTP: $msg")
                    }
                    addInterceptor(loggingInterceptor)
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Returns the backend base URL. Reads from BuildConfig (injected by
     * Stage 1.3 via gradle property `clinic.baseUrl`).
     */
    fun getBaseUrl(): String {
        val url = com.aistudio.clinicsystem.BuildConfig.BASE_URL
        return if (url.endsWith("/")) url else "$url/"
    }
}

/**
 * Hilt module that provides the API layer singletons.
 *
 * [ApiClient] itself is `@Inject constructor` + `@Singleton` on the class,
 * so it doesn't need a `@Provides` here. This module provides:
 *   - [Moshi] singleton
 *   - `@Named("refresh")` OkHttpClient + Retrofit (separate from the main
 *     client to avoid TokenAuthenticator recursion — Stage 3.3 / NET-3 fix)
 *   - [ApiService] and [MobileApiService] facades over the Retrofit instances
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    /**
     * Separate Retrofit for the refresh endpoint — no AuthInterceptor, no
     * TokenAuthenticator. Closes NET-3: previously a new OkHttpClient was
     * allocated on EVERY 401, leaking sockets + ~50 KB heap each time.
     */
    @Provides
    @Singleton
    @Named("refresh")
    fun provideRefreshOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("refresh")
    fun provideRefreshRetrofit(
        @Named("refresh") client: OkHttpClient,
        moshi: Moshi,
        apiClient: ApiClient,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(apiClient.getBaseUrl())
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
}

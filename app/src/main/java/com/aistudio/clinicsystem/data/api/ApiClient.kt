package com.aistudio.clinicsystem.data.api

import com.aistudio.clinicsystem.utils.SessionManager
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
 *
 * M1/E3.2: TokenAuthenticator is wired in via [initWithSession] — it
 * transparently refreshes the access token on 401 responses. The legacy
 * [onUnauthorized] callback is kept for source compatibility but is no
 * longer the primary 401 handling mechanism.
 *
 * M1/E3.1: [mobileService] exposes the new [MobileApiService] contract
 * (the `mobile`, `authentication`, and `2fa` endpoint families under `api/v1`).
 * The legacy [service] is kept for backward compat during the migration.
 */
object ApiClient {
    private val moshi: Moshi = Moshi.Builder()
        .build()

    // A dynamic provider to fetch the bearer token on demand, preventing hard dependencies on Context
    var tokenProvider: () -> String? = { null }

    // Callback to trigger when a 401 Unauthorized response is intercepted AND
    // refresh fails. This routes the user to the login screen.
    var onUnauthorized: () -> Unit = {}

    // M1/E3.2: the TokenAuthenticator instance, set by [initWithSession].
    // Until initWithSession is called, this is null and the OkHttpClient below
    // uses only AuthInterceptor (legacy behavior, no auto-refresh).
    @Volatile
    private var tokenAuthenticator: TokenAuthenticator? = null

    /**
     * M1/E3.2: must be called once at app startup (from ClinicViewModel.init
     * or MainActivity.onCreate) with the [SessionManager] instance, so that
     * [TokenAuthenticator] can refresh tokens on 401 responses.
     *
     * After this call, the [okHttpClient] is rebuilt with the authenticator
     * attached, and [mobileService] becomes available.
     */
    fun initWithSession(sessionManager: SessionManager) {
        tokenAuthenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            moshi = moshi,
            baseUrlProvider = ::getBaseUrl,
            onSessionInvalidated = { onUnauthorized() }
        )
        // Force re-creation of retrofit + services on next access
        _retrofit = null
        _mobileRetrofit = null
    }

    @Volatile private var _retrofit: Retrofit? = null
    @Volatile private var _mobileRetrofit: Retrofit? = null

    private val retrofit: Retrofit
        get() = _retrofit ?: synchronized(this) {
            _retrofit ?: buildRetrofit().also { _retrofit = it }
        }

    val service: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /**
     * M1/E3.1: the new MobileApiService — patient-facing contract.
     * Use this for all new code; legacy [service] will be removed in M2.
     */
    val mobileService: MobileApiService by lazy {
        retrofit.create(MobileApiService::class.java)
    }

    private val authInterceptor = AuthInterceptor({ tokenProvider() }, { onUnauthorized() })

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                // M1/E3.2: attach TokenAuthenticator for auto-refresh on 401.
                tokenAuthenticator?.let { authenticator(it) }

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
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(buildOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Returns the backend base URL.
     *
     * E2.7 (M0): the URL is now defined per build type in app/build.gradle.kts:
     *   - debug:   http://10.0.2.2:18000/
     *   - release: https://api.clinic.example.com/  (must be overridden before release)
     */
    private fun getBaseUrl(): String {
        val url = com.aistudio.clinicsystem.BuildConfig.BASE_URL
        return if (url.endsWith("/")) url else "$url/"
    }
}

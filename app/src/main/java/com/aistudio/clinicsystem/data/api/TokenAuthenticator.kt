package com.aistudio.clinicsystem.data.api

import android.util.Log
import com.aistudio.clinicsystem.utils.SessionManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException

/**
 * TokenAuthenticator — OkHttp [Authenticator] that transparently refreshes
 * the access token on HTTP 401 and retries the original request.
 *
 * M1/E3.2: replaces the previous pattern of `AuthInterceptor.onUnauthorized`
 * which would simply route the user to the login screen on any 401 — even
 * when the access token had merely expired and could be refreshed.
 *
 * Flow:
 *  1. A request fails with 401.
 *  2. [authenticate] is called by OkHttp.
 *  3. We acquire [refreshMutex] to coalesce concurrent refresh attempts:
 *     if another thread already refreshed the token while we were waiting,
 *     we skip the refresh and just retry with the new token.
 *  4. If refresh succeeds → save new tokens, retry original request with
 *     the new access token.
 *  5. If refresh fails (401 on the refresh endpoint, network error, etc.) →
 *     clear the session and return null (OkHttp gives up; the calling
 *     repository will surface the 401 to the user, who will be routed to
 *     login by the [SessionManager.sessionInvalidated] callback).
 *
 * The refresh endpoint is hit through a separate, minimal Retrofit instance
 * (without the [TokenAuthenticator] attached) to avoid infinite recursion:
 *   authenticate → refresh → 401 → authenticate → refresh → 401 → ...
 *
 * NOTE: this class is intentionally framework-light. It uses [runBlocking]
 * inside [authenticate] (which OkHttp calls on a background thread) to keep
 * the signature synchronous. The refresh network call is short and bounded.
 *
 * NOTE: the session manager interface was extended with [setTokens] and
 * [clearSession] for this class. See [com.aistudio.clinicsystem.utils.SessionManager].
 */
class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val moshi: com.squareup.moshi.Moshi,
    private val baseUrlProvider: () -> String,
    /** Called when refresh fails — typically routes user to login. */
    private val onSessionInvalidated: () -> Unit = {}
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
        // Guard against infinite retry loops: if we already retried 2 times,
        // give up. OkHttp also has its own guard, but this is belt-and-braces.
        if (responseCount(response) >= 2) {
            Log.w(TAG, "authenticate: too many retries, giving up")
            return null
        }

        // Extract the token that was used on the failing request (if any),
        // so we can detect the "another thread already refreshed" case.
        val failingRequestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")

        return runBlocking {
            refreshMutex.withLock {
                val currentAccessToken = sessionManager.getToken()

                // Case A: another concurrent request already refreshed the
                // token while we were waiting for the mutex. Just retry
                // with the new token.
                if (currentAccessToken != null && currentAccessToken != failingRequestToken) {
                    Log.d(TAG, "authenticate: token already refreshed by another request, retrying")
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                // Case B: we need to refresh ourselves.
                val refreshToken = sessionManager.getRefreshToken()
                if (refreshToken.isNullOrBlank()) {
                    Log.w(TAG, "authenticate: no refresh token, cannot refresh — clearing session")
                    sessionManager.clearSession()
                    onSessionInvalidated()
                    return@withLock null
                }

                val newTokens = try {
                    performRefresh(refreshToken)  // suspend call — OK inside runBlocking
                } catch (e: Exception) {
                    Log.e(TAG, "authenticate: refresh failed: ${e.message}", e)
                    sessionManager.clearSession()
                    onSessionInvalidated()
                    return@withLock null
                }

                // Persist the new tokens
                sessionManager.setTokens(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken
                )
                Log.d(TAG, "authenticate: refresh succeeded, retrying original request")

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
            }
        }
    }

    /**
     * Performs a synchronous POST to /api/v1/authentication/refresh using a
     * minimal, no-auth Retrofit instance (so we don't recurse into ourselves).
     *
     * Note: [MobileApiService.refreshToken] is a suspend function. We are
     * already inside [runBlocking] (called from [authenticate]), so we just
     * call it directly — Kotlin allows suspend calls inside runBlocking.
     */
    @Throws(IOException::class)
    private suspend fun performRefresh(refreshToken: String): RefreshTokenResponse {
        val refreshClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val refreshRetrofit = Retrofit.Builder()
            .baseUrl(baseUrlProvider())
            .client(refreshClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val refreshApi = refreshRetrofit.create(MobileApiService::class.java)

        val response = refreshApi.refreshToken(RefreshTokenRequest(refreshToken))

        if (!response.isSuccessful) {
            throw IOException("Refresh endpoint returned ${response.code()}")
        }

        return response.body()
            ?: throw IOException("Refresh response body is null")
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val TAG = "TokenAuthenticator"
    }
}

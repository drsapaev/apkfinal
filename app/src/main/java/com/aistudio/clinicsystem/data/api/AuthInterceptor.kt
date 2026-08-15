package com.aistudio.clinicsystem.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stage 2.6 (H-9 fix): AuthInterceptor — adds the Bearer token to every
 * outgoing request. NOTHING ELSE.
 *
 * The previous implementation also called [onUnauthorized] on every 401,
 * which RACED the [TokenAuthenticator] refresh flow: a single expired
 * access token would trigger logout (via [onUnauthorized]) WHILE the
 * authenticator was still trying to refresh. Even worse, [AuthInterceptor]
 * is an *application* interceptor, so it sees the response AFTER the
 * authenticator — meaning a successful refresh + retry was still
 * interpreted as "session invalid".
 *
 * Now [AuthInterceptor] only adds the header. 401 handling is the sole
 * responsibility of [TokenAuthenticator], which calls
 * [SessionRepository.invalidate] on refresh failure.
 *
 * The `onUnauthorized` constructor parameter is GONE.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // If the request already has an Authorization header (e.g. the
        // refresh endpoint uses a refresh-token bearer), respect it.
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            // No token — proceed without Authorization header. The server
            // will 401, and TokenAuthenticator will handle it.
            return chain.proceed(originalRequest)
        }

        val bearerValue = if (token.startsWith("Bearer ", ignoreCase = true)) {
            token
        } else {
            "Bearer $token"
        }
        val authedRequest = originalRequest.newBuilder()
            .header("Authorization", bearerValue)
            .build()
        return chain.proceed(authedRequest)
    }
}

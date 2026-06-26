package com.aistudio.clinicsystem.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * AuthInterceptor automatically appends the user's JWT bearer token under
 * the standard HTTP 'Authorization' header for private, secure endpoints.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit = {}
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // If the request already contains an Authorization header, respect it and continue
        if (originalRequest.header("Authorization") != null) {
            val response = chain.proceed(originalRequest)
            return handleUnauthorized(originalRequest.url.encodedPath, response)
        }

        val token = tokenProvider()
        val builder = originalRequest.newBuilder()
        
        if (!token.isNullOrBlank()) {
            val bearerValue = if (token.startsWith("Bearer ", ignoreCase = true)) {
                token
            } else {
                "Bearer $token"
            }
            builder.header("Authorization", bearerValue)
        }
        
        val response = chain.proceed(builder.build())
        return handleUnauthorized(originalRequest.url.encodedPath, response)
    }

    private fun handleUnauthorized(path: String, response: Response): Response {
        if (response.code == 401 && !path.endsWith("/login")) {
            onUnauthorized()
        }
        return response
    }
}

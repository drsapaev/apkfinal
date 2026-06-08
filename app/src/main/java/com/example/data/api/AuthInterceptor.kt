package com.example.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * AuthInterceptor automatically appends the user's JWT bearer token under
 * the standard HTTP 'Authorization' header for private, secure endpoints.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // If the request already contains an Authorization header, respect it and continue
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
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
        
        return chain.proceed(builder.build())
    }
}

package com.aistudio.clinicsystem.utils

import android.content.Context

/**
 * SessionManager — abstraction over the secure token storage layer.
 *
 * M1/E3.2: extended with [getRefreshToken] and [setTokens] to support
 * the new [com.aistudio.clinicsystem.data.api.TokenAuthenticator] flow.
 * The old [saveSession] is kept for source compatibility with code that
 * stores token + phone + role together (e.g. after login).
 */
interface SessionManager {
    /** Stores access token + user meta. Does NOT set refresh token — use [setTokens] for that. */
    fun saveSession(token: String, phone: String, role: String)

    /** Stores both access and refresh tokens (used after login / refresh). */
    fun setTokens(accessToken: String, refreshToken: String)

    fun getToken(): String?
    fun getRefreshToken(): String?
    fun getPhone(): String?
    fun getRole(): String?
    fun clearSession()
    fun isLoggedIn(): Boolean
}

class SessionManagerImpl(private val context: Context) : SessionManager {
    override fun saveSession(token: String, phone: String, role: String) {
        TokenManager.saveAuthData(context, token, phone, role)
    }

    override fun setTokens(accessToken: String, refreshToken: String) {
        TokenManager.saveTokens(context, accessToken, refreshToken)
    }

    override fun getToken(): String? = TokenManager.getToken(context)

    override fun getRefreshToken(): String? = TokenManager.getRefreshToken(context)

    override fun getPhone(): String? = TokenManager.getPhone(context)

    override fun getRole(): String? = TokenManager.getRole(context)

    override fun clearSession() {
        TokenManager.clearAuthData(context)
    }

    override fun isLoggedIn(): Boolean = TokenManager.isLoggedIn(context)

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManagerImpl(context.applicationContext).also { instance = it }
            }
        }
    }
}

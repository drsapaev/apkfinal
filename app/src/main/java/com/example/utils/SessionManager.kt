package com.example.utils

import android.content.Context

interface SessionManager {
    fun saveSession(token: String, phone: String, role: String)
    fun getToken(): String?
    fun getPhone(): String?
    fun getRole(): String?
    fun clearSession()
    fun isLoggedIn(): Boolean
}

class SessionManagerImpl(private val context: Context) : SessionManager {
    override fun saveSession(token: String, phone: String, role: String) {
        TokenManager.saveAuthData(context, token, phone, role)
    }

    override fun getToken(): String? = TokenManager.getToken(context)

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

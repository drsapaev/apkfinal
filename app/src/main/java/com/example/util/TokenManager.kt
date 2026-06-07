package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * TokenManager is a thread-safe helper singleton designed to securely save, retrieve,
 * and clear user authentication tokens using standard Android SharedPreferences.
 */
object TokenManager {
    private const val PREF_NAME = "intellect_clinic_prefs"
    private const val KEY_JWT_TOKEN = "jwt_access_token"
    private const val KEY_PHONE_NUMBER = "user_phone_number"
    private const val KEY_USER_ROLE = "user_role"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Store the JWT access token and logged-in user meta details on disk.
     */
    fun saveAuthData(context: Context, token: String, phone: String, role: String) {
        getPrefs(context).edit().apply {
            putString(KEY_JWT_TOKEN, token)
            putString(KEY_PHONE_NUMBER, phone)
            putString(KEY_USER_ROLE, role)
            apply()
        }
    }

    /**
     * Retrieves the stored access token. Returns null if not exists or unauthorized.
     */
    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_JWT_TOKEN, null)
    }

    /**
     * Retrieves the stored phone number.
     */
    fun getPhone(context: Context): String? {
        return getPrefs(context).getString(KEY_PHONE_NUMBER, null)
    }

    /**
     * Retrieves the stored user role (e.g. PATIENT or STAFF).
     */
    fun getRole(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_ROLE, null)
    }

    /**
     * Clears all credential records when a user performs a manual Logout.
     */
    fun clearAuthData(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_JWT_TOKEN)
            remove(KEY_PHONE_NUMBER)
            remove(KEY_USER_ROLE)
            apply()
        }
    }

    /**
     * Check if a valid login session persists.
     */
    fun isLoggedIn(context: Context): Boolean {
        return !getToken(context).isNullOrBlank()
    }
}

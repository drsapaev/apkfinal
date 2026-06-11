package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * TokenManager is a thread-safe helper singleton designed to securely save, retrieve,
 * and clear user authentication tokens using standard Android SharedPreferences.
 */
object TokenManager {
    private const val PREF_NAME = "intellect_clinic_secure_prefs"
    private const val KEY_JWT_TOKEN = "jwt_access_token"
    private const val KEY_PHONE_NUMBER = "user_phone_number"
    private const val KEY_USER_ROLE = "user_role"

    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("TokenManager", "Encryption keystore failure, falling back to standard SharedPreferences", e)
            context.getSharedPreferences("intellect_clinic_fallback_prefs", Context.MODE_PRIVATE)
        }
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

    /**
     * Retrieves or generates a secure, device-specific 32-byte key for Room database encryption.
     * Saved encrypted inside EncryptedSharedPreferences.
     */
    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        val prefs = getPrefs(context)
        val keyString = prefs.getString("db_key_sec", null)
        if (keyString != null) {
            return android.util.Base64.decode(keyString, android.util.Base64.DEFAULT)
        }
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
        prefs.edit().putString("db_key_sec", encoded).apply()
        return bytes
    }

    /**
     * Gets screen security protection state (FLAG_SECURE)
     */
    fun isScreenSecureEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("screen_secure_enabled", true)
    }

    /**
     * Sets screen security protection state (FLAG_SECURE)
     */
    fun setScreenSecureEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("screen_secure_enabled", enabled).apply()
    }
}

package com.aistudio.clinicsystem.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * TokenManager is a thread-safe helper singleton designed to securely save, retrieve,
 * and clear user authentication tokens using EncryptedSharedPreferences (AES-256).
 *
 * E1.6 (M0): the previous version silently fell back to plain SharedPreferences
 * when the Android Keystore was unavailable (corrupted keystore, factory reset
 * edge cases, etc.). This was a security ship-blocker: JWTs could end up stored
 * in cleartext on disk without the user ever knowing.
 *
 * The new behavior is fail-closed:
 *  - If EncryptedSharedPreferences cannot be created, [getPrefs] returns null.
 *  - All write operations (saveAuthData, clearAuthData, etc.) become no-ops.
 *  - All read operations return null / default values, which forces the auth
 *    flow to treat the session as invalid → user is prompted to re-login.
 *  - The error is logged to Logcat at ERROR level so it surfaces during
 *    development and in crash reports.
 *
 * This is the correct trade-off for a medical application: an unusable session
 * is far better than a session whose credentials are in cleartext on disk.
 *
 * NOTE: Application code that consumes TokenManager should treat null returns
 * as "no valid session" and route the user to the auth screen.
 */
object TokenManager {
    private const val PREF_NAME = "intellect_clinic_secure_prefs"
    private const val KEY_JWT_TOKEN = "jwt_access_token"
    private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
    private const val KEY_PHONE_NUMBER = "user_phone_number"
    private const val KEY_USER_ROLE = "user_role"
    private const val TAG = "TokenManager"

    /**
     * Last initialization error, if any. Useful for diagnostics
     * (e.g. showing the user a "keystore corrupted" dialog).
     */
    @Volatile
    private var lastInitError: Throwable? = null

    /**
     * Returns EncryptedSharedPreferences, or null if the Android Keystore is
     * unavailable / corrupted. Callers MUST handle null gracefully.
     */
    private fun getPrefs(context: Context): SharedPreferences? {
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
            // E1.6: FAIL CLOSED. Do NOT fall back to plain SharedPreferences —
            // that would store JWTs in cleartext.
            lastInitError = e
            Log.e(
                TAG,
                "EncryptedSharedPreferences initialization failed. " +
                    "Refusing to use plaintext storage. " +
                    "Session storage is unavailable — user must re-authenticate.",
                e
            )
            null
        }
    }

    /**
     * Store the JWT access token and logged-in user meta details on disk.
     * No-op if encrypted storage is unavailable.
     */
    fun saveAuthData(context: Context, token: String, phone: String, role: String) {
        val prefs = getPrefs(context) ?: return
        prefs.edit().apply {
            putString(KEY_JWT_TOKEN, token)
            putString(KEY_PHONE_NUMBER, phone)
            putString(KEY_USER_ROLE, role)
            apply()
        }
    }

    /**
     * M1/E3.2: store both access and refresh tokens (used after login and
     * after a successful refresh). Phone/role are preserved if already set.
     * No-op if encrypted storage is unavailable.
     */
    fun saveTokens(context: Context, accessToken: String, refreshToken: String) {
        val prefs = getPrefs(context) ?: return
        prefs.edit().apply {
            putString(KEY_JWT_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    /**
     * M1/E3.2: retrieve the refresh token. Returns null if not present OR
     * if encrypted storage is unavailable.
     */
    fun getRefreshToken(context: Context): String? {
        return getPrefs(context)?.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Retrieves the stored access token. Returns null if not present OR
     * if encrypted storage is unavailable.
     */
    fun getToken(context: Context): String? {
        return getPrefs(context)?.getString(KEY_JWT_TOKEN, null)
    }

    /**
     * Retrieves the stored phone number. Returns null if not present OR
     * if encrypted storage is unavailable.
     */
    fun getPhone(context: Context): String? {
        return getPrefs(context)?.getString(KEY_PHONE_NUMBER, null)
    }

    /**
     * Retrieves the stored user role (e.g. PATIENT or STAFF).
     */
    fun getRole(context: Context): String? {
        return getPrefs(context)?.getString(KEY_USER_ROLE, null)
    }

    /**
     * Clears all credential records when a user performs a manual Logout.
     * M1/E3.2: also clears the refresh token.
     * No-op if encrypted storage is unavailable.
     */
    fun clearAuthData(context: Context) {
        val prefs = getPrefs(context) ?: return
        prefs.edit().apply {
            remove(KEY_JWT_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_PHONE_NUMBER)
            remove(KEY_USER_ROLE)
            apply()
        }
    }

    /**
     * Check if a valid login session persists. Returns false if encrypted
     * storage is unavailable.
     */
    fun isLoggedIn(context: Context): Boolean {
        return !getToken(context).isNullOrBlank()
    }

    /**
     * Returns true if the encrypted storage layer is healthy, false if the
     * keystore is corrupted. Application code may use this to display a
     * user-facing error dialog ("Сбой безопасного хранилища, обратитесь к
     * администратору") and refuse to operate until the issue is resolved.
     */
    fun isStorageHealthy(context: Context): Boolean {
        return getPrefs(context) != null
    }

    /**
     * Returns the last initialization error (for diagnostics / crash reports).
     */
    fun lastInitError(): Throwable? = lastInitError

    /**
     * Retrieves or generates a secure, device-specific 32-byte key for Room
     * database encryption. Saved encrypted inside EncryptedSharedPreferences.
     * Returns null if encrypted storage is unavailable (the caller should
     * refuse to open the database in that case).
     */
    fun getOrCreateDatabaseKey(context: Context): ByteArray? {
        val prefs = getPrefs(context) ?: return null
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
     * Gets screen security protection state (FLAG_SECURE). Always returns true
     * now that SecureScreen is properly implemented (E1.5).
     *
     * The previous implementation returned false with a comment about
     * "streaming emulator black screen". That was a security ship-blocker.
     */
    fun isScreenSecureEnabled(context: Context): Boolean {
        return true
    }

    /**
     * Sets screen security protection state (FLAG_SECURE).
     */
    fun setScreenSecureEnabled(context: Context, enabled: Boolean) {
        getPrefs(context)?.edit()?.putBoolean("screen_secure_enabled", enabled)?.apply()
    }
}

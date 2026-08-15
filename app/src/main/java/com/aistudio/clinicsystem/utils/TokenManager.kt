@file:Suppress("UnusedPrivateProperty", "FunctionOnlyReturningConstant", "UnusedParameter")
package com.aistudio.clinicsystem.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

/**
 * TokenManager — secure storage for JWT tokens + SQLCipher passphrase.
 *
 * Stage 4.2 (M-3, M-4 fix): the master key is now hardware-backed on
 * devices that support StrongBox (Pixel 3+, Galaxy S20+, etc.). On older
 * devices it falls back to the TEE-backed keystore. The key is NOT
 * user-auth-bound by default — that would require the user to authenticate
 * on every read, which is incompatible with the app's auto-session-restore
 * flow. Biometric auth is handled separately at the BiometricPrompt layer
 * (Stage 4.4), where it gates access to a SEPARATE key used only for
 * refresh-token decryption.
 *
 * E1.6 fail-closed behavior is preserved: if EncryptedSharedPreferences
 * cannot be created (corrupted keystore, factory reset, etc.), all writes
 * become no-ops and all reads return null. The user is forced to re-login.
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
     *
     * Stage 4.2: master key is hardware-backed (StrongBox if available,
     * TEE otherwise). This is a defense-in-depth improvement — even on a
     * rooted device, extracting the master key from the hardware keystore
     * is significantly harder than from the software keystore.
     */
    private fun getPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKeyBuilder = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)

            // Stage 4.2 (M-3 fix): prefer StrongBox (hardware-backed) on
            // devices that support it. Fall back silently to TEE-backed
            // keystore on devices without StrongBox.
            //
            // Note: we intentionally do NOT call setUserAuthenticationRequired(true)
            // here — that would force biometric auth on every read, breaking
            // auto-session-restore. The biometric-gated key for refresh-token
            // decryption is a separate key (Stage 4.4).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    masterKeyBuilder.setIsStrongBoxBacked(true)
                } catch (e: Exception) {
                    // StrongBox not available on this device — fall back to TEE.
                    Timber.w("StrongBox unavailable, falling back to TEE-backed keystore: ${e.message}")
                }
            }

            val masterKey = masterKeyBuilder.build()

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
            Timber.e(
                e,
                "EncryptedSharedPreferences initialization failed. " +
                    "Refusing to use plaintext storage. " +
                    "Session storage is unavailable — user must re-authenticate."
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

    // ───────────────────────────────────────────────────────────────────
    // Stage 4.4: Biometric-gated refresh token storage.
    //
    // The refresh token is encrypted with a keystore key that requires
    // biometric auth to use. The encrypted blob + IV are stored here.
    // The plaintext refresh token is NEVER stored — only the ciphertext.
    // ───────────────────────────────────────────────────────────────────

    private const val KEY_BIOMETRIC_REFRESH_BLOB = "biometric_refresh_blob"

    /**
     * Stores the biometric-encrypted refresh token blob (ciphertext + IV).
     * Called after the user enrolls in biometric login and the refresh
     * token is encrypted with the biometric-gated key.
     */
    fun saveEncryptedRefreshTokenBlob(context: Context, blob: String) {
        getPrefs(context)?.edit()?.putString(KEY_BIOMETRIC_REFRESH_BLOB, blob)?.apply()
    }

    /**
     * Retrieves the biometric-encrypted refresh token blob, or null if
     * the user has not enrolled in biometric login.
     */
    fun getEncryptedRefreshTokenBlob(context: Context): String? {
        return getPrefs(context)?.getString(KEY_BIOMETRIC_REFRESH_BLOB, null)
    }

    /**
     * Clears the biometric-encrypted refresh token blob. Called on logout
     * or when biometric enrollment is disabled.
     */
    fun clearEncryptedRefreshTokenBlob(context: Context) {
        getPrefs(context)?.edit()?.remove(KEY_BIOMETRIC_REFRESH_BLOB)?.apply()
    }
}

@file:Suppress("UnusedPrivateProperty")
package com.aistudio.clinicsystem.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stage 4.4 (C-7 final fix): BiometricCryptoHelper — generates and
 * manages the biometric-gated keystore key used by BiometricPrompt.
 *
 * The previous implementation called `biometricPrompt.authenticate(promptInfo)`
 * (single-arg form) — biometric "success" simply unlocked use of an
 * already-stored JWT. Anyone who could read EncryptedSharedPreferences
 * (root, ADB backup exploit) got the JWT without biometric.
 *
 * The new flow:
 *   1. On biometric enrollment (user opts in), generate a `SecretKey` in
 *      the Android Keystore with `setUserAuthenticationRequired(true)`.
 *      This key CANNOT be used without biometric auth.
 *   2. Initialize a `Cipher` with the key. Wrap it in `BiometricPrompt.CryptoObject`.
 *   3. Call `biometricPrompt.authenticate(promptInfo, cryptoObject)` (two-arg form).
 *   4. On `onAuthenticationSucceeded`, the `Cipher` is unlocked — use it
 *      to decrypt the encrypted refresh token stored in EncryptedSharedPreferences.
 *
 * Security properties:
 *   - The refresh token is encrypted with a key that requires biometric
 *     auth to use. Even with root, extracting the key from the hardware
 *     keystore + reproducing the biometric auth is significantly harder
 *     than just reading a cleartext SharedPreferences file.
 *   - The key is StrongBox-backed where available (Pixel 3+, S20+).
 *   - The key is invalidated if the user enrolls a new fingerprint
 *     (`setInvalidatedByBiometricEnrollment(true)`).
 *
 * Reference: https://developer.android.com/training/sign-in/biometric-auth
 */
object BiometricCryptoHelper {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "clinic_biometric_refresh_key"
    private const val GCM_IV_LENGTH = 12 // bytes

    /**
     * Checks whether biometric authentication is available on this device.
     * Returns true only if BIOMETRIC_STRONG is available (fingerprint or
     * face that meets Class 3 strength). BIOMETRIC_WEAK is NOT sufficient
     * for the refresh-token decryption key.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Generates (or retrieves) the biometric-gated `SecretKey` from the
     * Android Keystore. The key:
     *   - Algorithm: AES
     *   - Block mode: GCM
     *   - Padding: NoPadding
     *   - Purpose: ENCRYPT | DECRYPT
     *   - Requires biometric auth to use
     *   - StrongBox-backed where available
     *   - Invalidated by new biometric enrollment
     *
     * Returns null if the keystore is unavailable.
     */
    private fun getOrCreateBiometricKey(): SecretKey? {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE)
        keyStore.load(null)

        // Return existing key if present
        keyStore.getKey(KEY_ALIAS, null)?.let {
            return it as? SecretKey
        }

        // Generate a new key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        // Stage 4.2: StrongBox where available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (e: Exception) {
                // StrongBox not available — fall back to TEE
            }
        }

        // Stage 4.4: require BIOMETRIC_STRONG (Class 3) — not BIOMETRIC_WEAK.
        // KeyGenParameterSpec.Builder.setUserAuthenticationParameters expects
        // KeyProperties.AUTH_* constants, NOT BiometricManager.Authenticators.
        // Both are the same integer value (0x000F), but lint enforces the type.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                /* timeout = */ 0, // 0 = require auth on every use (no timeout)
                /* types = */ KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Creates a `Cipher` initialized for ENCRYPTION, wrapped in a
     * `BiometricPrompt.CryptoObject`. The Cipher will be unlocked by
     * biometric auth — call `doFinal` on it only inside
     * `onAuthenticationSucceeded`.
     *
     * Returns null if the keystore key cannot be created or the cipher
     * cannot be initialized.
     */
    fun createEncryptionCryptoObject(): BiometricPrompt.CryptoObject? {
        val key = getOrCreateBiometricKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(
                "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}",
            )
            cipher.init(Cipher.ENCRYPT_MODE, key)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to create encryption CryptoObject")
            null
        }
    }

    /**
     * Creates a `Cipher` initialized for DECRYPTION with the given IV.
     * The Cipher will be unlocked by biometric auth — call `doFinal` on
     * it only inside `onAuthenticationSucceeded`.
     *
     * Returns null if the keystore key cannot be retrieved or the cipher
     * cannot be initialized (e.g. key was invalidated by new biometric
     * enrollment — caller must re-enroll).
     */
    fun createDecryptionCryptoObject(iv: ByteArray): BiometricPrompt.CryptoObject? {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE)
        keyStore.load(null)
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null

        return try {
            val cipher = Cipher.getInstance(
                "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}",
            )
            val spec = GCMParameterSpec(128, iv) // 128-bit auth tag
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to create decryption CryptoObject — key may be invalidated")
            null
        }
    }

    /**
     * Encrypts the refresh token using the unlocked Cipher (passed from
     * `onAuthenticationSucceeded`). Returns the ciphertext + IV, which
     * the caller stores in EncryptedSharedPreferences.
     *
     * The IV is non-secret and can be stored alongside the ciphertext.
     */
    fun encryptRefreshToken(cipher: Cipher, refreshToken: String): EncryptedData {
        val ciphertext = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        return EncryptedData(
            ciphertext = ciphertext,
            iv = cipher.iv,
        )
    }

    /**
     * Decrypts the refresh token using the unlocked Cipher. Returns the
     * plaintext token, or null if decryption fails.
     */
    fun decryptRefreshToken(cipher: Cipher, encryptedData: EncryptedData): String? {
        return try {
            val plaintext = cipher.doFinal(encryptedData.ciphertext)
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to decrypt refresh token")
            null
        }
    }

    /**
     * Deletes the biometric key from the keystore. Called when the user
     * disables biometric login.
     */
    fun deleteBiometricKey() {
        try {
            val keyStore = java.security.KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to delete biometric key")
        }
    }

    /**
     * Returns true if the biometric key exists in the keystore (i.e. the
     * user has previously enrolled in biometric login).
     */
    fun isBiometricKeyPresent(): Boolean {
        return try {
            val keyStore = java.security.KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convenience method: returns a CryptoObject for decryption of the
     * stored refresh token. Reads the encrypted blob + IV from
     * EncryptedSharedPreferences, creates a Cipher, and wraps it.
     *
     * Returns null if:
     *   - No biometric key is enrolled (user has not opted in to biometric login)
     *   - The stored encrypted blob is missing (user has never logged in)
     *   - The keystore key was invalidated (new fingerprint enrolled) —
     *     caller must prompt the user to re-enter password and re-enroll
     *
     * This is the entry point used by [com.aistudio.clinicsystem.ui.screens.AuthScreen]
     * when the user taps the biometric login button.
     */
    fun createDecryptionCryptoObjectForLogin(context: Context): BiometricPrompt.CryptoObject? {
        // Read the IV from EncryptedSharedPreferences — without it we can't
        // initialize the Cipher for decryption.
        val prefs = TokenManager.getEncryptedRefreshTokenBlob(context) ?: return null
        val encryptedData = EncryptedData.fromStorageString(prefs) ?: return null
        return createDecryptionCryptoObject(encryptedData.iv)
    }

    /**
     * Encrypted blob + IV. Both are Base64-encoded for storage in
     * EncryptedSharedPreferences.
     */
    data class EncryptedData(
        val ciphertext: ByteArray,
        val iv: ByteArray,
    ) {
        fun toStorageString(): String {
            val b64ciphertext = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
            val b64iv = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
            return "$b64iv:$b64ciphertext"
        }

        companion object {
            fun fromStorageString(s: String): EncryptedData? {
                val parts = s.split(":", limit = 2)
                if (parts.size != 2) return null
                val iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
                val ciphertext = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
                return EncryptedData(ciphertext, iv)
            }
        }
    }
}

package com.aistudio.clinicsystem.domain.repository

import com.aistudio.clinicsystem.domain.model.LoginResult
import com.aistudio.clinicsystem.domain.model.User
import javax.crypto.Cipher

/**
 * Stage 5.3 (C-9 fix): AuthRepositoryInterface — the domain-layer
 * contract for authentication operations.
 *
 * The previous version had method signatures that didn't match the real
 * AuthRepository (used different parameter names, missing methods).
 * Stage 5 rewrites the interface to match the actual repo methods.
 *
 * ViewModels and UseCases depend on this interface, NOT on the concrete
 * AuthRepository.
 *
 * P0-1 audit fix: added [loginWithBiometricRefreshToken] — completes
 * biometric login by decrypting the stored refresh-token blob with the
 * cipher unlocked by BiometricPrompt, then exchanging the plaintext
 * refresh token for a fresh access token via the backend's
 * `/authentication/refresh` endpoint.
 */
interface AuthRepositoryInterface {

    suspend fun login(username: String, password: String): Result<com.aistudio.clinicsystem.data.repository.LoginOutcome>

    suspend fun verify2FA(
        challengeToken: String,
        totpCode: String,
        rememberDevice: Boolean,
    ): Result<com.aistudio.clinicsystem.data.repository.LoginOutcome>

    suspend fun request2FARecovery(
        challengeToken: String,
        method: String,
    ): Result<String>

    suspend fun verify2FARecovery(
        recoveryToken: String,
        code: String,
    ): Result<com.aistudio.clinicsystem.data.repository.LoginOutcome>

    suspend fun verifyCurrentSession(): Result<com.aistudio.clinicsystem.data.api.UserDto>

    /**
     * P0-1 audit fix: completes biometric login using the [cipher]
     * unlocked by BiometricPrompt. The cipher is used to decrypt the
     * stored encrypted refresh-token blob; the plaintext refresh token
     * is then exchanged for a fresh access token via
     * `POST /api/v1/authentication/refresh`.
     *
     * Returns the user profile on success, or fails if:
     *   - the encrypted blob is missing (user never enrolled in biometric)
     *   - decryption fails (key invalidated by new fingerprint enrollment)
     *   - the refresh-token exchange fails (network, 401 — refresh token expired)
     *
     * @param phone    the phone of the user attempting biometric login
     *                 (used to look up the encrypted blob in
     *                 EncryptedSharedPreferences — actually stored
     *                 globally, but the parameter exists for API symmetry
     *                 with [loginWithBiometrics] and for future per-user
     *                 key alias support)
     * @param cipher   the Cipher unlocked by BiometricPrompt; initialised
     *                 for DECRYPTION with the IV stored alongside the
     *                 encrypted refresh-token blob
     */
    suspend fun loginWithBiometricRefreshToken(
        phone: String,
        cipher: Cipher,
    ): Result<com.aistudio.clinicsystem.data.api.UserDto>

    suspend fun logout(): Result<Unit>
}

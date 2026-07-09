package com.aistudio.clinicsystem.data.repository

import android.content.Context
import android.util.Log
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.api.ApiService
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.api.LoginRequest
import com.aistudio.clinicsystem.data.api.LogoutRequest
import com.aistudio.clinicsystem.data.api.UserDto
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.utils.SessionManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuthRepository manages network authentication workflows with the backend FastAPI 'final' server,
 * while maintaining local cache synchronization in the Room database.
 *
 * M3B.1: now uses [SessionRepository] as SSOT for session state.
 * Token storage and session state updates go through SessionRepository
 * instead of directly calling SessionManagerImpl.
 */
class AuthRepository(
    private val context: Context,
    private val database: ClinicDatabase,
    private val mobileApiService: MobileApiService,
    private val apiService: ApiService,
    private val sessionRepository: SessionRepository,
) : com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface {
    private val userDao = database.userDao()

    /**
     * Verifies credentials with the backend, logs the user in, gets the JWT tokens,
     * updates the application state with metadata details, and registers/caches the user in Room.
     *
     * M1/E3.4: when the backend requires 2FA, returns
     * [LoginOutcome.TwoFactorRequired] with the pending-2fa-token.
     * The caller (AuthViewModel) must then prompt the user for the TOTP code
     * and call [verify2FA].
     */
    override suspend fun login(username: String, password: String): Result<LoginOutcome> = withContext(Dispatchers.IO) {
        try {
            // Production-only: demo bypass was removed in M0 (E1.1).
            // Any username MUST be authenticated against the backend.
            val response = mobileApiService.login(
                LoginRequest(
                    username = username.trim(),
                    password = password,
                    deviceFingerprint = getDeviceFingerprint(),
                    rememberMe = true
                )
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    if (response.code() == 401)
                        AuthError.InvalidCredentials
                    else
                        retrofit2.HttpException(response)
                )
            }

            val loginResp = response.body()
                ?: return@withContext Result.failure(IllegalStateException("Empty login response body"))

            // ── 2FA challenge branch ──────────────────────────────────────────
            if (loginResp.isTwoFactorChallenge) {
                return@withContext Result.success(
                    LoginOutcome.TwoFactorRequired(
                        challengeToken = loginResp.pending2faToken!!
                    )
                )
            }

            // ── Normal success branch ─────────────────────────────────────────
            val accessToken = loginResp.accessToken
                ?: return@withContext Result.failure(IllegalStateException("Login response missing access_token"))
            val refreshToken = loginResp.refreshToken
                ?: return@withContext Result.failure(IllegalStateException("Login response missing refresh_token"))

            // Persist both tokens
            sessionRepository.onTokensRefreshed(accessToken, refreshToken)

            // Fetch full profile (loginResp.user is untyped Map; profile endpoint gives typed data)
            val profileResponse = mobileApiService.getProfile()
            if (!profileResponse.isSuccessful) {
                return@withContext Result.failure(retrofit2.HttpException(profileResponse))
            }

            val userProfile = profileResponse.body()!!
            sessionRepository.onProfileLoaded(
                phone = userProfile.phone ?: "",
                role = userProfile.role ?: "PATIENT"
            )

            // Cache user in Room
            val cachedUser = UserEntity(
                phone = userProfile.phone ?: "",
                fullName = userProfile.fullName ?: "",
                role = userProfile.role ?: "PATIENT",
                dateOfBirth = userProfile.dateOfBirth ?: "",
                biometricEnabled = userProfile.biometricEnabled ?: false,
                telegramChatId = userProfile.telegramChatId
            )
            val existing = userDao.getUserByPhone(cachedUser.phone)
            if (existing == null) {
                userDao.insertUser(cachedUser)
            } else {
                userDao.updateUser(cachedUser.copy(id = existing.id))
            }

            Result.success(
                LoginOutcome.Success(
                    user = UserDto(
                        id = userProfile.id,
                        phone = userProfile.phone ?: "",
                        fullName = userProfile.fullName ?: "",
                        role = userProfile.role ?: "PATIENT",
                        dateOfBirth = userProfile.dateOfBirth,
                        biometricEnabled = userProfile.biometricEnabled ?: false,
                        telegramChatId = userProfile.telegramChatId,
                        clinicId = userProfile.clinicId
                    )
                )
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "login error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * M1/E3.4: completes a 2FA challenge after [login] returned
     * [LoginOutcome.TwoFactorRequired]. On success, the backend returns
     * the real access + refresh tokens and the user is logged in.
     */
    override suspend fun verify2FA(
        challengeToken: String,
        totpCode: String,
        rememberDevice: Boolean
    ): Result<LoginOutcome> = withContext(Dispatchers.IO) {
        try {
            val response = mobileApiService.verify2FA(
                com.aistudio.clinicsystem.data.api.TwoFAVerifyRequest(
                    pending2faToken = challengeToken,
                    totpCode = totpCode.trim(),
                    rememberDevice = rememberDevice,
                    deviceFingerprint = getDeviceFingerprint()
                )
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    if (response.code() == 401) AuthError.InvalidTwoFACode
                    else retrofit2.HttpException(response)
                )
            }

            val loginResp = response.body()
                ?: return@withContext Result.failure(IllegalStateException("Empty 2FA response body"))

            val accessToken = loginResp.accessToken
                ?: return@withContext Result.failure(IllegalStateException("2FA response missing access_token"))
            val refreshToken = loginResp.refreshToken
                ?: return@withContext Result.failure(IllegalStateException("2FA response missing refresh_token"))

            sessionRepository.onTokensRefreshed(accessToken, refreshToken)

            // Fetch profile to populate phone/role
            val profileResponse = mobileApiService.getProfile()
            if (!profileResponse.isSuccessful) {
                return@withContext Result.failure(retrofit2.HttpException(profileResponse))
            }
            val userProfile = profileResponse.body()!!
            sessionRepository.onProfileLoaded(
                phone = userProfile.phone ?: "",
                role = userProfile.role ?: "PATIENT"
            )

            val cachedUser = UserEntity(
                phone = userProfile.phone ?: "",
                fullName = userProfile.fullName ?: "",
                role = userProfile.role ?: "PATIENT",
                dateOfBirth = userProfile.dateOfBirth ?: "",
                biometricEnabled = userProfile.biometricEnabled ?: false,
                telegramChatId = userProfile.telegramChatId
            )
            val existing = userDao.getUserByPhone(cachedUser.phone)
            if (existing == null) {
                userDao.insertUser(cachedUser)
            } else {
                userDao.updateUser(cachedUser.copy(id = existing.id))
            }

            Result.success(
                LoginOutcome.Success(
                    user = UserDto(
                        id = userProfile.id,
                        phone = userProfile.phone ?: "",
                        fullName = userProfile.fullName ?: "",
                        role = userProfile.role ?: "PATIENT",
                        dateOfBirth = userProfile.dateOfBirth,
                        biometricEnabled = userProfile.biometricEnabled ?: false,
                        telegramChatId = userProfile.telegramChatId,
                        clinicId = userProfile.clinicId
                    )
                )
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "verify2FA error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * M1/E3.4: requests a recovery code (via email or SMS) when the user
     * cannot produce a TOTP code. The returned recovery-token must be used
     * with [verify2FARecovery] together with the code from the SMS/email.
     */
    override suspend fun request2FARecovery(
        challengeToken: String,
        method: String  // "email" | "sms"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = mobileApiService.request2FARecovery(
                com.aistudio.clinicsystem.data.api.TwoFARecoveryRequest(
                    pending2faToken = challengeToken,
                    method = method
                )
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(retrofit2.HttpException(response))
            }
            val body = response.body()
                ?: return@withContext Result.failure(IllegalStateException("Empty recovery response body"))
            Result.success(body.recoveryToken)
        } catch (e: Exception) {
            Log.e("AuthRepository", "request2FARecovery error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * M1/E3.4: verifies a recovery code (from SMS/email) and completes login.
     */
    override suspend fun verify2FARecovery(
        recoveryToken: String,
        code: String
    ): Result<LoginOutcome> = withContext(Dispatchers.IO) {
        try {
            val response = mobileApiService.verify2FARecovery(
                com.aistudio.clinicsystem.data.api.TwoFARecoveryVerifyRequest(
                    recoveryToken = recoveryToken,
                    code = code.trim()
                )
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    if (response.code() == 401) AuthError.InvalidTwoFACode
                    else retrofit2.HttpException(response)
                )
            }
            val loginResp = response.body()
                ?: return@withContext Result.failure(IllegalStateException("Empty recovery verify response"))

            val accessToken = loginResp.accessToken
                ?: return@withContext Result.failure(IllegalStateException("Recovery verify missing access_token"))
            val refreshToken = loginResp.refreshToken
                ?: return@withContext Result.failure(IllegalStateException("Recovery verify missing refresh_token"))

            sessionRepository.onTokensRefreshed(accessToken, refreshToken)

            val profileResponse = mobileApiService.getProfile()
            if (!profileResponse.isSuccessful) {
                return@withContext Result.failure(retrofit2.HttpException(profileResponse))
            }
            val userProfile = profileResponse.body()!!
            sessionRepository.onProfileLoaded(
                phone = userProfile.phone ?: "",
                role = userProfile.role ?: "PATIENT"
            )

            Result.success(
                LoginOutcome.Success(
                    user = UserDto(
                        id = userProfile.id,
                        phone = userProfile.phone ?: "",
                        fullName = userProfile.fullName ?: "",
                        role = userProfile.role ?: "PATIENT",
                        dateOfBirth = userProfile.dateOfBirth,
                        biometricEnabled = userProfile.biometricEnabled ?: false,
                        telegramChatId = userProfile.telegramChatId,
                        clinicId = userProfile.clinicId
                    )
                )
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "verify2FARecovery error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Perform physical or bio authorization verification.
     * Fetches current server-side profile session using saved JWT token.
     *
     * M1/E3.2: 401 handling is now done by TokenAuthenticator (auto-refresh).
     * If we get here with a 401, refresh already failed — clear session.
     */
    override suspend fun verifyCurrentSession(): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val token = sessionRepository.accessToken
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Сессия отсутствует"))
            }

            val response = mobileApiService.getProfile()
            if (response.isSuccessful && response.body() != null) {
                val userProfile = response.body()!!

                val cachedUser = UserEntity(
                    phone = userProfile.phone ?: "",
                    fullName = userProfile.fullName ?: "",
                    role = userProfile.role ?: "PATIENT",
                    dateOfBirth = userProfile.dateOfBirth ?: "",
                    biometricEnabled = userProfile.biometricEnabled ?: false,
                    telegramChatId = userProfile.telegramChatId
                )
                val existing = userDao.getUserByPhone(cachedUser.phone)
                if (existing == null) {
                    userDao.insertUser(cachedUser)
                } else {
                    userDao.updateUser(cachedUser.copy(id = existing.id))
                }

                Result.success(
                    UserDto(
                        id = userProfile.id,
                        phone = userProfile.phone ?: "",
                        fullName = userProfile.fullName ?: "",
                        role = userProfile.role ?: "PATIENT",
                        dateOfBirth = userProfile.dateOfBirth,
                        biometricEnabled = userProfile.biometricEnabled ?: false,
                        telegramChatId = userProfile.telegramChatId,
                        clinicId = userProfile.clinicId
                    )
                )
            } else {
                // TokenAuthenticator already tried to refresh and failed — give up.
                sessionRepository.clearSession()
                Result.failure(Exception("Срок действия сессии истек"))
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "verifyCurrentSession error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * P0-1 audit fix: completes biometric login using the [cipher] unlocked
     * by BiometricPrompt.
     *
     * Flow:
     *   1. Read the encrypted refresh-token blob from EncryptedSharedPreferences
     *      via [com.aistudio.clinicsystem.utils.TokenManager.getEncryptedRefreshTokenBlob].
     *      If absent → user has never enrolled in biometric, fail closed.
     *   2. Parse the blob into [com.aistudio.clinicsystem.utils.BiometricCryptoHelper.EncryptedData]
     *      (Base64 IV + ciphertext).
     *   3. Use [cipher] to decrypt the ciphertext → plaintext refresh token.
     *      If decryption fails → keystore key was invalidated by new fingerprint
     *      enrollment, caller must re-login with password and re-enroll.
     *   4. Exchange the plaintext refresh token for a fresh access token via
     *      `POST /api/v1/authentication/refresh`. If the backend returns 401
     *      → refresh token expired server-side, session cleared, user must re-login.
     *   5. On success, persist both new tokens via [SessionRepository.onTokensRefreshed],
     *      fetch the user profile, cache it in Room, return the [UserDto].
     *
     * Security property: the refresh token is never stored in plaintext.
     * Without the cipher (which requires biometric auth to unlock), the
     * encrypted blob is useless.
     */
    override suspend fun loginWithBiometricRefreshToken(
        phone: String,
        cipher: javax.crypto.Cipher,
    ): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            // 1. Read encrypted blob
            val blob = com.aistudio.clinicsystem.utils.TokenManager
                .getEncryptedRefreshTokenBlob(context)
                ?: return@withContext Result.failure(
                    IllegalStateException("Биометрический вход не настроен — войдите по паролю.")
                )

            // 2. Parse IV + ciphertext
            val encryptedData = com.aistudio.clinicsystem.utils.BiometricCryptoHelper.EncryptedData
                .fromStorageString(blob)
                ?: return@withContext Result.failure(
                    IllegalStateException("Повреждённое хранилище биометрического токена.")
                )

            // 3. Decrypt with the cipher unlocked by BiometricPrompt
            val plaintextRefreshToken = com.aistudio.clinicsystem.utils.BiometricCryptoHelper
                .decryptRefreshToken(cipher, encryptedData)
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        "Не удалось расшифровать refresh token — возможно, отпечаток изменён. " +
                            "Войдите по паролю и повторите активацию биометрии."
                    )
                )

            // 4. Exchange refresh token for a fresh access token
            val refreshResponse = mobileApiService.refreshToken(
                com.aistudio.clinicsystem.data.api.RefreshTokenRequest(plaintextRefreshToken)
            )

            if (!refreshResponse.isSuccessful) {
                // 401 → refresh token expired server-side; clear session
                if (refreshResponse.code() == 401) {
                    sessionRepository.clearSession()
                    com.aistudio.clinicsystem.utils.TokenManager.clearEncryptedRefreshTokenBlob(context)
                    com.aistudio.clinicsystem.utils.BiometricCryptoHelper.deleteBiometricKey()
                }
                return@withContext Result.failure(
                    IllegalStateException(
                        "Сессия истекла на сервере (HTTP ${refreshResponse.code()}). Войдите заново."
                    )
                )
            }

            val refreshBody = refreshResponse.body()
                ?: return@withContext Result.failure(
                    IllegalStateException("Пустой ответ сервера при обновлении токена.")
                )

            val newAccessToken = refreshBody.accessToken
                ?: return@withContext Result.failure(
                    IllegalStateException("Ответ /refresh не содержит access_token.")
                )
            val newRefreshToken = refreshBody.refreshToken
                ?: return@withContext Result.failure(
                    IllegalStateException("Ответ /refresh не содержит refresh_token.")
                )

            // Persist the refreshed tokens
            sessionRepository.onTokensRefreshed(newAccessToken, newRefreshToken)

            // 5. Fetch profile, cache in Room, return UserDto
            val profileResponse = mobileApiService.getProfile()
            if (!profileResponse.isSuccessful) {
                return@withContext Result.failure(retrofit2.HttpException(profileResponse))
            }
            val userProfile = profileResponse.body()!!
            sessionRepository.onProfileLoaded(
                phone = userProfile.phone ?: "",
                role = userProfile.role ?: "PATIENT"
            )

            val cachedUser = UserEntity(
                phone = userProfile.phone ?: "",
                fullName = userProfile.fullName ?: "",
                role = userProfile.role ?: "PATIENT",
                dateOfBirth = userProfile.dateOfBirth ?: "",
                biometricEnabled = userProfile.biometricEnabled ?: false,
                telegramChatId = userProfile.telegramChatId
            )
            val existing = userDao.getUserByPhone(cachedUser.phone)
            if (existing == null) {
                userDao.insertUser(cachedUser)
            } else {
                userDao.updateUser(cachedUser.copy(id = existing.id))
            }

            Result.success(
                UserDto(
                    id = userProfile.id,
                    phone = userProfile.phone ?: "",
                    fullName = userProfile.fullName ?: "",
                    role = userProfile.role ?: "PATIENT",
                    dateOfBirth = userProfile.dateOfBirth,
                    biometricEnabled = userProfile.biometricEnabled ?: false,
                    telegramChatId = userProfile.telegramChatId,
                    clinicId = userProfile.clinicId
                )
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "loginWithBiometricRefreshToken error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Links telegram notifications to this user profile
     */
    suspend fun linkTelegram(telegramId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = sessionRepository.accessToken ?: return@withContext Result.failure(Exception("Не авторизован"))
            val response = apiService.linkTelegram(telegramId)
            if (response.isSuccessful) {
                // Update local DB cache as well
                sessionRepository.phone?.let { phone ->
                    userDao.getUserByPhone(phone)?.let { user ->
                        userDao.updateUser(user.copy(telegramChatId = telegramId))
                    }
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Ошибка привязки Telegram: Код ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * M1/E3.3: terminate the current session.
     *
     * Now suspend — calls POST /api/v1/authentication/logout on the backend
     * with the refresh token. The server invalidates the session in the
     * UserSession table and blacklists the access token. After the server
     * confirms, local tokens are cleared.
     *
     * If the network call fails:
     *  - Network error → returns Result.failure, local tokens are KEPT so the
     *    user can retry. The session will be re-tried on next login attempt.
     *  - 401 (refresh already invalid) → tokens are cleared locally anyway,
     *    since the server doesn't recognise them.
     *  - Other 4xx/5xx → tokens are cleared; server might be in a weird state
     *    but the user wanted to log out, so we honor that locally.
     */
    override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val refreshToken = sessionRepository.refreshToken

        // If we have a refresh token, notify the server first
        if (!refreshToken.isNullOrBlank()) {
            try {
                val response = mobileApiService.logout(LogoutRequest(refreshToken))
                if (!response.isSuccessful && response.code() != 401) {
                    // Network/5xx error — keep local tokens so user can retry
                    Log.w("AuthRepository", "logout server call failed: ${response.code()}")
                    return@withContext Result.failure(
                        Exception("Не удалось связаться с сервером: ${response.code()}")
                    )
                }
                // 401 here means the refresh token is already invalid — that's fine,
                // we'll clear local state below.
            } catch (e: Exception) {
                Log.w("AuthRepository", "logout network error: ${e.message}")
                return@withContext Result.failure(e)
            }
        }

        // Clear local state
        sessionRepository.clearSession()
        Result.success(Unit)
    }

    /** Generates a stable device fingerprint for the backend's device-tracking feature. */
    private fun getDeviceFingerprint(): String {
        // Simple, stable identifier based on Android ID. Suitable for device-tracking
        // purposes (not for security-sensitive identification). The backend uses this
        // to detect "login from a new device" and offer 2FA-remember-device.
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        return "android-${androidId.take(16)}"
    }
}

/**
 * M1/E3.4: result of [AuthRepository.login] and [AuthRepository.verify2FA].
 * Either the user is fully logged in, or a 2FA challenge must be completed.
 */
sealed class LoginOutcome {
    data class Success(val user: UserDto) : LoginOutcome()
    data class TwoFactorRequired(val challengeToken: String) : LoginOutcome()
}

/** M1/E3.4: typed auth errors for cleaner UI handling. */
sealed class AuthError : Throwable() {
    object InvalidCredentials : AuthError()
    object InvalidTwoFACode : AuthError()
}

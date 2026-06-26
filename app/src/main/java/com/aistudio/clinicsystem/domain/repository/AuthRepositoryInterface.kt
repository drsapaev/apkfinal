package com.aistudio.clinicsystem.domain.repository

import com.aistudio.clinicsystem.domain.model.LoginResult
import com.aistudio.clinicsystem.domain.model.User

interface AuthRepositoryInterface {
    suspend fun login(username: String, password: String): Result<LoginResult>
    suspend fun verify2FA(challengeToken: String, totpCode: String, rememberDevice: Boolean): Result<LoginResult>
    suspend fun request2FARecovery(challengeToken: String, method: String): Result<String>
    suspend fun verify2FARecovery(recoveryToken: String, code: String): Result<LoginResult>
    suspend fun verifyCurrentSession(): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun linkTelegram(telegramId: String): Result<Unit>
}

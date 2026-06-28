package com.aistudio.clinicsystem.domain.repository

import com.aistudio.clinicsystem.domain.model.LoginResult
import com.aistudio.clinicsystem.domain.model.User

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

    suspend fun logout(): Result<Unit>
}

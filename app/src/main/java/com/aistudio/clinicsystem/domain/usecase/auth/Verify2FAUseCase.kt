package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 5.4 (C-3 fix): Verify2FAUseCase — validates TOTP code format
 * before delegating to the repository.
 */
@Singleton
class Verify2FAUseCase @Inject constructor(
    private val authRepository: AuthRepositoryInterface,
) {
    suspend operator fun invoke(
        challengeToken: String,
        totpCode: String,
        rememberDevice: Boolean,
    ): Result<LoginOutcome> {
        if (challengeToken.isBlank()) {
            return Result.failure(IllegalStateException("Сессия 2FA истекла, войдите заново"))
        }
        if (totpCode.length != 6 || !totpCode.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Код должен состоять из 6 цифр"))
        }
        return authRepository.verify2FA(challengeToken, totpCode, rememberDevice)
    }
}

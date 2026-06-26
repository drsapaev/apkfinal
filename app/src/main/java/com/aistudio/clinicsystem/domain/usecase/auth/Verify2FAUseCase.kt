package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.model.LoginResult
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface

class Verify2FAUseCase(private val authRepository: AuthRepositoryInterface) {
    suspend operator fun invoke(challengeToken: String, totpCode: String, rememberDevice: Boolean): Result<LoginResult> {
        if (challengeToken.isBlank()) return Result.failure(IllegalStateException("Сессия 2FA истекла, войдите заново"))
        if (totpCode.length != 6 || !totpCode.all { it.isDigit() }) return Result.failure(IllegalArgumentException("Код должен состоять из 6 цифр"))
        return authRepository.verify2FA(challengeToken, totpCode, rememberDevice)
    }
}

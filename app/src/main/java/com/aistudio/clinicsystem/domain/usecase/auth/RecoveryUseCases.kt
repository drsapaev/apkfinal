package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface

class Request2FARecoveryUseCase(private val authRepository: AuthRepositoryInterface) {
    suspend operator fun invoke(challengeToken: String, method: String): Result<String> {
        if (method !in listOf("email", "sms")) return Result.failure(IllegalArgumentException("Метод восстановления должен быть email или sms"))
        return authRepository.request2FARecovery(challengeToken, method)
    }
}

class Verify2FARecoveryUseCase(private val authRepository: AuthRepositoryInterface) {
    suspend operator fun invoke(recoveryToken: String, code: String) = authRepository.verify2FARecovery(recoveryToken, code)
}

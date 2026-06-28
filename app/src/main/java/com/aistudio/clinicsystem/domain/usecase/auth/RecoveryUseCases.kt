package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Request2FARecoveryUseCase @Inject constructor(
    private val authRepository: AuthRepositoryInterface,
) {
    suspend operator fun invoke(challengeToken: String, method: String): Result<String> {
        if (method !in listOf("email", "sms")) {
            return Result.failure(IllegalArgumentException("Метод восстановления должен быть email или sms"))
        }
        return authRepository.request2FARecovery(challengeToken, method)
    }
}

@Singleton
class Verify2FARecoveryUseCase @Inject constructor(
    private val authRepository: AuthRepositoryInterface,
) {
    suspend operator fun invoke(recoveryToken: String, code: String): Result<LoginOutcome> =
        authRepository.verify2FARecovery(recoveryToken, code)
}

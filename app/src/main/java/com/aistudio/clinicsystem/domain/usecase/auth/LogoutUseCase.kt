package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 5.4 (C-3 fix): LogoutUseCase is now @Singleton + @Inject.
 * Called by AuthViewModel and ClinicViewModel on user logout.
 */
@Singleton
class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepositoryInterface,
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.logout()
}

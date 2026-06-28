package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.api.UserDto
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 5.4 (C-3 fix): VerifySessionUseCase is now @Singleton + @Inject.
 * Called by SessionRepository.restoreSession() and ClinicViewModel.refreshSession().
 */
@Singleton
class VerifySessionUseCase @Inject constructor(
    private val authRepository: AuthRepositoryInterface,
) {
    suspend operator fun invoke(): Result<UserDto> = authRepository.verifyCurrentSession()
}

package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface

class LogoutUseCase(private val authRepository: AuthRepositoryInterface) {
    suspend operator fun invoke(): Result<Unit> = authRepository.logout()
}

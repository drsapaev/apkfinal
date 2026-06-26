package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.model.User
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface

class VerifySessionUseCase(private val authRepository: AuthRepositoryInterface) {
    suspend operator fun invoke(): Result<User> = authRepository.verifyCurrentSession()
}

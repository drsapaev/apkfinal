package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 5.4 (C-3 fix): LoginUseCase is now @Singleton + @Inject, wired
 * via Hilt. AuthViewModel calls this UseCase instead of calling
 * AuthRepository.login() directly.
 *
 * Validates input before delegating to the repository.
 */
@Singleton
class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepositoryInterface,
) {
    suspend operator fun invoke(username: String, password: String): Result<LoginOutcome> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Имя пользователя не может быть пустым"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Пароль не может быть пустым"))
        }
        return authRepository.login(username.trim(), password)
    }
}

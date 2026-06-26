package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.model.LoginResult
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface

class LoginUseCase(private val authRepository: AuthRepositoryInterface) {
    suspend operator fun invoke(username: String, password: String): Result<LoginResult> {
        if (username.isBlank()) return Result.failure(IllegalArgumentException("Имя пользователя не может быть пустым"))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Пароль не может быть пустым"))
        return authRepository.login(username.trim(), password)
    }
}

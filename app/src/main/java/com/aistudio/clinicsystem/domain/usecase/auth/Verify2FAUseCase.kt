package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 5.4 (C-3 fix): Verify2FAUseCase — validates the verification code
 * before delegating to the repository.
 *
 * M-CONTRACT-FIX: accepts BOTH
 *  - a 6-digit TOTP code (→ backend `totp_code`), and
 *  - an 8-10 char alphanumeric backup code (→ backend `backup_code`).
 * The backend's TwoFactorVerifyRequest has separate fields and rejects
 * requests carrying neither; backup codes are the only mid-login-challenge
 * recovery path the backend supports.
 */
@Singleton
class Verify2FAUseCase
    @Inject
    constructor(
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
            val code = totpCode.trim()
            val isTotp = code.length == 6 && code.all { it.isDigit() }
            val isBackup = code.length in 8..10 && code.all { it.isLetterOrDigit() }
            if (!isTotp && !isBackup) {
                return Result.failure(
                    IllegalArgumentException("Код должен состоять из 6 цифр (TOTP) или быть резервным кодом (8-10 символов)"),
                )
            }
            return authRepository.verify2FA(challengeToken, code, rememberDevice)
        }
    }

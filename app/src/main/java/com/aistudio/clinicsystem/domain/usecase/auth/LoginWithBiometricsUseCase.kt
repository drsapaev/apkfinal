package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.api.UserDto
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-5 audit fix: LoginWithBiometricsUseCase — completes biometric
 * login by delegating to [AuthRepositoryInterface.loginWithBiometricRefreshToken].
 *
 * Added in P0-1 (biometric cipher fix) but not extracted into a use case
 * at the time. High-5 wires it up so AuthViewModel has a consistent
 * layering: ViewModel → UseCase → Repository, with no direct
 * repository calls.
 *
 * The cipher is the one unlocked by BiometricPrompt in AuthScreen.kt.
 * It was initialised for DECRYPTION with the IV stored alongside the
 * encrypted refresh-token blob in EncryptedSharedPreferences.
 *
 * Security: the cipher must NOT be null — if the caller cannot supply
 * a cipher, biometric login is impossible. The use case fails closed
 * with a clear error rather than silently falling back to a non-biometric
 * path.
 */
@Singleton
class LoginWithBiometricsUseCase @Inject constructor(
    private val authRepository: AuthRepositoryInterface,
) {
    suspend operator fun invoke(phone: String, cipher: Cipher?): Result<UserDto> {
        if (cipher == null) {
            return Result.failure(
                IllegalStateException("Биометрический ключ недоступен. Войдите по паролю.")
            )
        }
        if (phone.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Телефон не может быть пустым")
            )
        }
        return authRepository.loginWithBiometricRefreshToken(phone.trim(), cipher)
    }
}

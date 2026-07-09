package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.api.UserDto
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.Cipher

/**
 * High-5 audit fix: unit tests for [LoginWithBiometricsUseCase].
 *
 * The use case wraps [AuthRepositoryInterface.loginWithBiometricRefreshToken]
 * with input validation (cipher != null, phone not blank) before delegating
 * to the repository. These tests verify the validation logic and the
 * pass-through behavior on success/failure.
 */
class LoginWithBiometricsUseCaseTest {

    private lateinit var authRepository: AuthRepositoryInterface
    private lateinit var useCase: LoginWithBiometricsUseCase

    private val testUser = UserDto(
        id = 1, phone = "+77771112233", fullName = "Test User",
        role = "PATIENT", dateOfBirth = "1990-01-01", biometricEnabled = true,
        telegramChatId = null,
    )

    @Before
    fun setUp() {
        authRepository = mockk(relaxed = true)
        useCase = LoginWithBiometricsUseCase(authRepository)
    }

    @Test
    fun `invoke with null cipher returns failure without calling repository`() = runBlocking {
        val cipher = mockk<Cipher>(relaxed = true)
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.success(testUser)

        val result = useCase("+77771112233", cipher = null)

        assertTrue("Should fail on null cipher", result.isFailure)
        assertEquals(
            "Биометрический ключ недоступен. Войдите по паролю.",
            result.exceptionOrNull()?.message,
        )
        // Repository must NOT be called when cipher is null — fail closed.
        io.mockk.coVerify(exactly = 0) {
            authRepository.loginWithBiometricRefreshToken(any(), any())
        }
    }

    @Test
    fun `invoke with blank phone returns failure without calling repository`() = runBlocking {
        val cipher = mockk<Cipher>(relaxed = true)

        val result = useCase("   ", cipher)

        assertTrue("Should fail on blank phone", result.isFailure)
        assertEquals(
            "Телефон не может быть пустым",
            result.exceptionOrNull()?.message,
        )
        io.mockk.coVerify(exactly = 0) {
            authRepository.loginWithBiometricRefreshToken(any(), any())
        }
    }

    @Test
    fun `invoke with valid inputs delegates to repository and returns success`() = runBlocking {
        val cipher = mockk<Cipher>(relaxed = true)
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.success(testUser)

        val result = useCase("+77771112233", cipher)

        assertTrue("Should succeed on valid inputs", result.isSuccess)
        assertEquals(testUser, result.getOrNull())
    }

    @Test
    fun `invoke trims phone before delegating`() = runBlocking {
        val cipher = mockk<Cipher>(relaxed = true)
        coEvery {
            authRepository.loginWithBiometricRefreshToken("+77771112233", cipher)
        } returns Result.success(testUser)

        val result = useCase("  +77771112233  ", cipher)

        assertTrue(result.isSuccess)
        io.mockk.coVerify(exactly = 1) {
            authRepository.loginWithBiometricRefreshToken("+77771112233", cipher)
        }
    }

    @Test
    fun `invoke propagates repository failure`() = runBlocking {
        val cipher = mockk<Cipher>(relaxed = true)
        val errorMsg = "Не удалось расшифровать refresh token"
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.failure(IllegalStateException(errorMsg))

        val result = useCase("+77771112233", cipher)

        assertTrue("Should propagate failure", result.isFailure)
        assertEquals(errorMsg, result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke propagates repository failure on 401 expired session`() = runBlocking {
        val cipher = mockk<Cipher>(relaxed = true)
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.failure(IllegalStateException("Сессия истекла на сервере (HTTP 401). Войдите заново."))

        val result = useCase("+77771112233", cipher)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Сессия истекла") == true,
        )
    }
}

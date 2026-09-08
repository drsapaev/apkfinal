package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.data.api.UserDto
import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.domain.model.LoginResult
import com.aistudio.clinicsystem.domain.model.User
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import com.aistudio.clinicsystem.domain.model.UserRole
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * M3A/E6.1: Unit tests for [LoginUseCase].
 *
 * Tests input validation and delegation to the repository.
 * Uses mockk for the AuthRepositoryInterface — no Robolectric needed.
 */
class LoginUseCaseTest {

    private lateinit var repository: AuthRepositoryInterface
    private lateinit var useCase: LoginUseCase

    private val userDto = UserDto(
        id = 1,
        phone = "+77771112233",
        fullName = "Test User",
        role = "PATIENT",
        dateOfBirth = null,
        biometricEnabled = false,
        telegramChatId = null,
    )

    private val testUser = User(
        id = "1",
        phone = "+77771112233",
        fullName = "Test User",
        role = UserRole.PATIENT
    )

    @Before
    fun setUp() {
        repository = mockk()
        useCase = LoginUseCase(repository)
    }

    @Test
    fun `blank username returns failure`() = runBlocking {
        val result = useCase.invoke("", "password")
        assertTrue("Blank username should fail", result.isFailure)
        assertTrue(
            "Error should be IllegalArgumentException",
            result.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun `blank password returns failure`() = runBlocking {
        val result = useCase.invoke("user", "")
        assertTrue("Blank password should fail", result.isFailure)
    }

    @Test
    fun `valid credentials delegate to repository`() = runBlocking {
        coEvery { repository.login("user", "pass") } returns
            Result.success(LoginOutcome.Success(user = userDto))

        val result = useCase.invoke("user", "pass")

        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Result should be Success", result.getOrThrow() is LoginOutcome.Success)
    }

    @Test
    fun `2FA challenge is propagated`() = runBlocking {
        coEvery { repository.login(any(), any()) } returns
            Result.success(LoginOutcome.TwoFactorRequired("challenge-token"))

        val result = useCase.invoke("user", "pass")

        assertTrue("Should succeed at UseCase level", result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue("Should be TwoFactorRequired", outcome is LoginOutcome.TwoFactorRequired)
        assertEquals("challenge-token", (outcome as LoginOutcome.TwoFactorRequired).challengeToken)
    }

    @Test
    fun `repository failure is propagated`() = runBlocking {
        val error = RuntimeException("Network error")
        coEvery { repository.login(any(), any()) } returns Result.failure(error)

        val result = useCase.invoke("user", "pass")

        assertTrue("Should fail", result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}

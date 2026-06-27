package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.model.LoginResult
import com.aistudio.clinicsystem.domain.model.User
import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import com.aistudio.clinicsystem.data.model.UserRole
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * M3A/E6.1: Unit tests for [Verify2FAUseCase].
 *
 * Validates TOTP code format (6 digits) before delegating to repository.
 */
class Verify2FAUseCaseTest {

    private lateinit var repository: AuthRepositoryInterface
    private lateinit var useCase: Verify2FAUseCase

    private val testUser = User(
        id = "1", phone = "+77771112233", fullName = "Test User", role = UserRole.PATIENT
    )

    @Before
    fun setUp() {
        repository = mockk()
        useCase = Verify2FAUseCase(repository)
    }

    @Test
    fun `blank challenge token returns failure`() = runBlocking {
        val result = useCase.invoke("", "123456", false)
        assertTrue("Blank challenge should fail", result.isFailure)
        assertTrue(
            "Should be IllegalStateException",
            result.exceptionOrNull() is IllegalStateException
        )
    }

    @Test
    fun `code shorter than 6 digits returns failure`() = runBlocking {
        val result = useCase.invoke("challenge", "12345", false)
        assertTrue("Short code should fail", result.isFailure)
        assertTrue(
            "Should be IllegalArgumentException",
            result.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun `code longer than 6 digits returns failure`() = runBlocking {
        val result = useCase.invoke("challenge", "1234567", false)
        assertTrue("Long code should fail", result.isFailure)
    }

    @Test
    fun `code with letters returns failure`() = runBlocking {
        val result = useCase.invoke("challenge", "12345a", false)
        assertTrue("Non-digit code should fail", result.isFailure)
    }

    @Test
    fun `valid 6-digit code delegates to repository`() = runBlocking {
        coEvery { repository.verify2FA("challenge", "123456", true) } returns
            Result.success(LoginResult.Success(testUser))

        val result = useCase.invoke("challenge", "123456", true)

        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should be Success", result.getOrThrow() is LoginResult.Success)
    }

    @Test
    fun `repository failure is propagated`() = runBlocking {
        coEvery { repository.verify2FA(any(), any(), any()) } returns
            Result.failure(RuntimeException("Invalid code"))

        val result = useCase.invoke("challenge", "123456", false)

        assertTrue("Should fail", result.isFailure)
    }
}

package com.aistudio.clinicsystem.domain.usecase.auth

import com.aistudio.clinicsystem.domain.repository.AuthRepositoryInterface
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M3A/E6.1: Unit tests for [LogoutUseCase].
 *
 * Verifies delegation to repository.logout().
 */
class LogoutUseCaseTest {

    private lateinit var repository: AuthRepositoryInterface
    private lateinit var useCase: LogoutUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = LogoutUseCase(repository)
    }

    @Test
    fun `logout delegates to repository`() = runBlocking {
        coEvery { repository.logout() } returns Result.success(Unit)

        val result = useCase.invoke()

        assertTrue("Should succeed", result.isSuccess)
        coVerify(exactly = 1) { repository.logout() }
    }

    @Test
    fun `logout failure is propagated`() = runBlocking {
        val error = RuntimeException("Network error")
        coEvery { repository.logout() } returns Result.failure(error)

        val result = useCase.invoke()

        assertTrue("Should fail", result.isFailure)
        assertTrue(
            "Should propagate the same error",
            result.exceptionOrNull() === error
        )
    }
}

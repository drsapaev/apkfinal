package com.aistudio.clinicsystem.domain.usecase.sync

import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M3A/E6.4: Unit tests for [SyncAllFromServerUseCase] and [RetryPendingSyncsUseCase].
 *
 * These UseCases are thin wrappers — they just delegate to the repository.
 * Tests verify delegation and result propagation.
 */
class SyncUseCasesTest {

    private lateinit var repository: ClinicRepositoryInterface
    private lateinit var syncUseCase: SyncAllFromServerUseCase
    private lateinit var retryUseCase: RetryPendingSyncsUseCase

    @Before
    fun setUp() {
        repository = mockk()
        syncUseCase = SyncAllFromServerUseCase(repository)
        retryUseCase = RetryPendingSyncsUseCase(repository)
    }

    @Test
    fun `syncAll delegates with token`() = runBlocking {
        coEvery { repository.syncAllFromServer("token123") } returns Result.success(true)

        val result = syncUseCase.invoke("token123")

        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should return true", result.getOrThrow())
        coVerify(exactly = 1) { repository.syncAllFromServer("token123") }
    }

    @Test
    fun `syncAll with null token delegates`() = runBlocking {
        coEvery { repository.syncAllFromServer(null) } returns Result.success(false)

        val result = syncUseCase.invoke(null)

        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should return false", !result.getOrThrow()!!)
    }

    @Test
    fun `syncAll failure is propagated`() = runBlocking {
        coEvery { repository.syncAllFromServer(any()) } returns
            Result.failure(RuntimeException("Network"))

        val result = syncUseCase.invoke("token")

        assertTrue("Should fail", result.isFailure)
    }

    @Test
    fun `retryPendingSyncs delegates with token`() = runBlocking {
        coEvery { repository.retryPendingSyncs("token123") } returns Result.success(true)

        val result = retryUseCase.invoke("token123")

        assertTrue("Should succeed", result.isSuccess)
        coVerify(exactly = 1) { repository.retryPendingSyncs("token123") }
    }

    @Test
    fun `retryPendingSyncs failure is propagated`() = runBlocking {
        coEvery { repository.retryPendingSyncs(any()) } returns
            Result.failure(RuntimeException("DB error"))

        val result = retryUseCase.invoke("token")

        assertTrue("Should fail", result.isFailure)
    }
}

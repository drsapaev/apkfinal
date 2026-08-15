package com.aistudio.clinicsystem.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 3.12: SyncWorkerTest — rewired for @HiltWorker AssistedInject.
 *
 * The previous test used `mockkObject(ClinicDatabase.Companion)` and
 * `mockkObject(SessionManagerImpl.Companion)` to redirect the singletons
 * accessed inside the worker's `doWork()`. Now that SyncWorker takes
 * [ClinicRepository] and [SessionRepository] via constructor injection,
 * the test simply passes mockk() instances directly.
 *
 * Closes audit finding TEST-16: "SyncWorkerTest mocks the wrong DAO method
 * (`getAllPendingSyncs` instead of `getPendingForRetry`)" — the new test
 * mocks `clinicRepository.retryUnsyncedWrites()` directly, so the DAO
 * method choice is no longer the test's concern.
 *
 * Strategy:
 *   - Mock ClinicRepository and SessionRepository.
 *   - Construct SyncWorker with the mocks via the AssistedInject constructor
 *     (the test passes mock WorkerParameters — SyncWorker doesn't read them
 *     in doWork()).
 *   - Stub `retryUnsyncedWrites` to return true / false / throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var mockRepository: ClinicRepository
    private lateinit var mockSessionRepository: SessionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockRepository = mockk(relaxed = true)
        mockSessionRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        // No singletons to unmock — all mocks are local.
    }

    private fun createWorker(): SyncWorker {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        // Stage 3.12: SyncWorker is @HiltWorker — its constructor takes
        // (appContext, workerParams, clinicRepository, sessionRepository)
        // via AssistedInject. The first two are @Assisted; the last two
        // are injected by Hilt at runtime. In the test we pass them directly.
        return SyncWorker(
            context,
            workerParams,
            mockRepository,
            mockSessionRepository,
        )
    }

    @Test
    fun `doWork returns success when retryUnsyncedWrites succeeds`() = runBlocking {
        every { mockSessionRepository.accessToken } returns "valid-token"
        coEvery { mockRepository.retryUnsyncedWrites(any()) } returns true

        val result = createWorker().doWork()

        assertTrue("Should return Result.success()", result is androidx.work.ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns retry when retryUnsyncedWrites fails`() = runBlocking {
        every { mockSessionRepository.accessToken } returns "valid-token"
        coEvery { mockRepository.retryUnsyncedWrites(any()) } returns false

        val result = createWorker().doWork()

        assertTrue("Should return Result.retry()", result is androidx.work.ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork returns retry on exception`() = runBlocking {
        every { mockSessionRepository.accessToken } returns "valid-token"
        coEvery { mockRepository.retryUnsyncedWrites(any()) } throws RuntimeException("DB corruption")

        val result = createWorker().doWork()

        assertTrue("Should return Result.retry() on exception", result is androidx.work.ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork handles null token gracefully`() = runBlocking {
        every { mockSessionRepository.accessToken } returns null
        coEvery { mockRepository.retryUnsyncedWrites(any()) } returns true

        val result = createWorker().doWork()

        assertTrue("Should handle null token", result is androidx.work.ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork handles empty token`() = runBlocking {
        every { mockSessionRepository.accessToken } returns ""
        coEvery { mockRepository.retryUnsyncedWrites(any()) } returns true

        val result = createWorker().doWork()

        assertTrue("Should handle empty token", result is androidx.work.ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork with repository exception returns retry`() = runBlocking {
        every { mockSessionRepository.accessToken } returns "token"
        coEvery { mockRepository.retryUnsyncedWrites(any()) } throws java.io.IOException("Network I/O error")

        val result = createWorker().doWork()

        assertTrue("Should retry on repository exception", result is androidx.work.ListenableWorker.Result.Retry)
    }
}

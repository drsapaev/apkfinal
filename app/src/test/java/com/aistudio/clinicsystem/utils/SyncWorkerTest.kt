package com.aistudio.clinicsystem.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M3A/E6.4: Unit tests for [SyncWorker].
 *
 * Tests the background sync worker:
 *   1. retryUnsyncedWrites returns true → Result.success()
 *   2. retryUnsyncedWrites returns false → Result.retry()
 *   3. Exception during sync → Result.retry()
 *   4. Null token (not logged in) → still attempts sync
 *   5. Empty token → still attempts sync
 *   6. Network error → Result.retry()
 *
 * Strategy:
 *   - mockkObject(ClinicDatabase) to return mock DB
 *   - mockkObject(SessionManagerImpl) to return mock session manager
 *   - Create SyncWorker with mock WorkerParameters
 *   - ClinicRepository is created inside doWork — we mock its methods
 *     by making the mock DB return mock DAOs that don't crash
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var mockDatabase: ClinicDatabase
    private lateinit var mockSessionManager: SessionManagerImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Mock ClinicDatabase singleton — return a relaxed mock that won't crash
        // when ClinicRepository accesses its DAOs
        mockDatabase = mockk(relaxed = true)
        mockkObject(ClinicDatabase.Companion)
        every { ClinicDatabase.getDatabase(any()) } returns mockDatabase

        // Mock SessionManagerImpl singleton
        mockSessionManager = mockk(relaxed = true)
        mockkObject(SessionManagerImpl.Companion)
        every { SessionManagerImpl.getInstance(any()) } returns mockSessionManager
    }

    @After
    fun tearDown() {
        unmockkObject(ClinicDatabase.Companion)
        unmockkObject(SessionManagerImpl.Companion)
    }

    private fun createWorker(): SyncWorker {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        return SyncWorker(context, workerParams)
    }

    @Test
    fun `doWork returns success when retryUnsyncedWrites succeeds`() = runBlocking {
        every { mockSessionManager.getToken() } returns "valid-token"

        // ClinicRepository is created with mockDatabase (relaxed) — its
        // retryUnsyncedWrites will use the mock DAOs which return empty lists.
        // We need to make retryUnsyncedWrites return true.
        // Since ClinicRepository is real (not mocked), we mock the pendingSyncDao
        // to return empty list (which makes retryUnsyncedWrites return true).
        val mockPendingSyncDao = mockk<com.aistudio.clinicsystem.data.db.PendingSyncDao>(relaxed = true)
        every { mockDatabase.pendingSyncDao() } returns mockPendingSyncDao
        coEvery { mockPendingSyncDao.getAllPendingSyncs() } returns emptyList()

        val result = createWorker().doWork()

        assertTrue("Should return Result.success()", result is androidx.work.ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns retry when retryUnsyncedWrites fails`() = runBlocking {
        every { mockSessionManager.getToken() } returns "valid-token"

        // Make pendingSyncDao return items but the API call will fail
        val mockPendingSyncDao = mockk<com.aistudio.clinicsystem.data.db.PendingSyncDao>(relaxed = true)
        every { mockDatabase.pendingSyncDao() } returns mockPendingSyncDao
        val pendingSync = com.aistudio.clinicsystem.data.db.PendingSyncEntity(
            type = "CREATE_APPOINTMENT",
            payload = "{}",
            clientRequestId = "req-1"
        )
        coEvery { mockPendingSyncDao.getAllPendingSyncs() } returns listOf(pendingSync)

        // The repository will try to process this pending sync but will fail
        // because the mock DAOs are relaxed (return defaults) and the API
        // service is not configured. retryUnsyncedWrites returns false when
        // not all items succeed.
        val result = createWorker().doWork()

        assertTrue("Should return Result.retry()", result is androidx.work.ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork returns retry on exception`() = runBlocking {
        every { mockSessionManager.getToken() } returns "valid-token"

        // Make the database throw an exception when accessed
        every { mockDatabase.pendingSyncDao() } throws RuntimeException("DB corruption")

        val result = createWorker().doWork()

        assertTrue("Should return Result.retry() on exception", result is androidx.work.ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork handles null token gracefully`() = runBlocking {
        every { mockSessionManager.getToken() } returns null

        val mockPendingSyncDao = mockk<com.aistudio.clinicsystem.data.db.PendingSyncDao>(relaxed = true)
        every { mockDatabase.pendingSyncDao() } returns mockPendingSyncDao
        coEvery { mockPendingSyncDao.getAllPendingSyncs() } returns emptyList()

        val result = createWorker().doWork()

        assertTrue("Should handle null token", result is androidx.work.ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork handles empty token`() = runBlocking {
        every { mockSessionManager.getToken() } returns ""

        val mockPendingSyncDao = mockk<com.aistudio.clinicsystem.data.db.PendingSyncDao>(relaxed = true)
        every { mockDatabase.pendingSyncDao() } returns mockPendingSyncDao
        coEvery { mockPendingSyncDao.getAllPendingSyncs() } returns emptyList()

        val result = createWorker().doWork()

        assertTrue("Should handle empty token", result is androidx.work.ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork with database error returns retry`() = runBlocking {
        every { mockSessionManager.getToken() } returns "token"
        every { mockDatabase.pendingSyncDao() } throws java.io.IOException("DB I/O error")

        val result = createWorker().doWork()

        assertTrue("Should retry on DB error", result is androidx.work.ListenableWorker.Result.Retry)
    }
}

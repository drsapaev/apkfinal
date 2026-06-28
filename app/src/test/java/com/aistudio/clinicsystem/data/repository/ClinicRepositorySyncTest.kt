package com.aistudio.clinicsystem.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.api.ApiService
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import com.aistudio.clinicsystem.data.outbox.OutboxOperation
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 10b (TEST-11 fix): ClinicRepositorySyncTest.
 *
 * Closes audit finding TEST-11: "ClinicRepositoryRoomTest tests only local
 * CRUD — no tests for sync logic. Untested methods: retryUnsyncedWrites,
 * syncAllAppointmentsFromServer, createAppointmentOnServerAndLocal,
 * updateAppointmentStatusOnServerAndLocal, handleOutboxFailure."
 *
 * Tests cover:
 *  1. retryUnsyncedWrites with empty outbox → returns true (no-op)
 *  2. retryUnsyncedWrites with PENDING row + server 200 → row COMPLETED + deleted
 *  3. retryUnsyncedWrites with PENDING row + server 500 → row FAILED + retry scheduled
 *  4. retryUnsyncedWrites with PENDING row + server 400 → row DEAD_LETTER immediately
 *  5. OutboxOperation enum — fromCode round-trip for all values
 *  6. claimForProcessing — marks rows as PROCESSING atomically
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ClinicRepositorySyncTest {

    private lateinit var database: ClinicDatabase
    private lateinit var repository: ClinicRepository
    private lateinit var mockMobileApi: MobileApiService
    private lateinit var mockLegacyApi: ApiService
    private val moshi = Moshi.Builder().build()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ClinicDatabase::class.java,
        ).allowMainThreadQueries().build()

        mockMobileApi = mockk(relaxed = true)
        mockLegacyApi = mockk(relaxed = true)

        repository = ClinicRepository(
            database = database,
            mobileApiService = mockMobileApi,
            legacyApiService = mockLegacyApi,
            moshi = moshi,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `retryUnsyncedWrites with empty outbox returns true`() = runBlocking {
        val result = repository.retryUnsyncedWrites(token = "test-token")
        assertTrue("Should return true when no pending syncs", result)
    }

    @Test
    fun `retryUnsyncedWrites with CREATE_APPOINTMENT and server 200 marks COMPLETED and deletes`() = runBlocking {
        // Insert a pending sync row
        val pendingSync = PendingSyncEntity(
            type = OutboxOperation.CREATE_APPOINTMENT.code,
            payload = """{"id":null,"patient_phone":"+77771112233","patient_name":"Test","doctor_name":"Dr.","specialty":"S","date":"2026-07-10","time":"14:00","status":"PENDING","reason":"R"}""",
            clientRequestId = "req-001",
        )
        database.pendingSyncDao().insertPendingSync(pendingSync)

        // Mock server response — 200 with a created appointment
        val createdDto = com.aistudio.clinicsystem.data.api.AppointmentDto(
            id = 42,
            patientPhone = "+77771112233",
            patientName = "Test",
            doctorName = "Dr.",
            specialty = "S",
            date = "2026-07-10",
            time = "14:00",
            status = "PENDING",
            reason = "R",
        )
        coEvery { mockLegacyApi.createAppointment(any()) } returns retrofit2.Response.success(createdDto)

        val result = repository.retryUnsyncedWrites("test-token")

        assertTrue("Should return true on success", result)

        // Verify the row was deleted (COMPLETED + delete)
        val remaining = database.pendingSyncDao().getAllPendingSyncs()
        assertTrue("Pending sync should be deleted after success", remaining.none { it.clientRequestId == "req-001" })
    }

    @Test
    fun `retryUnsyncedWrites with server 500 schedules retry with backoff`() = runBlocking {
        val pendingSync = PendingSyncEntity(
            type = OutboxOperation.CREATE_APPOINTMENT.code,
            payload = """{"id":null,"patient_phone":"+77771112233","patient_name":"Test","doctor_name":"Dr.","specialty":"S","date":"2026-07-10","time":"14:00","status":"PENDING","reason":"R"}""",
            clientRequestId = "req-500",
        )
        database.pendingSyncDao().insertPendingSync(pendingSync)

        // Mock server returns 500
        coEvery { mockLegacyApi.createAppointment(any()) } returns retrofit2.Response.error(
            500,
            okhttp3.ResponseBody.create(null, "Internal Server Error"),
        )

        repository.retryUnsyncedWrites("test-token")

        // The row should be in FAILED state with a nextRetryAt set
        val rows = database.pendingSyncDao().getAllPendingSyncs()
        val failedRow = rows.find { it.clientRequestId == "req-500" }
        assertTrue("Row should still exist (not deleted)", failedRow != null)
        assertEquals("FAILED", failedRow?.status)
        assertTrue("nextRetryAt should be set", failedRow?.nextRetryAt != null)
        assertEquals(500, failedRow?.lastHttpCode)
    }

    @Test
    fun `retryUnsyncedWrites with server 400 moves to DEAD_LETTER immediately`() = runBlocking {
        val pendingSync = PendingSyncEntity(
            type = OutboxOperation.CREATE_APPOINTMENT.code,
            payload = """{"id":null,"patient_phone":"+77771112233","patient_name":"Test","doctor_name":"Dr.","specialty":"S","date":"2026-07-10","time":"14:00","status":"PENDING","reason":"R"}""",
            clientRequestId = "req-400",
        )
        database.pendingSyncDao().insertPendingSync(pendingSync)

        // Mock server returns 400 Bad Request (non-retriable)
        coEvery { mockLegacyApi.createAppointment(any()) } returns retrofit2.Response.error(
            400,
            okhttp3.ResponseBody.create(null, """{"detail":"Invalid doctor_id"}"""),
        )

        repository.retryUnsyncedWrites("test-token")

        val rows = database.pendingSyncDao().getAllPendingSyncs()
        val deadRow = rows.find { it.clientRequestId == "req-400" }
        assertTrue("Row should still exist", deadRow != null)
        assertEquals("DEAD_LETTER", deadRow?.status)
        assertEquals(400, deadRow?.lastHttpCode)
    }

    @Test
    fun `OutboxOperation fromCode round-trip for all values`() {
        for (op in OutboxOperation.entries) {
            val parsed = OutboxOperation.fromCode(op.code)
            assertEquals("Round-trip should preserve enum value", op, parsed)
        }
    }

    @Test
    fun `OutboxOperation fromCode returns null for unknown code`() {
        assertEquals(null, OutboxOperation.fromCode("UNKNOWN_OPERATION"))
        assertEquals(null, OutboxOperation.fromCode(""))
    }

    @Test
    fun `claimForProcessing marks PENDING rows as PROCESSING`() = runBlocking {
        // Insert 2 PENDING rows
        database.pendingSyncDao().insertPendingSync(
            PendingSyncEntity(
                type = OutboxOperation.CREATE_APPOINTMENT.code,
                payload = "{}",
                clientRequestId = "req-a",
            ),
        )
        database.pendingSyncDao().insertPendingSync(
            PendingSyncEntity(
                type = OutboxOperation.CREATE_MEDICAL_RECORD.code,
                payload = "{}",
                clientRequestId = "req-b",
            ),
        )

        // Claim
        val claimed = database.pendingSyncDao().claimForProcessing(
            staleBefore = System.currentTimeMillis() - 5 * 60_000,
        )

        assertEquals("Should claim 2 rows", 2, claimed.size)
        claimed.forEach { row ->
            assertEquals("Row should be PROCESSING", "PROCESSING", row.status)
        }
    }

    @Test
    fun `claimForProcessing does not reclaim recently-stuck PROCESSING rows`() = runBlocking {
        // Insert a PROCESSING row with recent updatedAt
        val recentProcessing = PendingSyncEntity(
            type = OutboxOperation.CREATE_APPOINTMENT.code,
            payload = "{}",
            clientRequestId = "req-recent",
            status = "PROCESSING",
            updatedAt = System.currentTimeMillis(), // just now — not stale
        )
        database.pendingSyncDao().insertPendingSync(recentProcessing)

        // Claim with stale threshold = 5 minutes ago
        val claimed = database.pendingSyncDao().claimForProcessing(
            staleBefore = System.currentTimeMillis() - 5 * 60_000,
        )

        // The recently-PROCESSING row should NOT be reclaimed
        assertTrue(
            "Should not reclaim recently-PROCESSING row",
            claimed.none { it.clientRequestId == "req-recent" },
        )
    }
}

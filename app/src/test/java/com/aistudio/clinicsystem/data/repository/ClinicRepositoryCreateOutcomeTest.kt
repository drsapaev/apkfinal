package com.aistudio.clinicsystem.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.api.ApiService
import com.aistudio.clinicsystem.data.api.AppointmentBookRequest
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.api.MobileAppointmentOut
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Codex P1/P2 (#150) regression: the CREATE booking flow must distinguish
 * a RETRIABLE server failure (500/503/408/429/401) from a hard rejection.
 *
 * - retriable  → local row stays QUEUED, the outbox row stays PENDING and
 *                will be replayed verbatim on the SAME route later;
 * - 4xx        → local row becomes REJECTED and the outbox row is
 *                dead-lettered immediately (no pointless retries);
 * - successful outbox replay clears the QUEUED marker (SYNC_STATE_CLEAN).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ClinicRepositoryCreateOutcomeTest {
    private lateinit var database: ClinicDatabase
    private lateinit var repository: ClinicRepository
    private lateinit var mockMobileApi: MobileApiService
    private lateinit var mockLegacyApi: ApiService

    private val moshi = Moshi.Builder().build()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    ClinicDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        mockMobileApi = mockk(relaxed = true)
        mockLegacyApi = mockk(relaxed = true)

        repository =
            ClinicRepository(
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

    private fun errorResponse(code: Int): Response<MobileAppointmentOut> =
        Response.error(code, "{\"detail\":\"x\"}".toResponseBody("application/json".toMediaType()))

    private fun bookingArgs(): Map<String, Any?> =
        mapOf(
            "token" to "test-token",
            "patientId" to null,
            "patientPhone" to "+77771112233",
            "patientName" to "Test Patient",
            "doctorId" to 5,
            "doctorName" to "Dr. Five",
            "specialty" to "Cardiology",
            "date" to "2026-09-15",
            "time" to "10:30",
            "reason" to "checkup",
        )

    @Test
    fun `create with retriable 503 stays queued and outbox row stays pending`() =
        runBlocking {
            coEvery { mockMobileApi.bookAppointment(any<AppointmentBookRequest>()) } returns errorResponse(503)

            val saved =
                repository.createAppointmentOnServerAndLocal(
                    token = "test-token",
                    patientId = null,
                    patientPhone = "+77771112233",
                    patientName = "Test Patient",
                    doctorId = 5,
                    doctorName = "Dr. Five",
                    specialty = "Cardiology",
                    date = "2026-09-15",
                    time = "10:30",
                    reason = "checkup",
                )

            assertEquals(
                "retriable failure must NOT be reported as rejected",
                AppointmentEntity.SYNC_STATE_QUEUED,
                saved.syncState,
            )
            val pending = database.pendingSyncDao().getAllPendingSyncs()
            assertTrue("outbox row must remain for retry", pending.isNotEmpty())
            assertEquals("PENDING", pending.first().status)
        }

    @Test
    fun `create with 422 is rejected and dead-lettered`() =
        runBlocking {
            coEvery { mockMobileApi.bookAppointment(any<AppointmentBookRequest>()) } returns errorResponse(422)

            val saved =
                repository.createAppointmentOnServerAndLocal(
                    token = "test-token",
                    patientId = null,
                    patientPhone = "+77771112233",
                    patientName = "Test Patient",
                    doctorId = 5,
                    doctorName = "Dr. Five",
                    specialty = "Cardiology",
                    date = "2026-09-15",
                    time = "10:30",
                    reason = "checkup",
                )

            assertEquals(
                "hard rejection must mark the row REJECTED",
                AppointmentEntity.SYNC_STATE_REJECTED,
                saved.syncState,
            )
            val pending = database.pendingSyncDao().getAllPendingSyncs()
            assertTrue("row must be dead-lettered, not pending", pending.isEmpty() || pending.first().status != "PENDING")
        }

    @Test
    fun `confirmed create stores serverId and clean sync state`() =
        runBlocking {
            val body =
                Moshi
                    .Builder()
                    .build()
                    .adapter(MobileAppointmentOut::class.java)
                    .fromJson(
                        """{"id":42,"doctor_name":"Dr. Five","specialty":"Cardiology",
                           "appointment_date":"2026-09-15T10:30:00","clinic_address":"addr","status":"scheduled"}""",
                    )
            assertNotNull(body)
            coEvery { mockMobileApi.bookAppointment(any<AppointmentBookRequest>()) } returns Response.success(body!!)

            val saved =
                repository.createAppointmentOnServerAndLocal(
                    token = "test-token",
                    patientId = null,
                    patientPhone = "+77771112233",
                    patientName = "Test Patient",
                    doctorId = 5,
                    doctorName = "Dr. Five",
                    specialty = "Cardiology",
                    date = "2026-09-15",
                    time = "10:30",
                    reason = "checkup",
                )

            assertEquals(42, saved.serverId)
            assertEquals(AppointmentEntity.SYNC_STATE_CLEAN, saved.syncState)
        }
}

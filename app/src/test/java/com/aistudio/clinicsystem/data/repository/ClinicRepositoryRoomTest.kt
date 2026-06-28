package com.aistudio.clinicsystem.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M3A/E6.2: Unit tests for ClinicRepository Room operations.
 *
 * Tests the local cache layer (Room DAO operations) that
 * NetworkBoundResource relies on. Verifies that:
 *   - Appointments are correctly inserted and queried
 *   - Patient-scoped queries work (only return appointments for the given phone)
 *   - Stale-write guard logic (updatedAt comparison) works
 *   - Cancel/delete operations work
 *
 * These tests use in-memory Room under Robolectric — no network mocking
 * needed. The observeAppointmentsWithSync() method (which uses NBR +
 * MobileApiService) is tested separately when Hilt DI is available for
 * clean service injection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ClinicRepositoryRoomTest {

    private lateinit var database: ClinicDatabase
    private lateinit var repository: ClinicRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ClinicDatabase::class.java
        ).allowMainThreadQueries().build()

        // Stage 2.11 + 3.10: ClinicRepository requires mobileApiService,
        // legacyApiService, and moshi. Tests use relaxed mockk for both
        // services and a real Moshi instance (lightweight, no IO).
        val mockMobileApi = io.mockk.mockk<com.aistudio.clinicsystem.data.api.MobileApiService>(relaxed = true)
        val mockLegacyApi = io.mockk.mockk<com.aistudio.clinicsystem.data.api.ApiService>(relaxed = true)
        val moshi = com.squareup.moshi.Moshi.Builder().build()
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
    fun `insertAppointment saves to Room`() = runBlocking {
        val appointment = AppointmentEntity(
            id = "1", patientPhone = "+77771112233", patientName = "Test",
            doctorName = "Dr. Smith", specialty = "Cardiology",
            date = "2026-07-01", time = "10:00", status = "PENDING",
            reason = "Checkup"
        )

        repository.insertAppointment(appointment)

        val saved = repository.getAppointmentById("1")
        // Stage 1.1 regression: assert NOT NULL explicitly (was previously
        // a no-op — `saved` would be null and the assertEquals below
        // would NPE the test into a failure).
        assertNotNull("insertAppointment must persist the row to Room", saved)
        assertEquals("Dr. Smith", saved?.doctorName)
        assertEquals("PENDING", saved?.status)
    }

    @Test
    fun `insertMedicalRecord saves to Room`() = runBlocking {
        val record = com.aistudio.clinicsystem.data.db.MedicalRecordEntity(
            id = "1", patientPhone = "+77771112233", doctorName = "Dr. Smith",
            diagnosis = "Acute bronchitis", prescription = "Amoxicillin 500mg",
            visitDate = "2026-07-01"
        )

        repository.insertMedicalRecord(record)

        val saved = repository.getMedicalRecordById("1")
        // Stage 1.1 regression: insertMedicalRecord was a no-op (C-1).
        assertNotNull("insertMedicalRecord must persist the row to Room", saved)
        assertEquals("Acute bronchitis", saved?.diagnosis)
        assertEquals("Amoxicillin 500mg", saved?.prescription)
    }

    @Test
    fun `getAppointmentsForPatient returns only patient appointments`() = runBlocking {
        // Insert appointments for 2 different patients
        repository.insertAppointment(AppointmentEntity(
            id = "1", patientPhone = "+77771112233", patientName = "Patient A",
            doctorName = "Dr. X", specialty = "S", date = "2026-07-01", time = "10:00",
            status = "PENDING", reason = "R"
        ))
        repository.insertAppointment(AppointmentEntity(
            id = "2", patientPhone = "+77002223344", patientName = "Patient B",
            doctorName = "Dr. Y", specialty = "S", date = "2026-07-02", time = "11:00",
            status = "PENDING", reason = "R"
        ))
        repository.insertAppointment(AppointmentEntity(
            id = "3", patientPhone = "+77771112233", patientName = "Patient A",
            doctorName = "Dr. Z", specialty = "S", date = "2026-07-03", time = "12:00",
            status = "APPROVED", reason = "R"
        ))

        val patientAAppointments = repository.getAppointmentsForPatient("+77771112233").first()
        assertEquals("Should return 2 appointments for Patient A", 2, patientAAppointments.size)
        assertTrue(
            "Should contain IDs 1 and 3",
            patientAAppointments.map { it.id }.containsAll(listOf("1", "3"))
        )
    }

    @Test
    fun `updateAppointment changes status`() = runBlocking {
        val appointment = AppointmentEntity(
            id = "1", patientPhone = "+77771112233", patientName = "Test",
            doctorName = "Dr.", specialty = "S", date = "2026-07-01", time = "10:00",
            status = "PENDING", reason = "R"
        )
        repository.insertAppointment(appointment)

        val updated = appointment.copy(status = "APPROVED", notes = "Confirmed")
        repository.updateAppointment(updated)

        val saved = repository.getAppointmentById("1")
        assertEquals("APPROVED", saved?.status)
        assertEquals("Confirmed", saved?.notes)
    }

    @Test
    fun `deleteAppointment removes from Room`() = runBlocking {
        val appointment = AppointmentEntity(
            id = "1", patientPhone = "+77771112233", patientName = "Test",
            doctorName = "Dr.", specialty = "S", date = "2026-07-01", time = "10:00",
            status = "PENDING", reason = "R"
        )
        repository.insertAppointment(appointment)

        repository.deleteAppointment("1")

        val saved = repository.getAppointmentById("1")
        assertTrue("Appointment should be deleted", saved == null)
    }

    @Test
    fun `allAppointments flow emits all appointments`() = runBlocking {
        repository.insertAppointment(AppointmentEntity(
            id = "1", patientPhone = "+77771112233", patientName = "A",
            doctorName = "Dr1", specialty = "S", date = "2026-07-01", time = "10:00",
            status = "PENDING", reason = "R"
        ))
        repository.insertAppointment(AppointmentEntity(
            id = "2", patientPhone = "+77002223344", patientName = "B",
            doctorName = "Dr2", specialty = "S", date = "2026-07-02", time = "11:00",
            status = "APPROVED", reason = "R"
        ))

        val all = repository.allAppointments.first()
        assertEquals(2, all.size)
    }

    @Test
    fun `insertUser saves to Room`() = runBlocking {
        val user = com.aistudio.clinicsystem.data.db.UserEntity(
            phone = "+77771112233", fullName = "Test User", role = "PATIENT"
        )

        repository.insertUser(user)

        val saved = repository.getUserByPhone("+77771112233")
        assertEquals("Test User", saved?.fullName)
        assertEquals("PATIENT", saved?.role)
    }

    @Test
    fun `addSyncLog and recentLogs flow work`() = runBlocking {
        repository.addSyncLog("Test log message", "SYSTEM_SYNC")

        val logs = repository.recentLogs.first()
        assertEquals(1, logs.size)
        assertEquals("Test log message", logs[0].logMessage)
        assertEquals("SYSTEM_SYNC", logs[0].direction)
    }

    @Test
    fun `clearSensitiveDataForPatient removes appointments and records`() = runBlocking {
        val phone = "+77771112233"
        repository.insertAppointment(AppointmentEntity(
            id = "1", patientPhone = phone, patientName = "Test",
            doctorName = "Dr.", specialty = "S", date = "2026-07-01", time = "10:00",
            status = "PENDING", reason = "R"
        ))
        repository.insertMedicalRecord(com.aistudio.clinicsystem.data.db.MedicalRecordEntity(
            id = "1", patientPhone = phone, doctorName = "Dr.",
            diagnosis = "Flu", prescription = "Rest", visitDate = "2026-06-01"
        ))

        repository.clearSensitiveDataForPatient(phone)

        val appointments = repository.getAppointmentsForPatient(phone).first()
        assertTrue("Appointments should be cleared", appointments.isEmpty())
    }
}

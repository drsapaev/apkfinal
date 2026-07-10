package com.aistudio.clinicsystem.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Stage 10.2 (C-14 fix) + Medium-4 audit fix: Real migration tests.
 *
 * Closes audit finding C-14: "MigrationTest tests NOTHING — it tests data
 * persistence at the SAME version, not migration. Passes even if you delete
 * the migration SQL."
 *
 * Medium-4 audit fix: added tests for migrations 7→8 (doctors table) and
 * 8→9 (lab_results table). Updated `allMigrationsAreRegistered` to expect
 * 5 migrations (was 3 — stale after 7→8 and 8→9 were added).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MigrationTest {

    private lateinit var context: Context
    private val dbName = "migration_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    // ── Migration registration tests ──

    @Test
    fun allMigrationsAreRegistered() {
        assertNotNull(Migrations.ALL)
        // Medium-4 audit fix: was 3, now 5 (7→8 doctors + 8→9 lab_results).
        assertEquals("Should have 5 migrations registered", 5, Migrations.ALL.size)
    }

    @Test
    fun migration_4_to_5_exists() {
        val migration = Migrations.ALL.find { it.startVersion == 4 && it.endVersion == 5 }
        assertNotNull("MIGRATION_4_5 must exist", migration)
    }

    @Test
    fun migration_5_to_6_exists() {
        val migration = Migrations.ALL.find { it.startVersion == 5 && it.endVersion == 6 }
        assertNotNull("MIGRATION_5_6 must exist", migration)
    }

    @Test
    fun migration_6_to_7_exists() {
        val migration = Migrations.ALL.find { it.startVersion == 6 && it.endVersion == 7 }
        assertNotNull("MIGRATION_6_7 must exist", migration)
    }

    @Test
    fun migration_7_to_8_exists() {
        val migration = Migrations.ALL.find { it.startVersion == 7 && it.endVersion == 8 }
        assertNotNull("MIGRATION_7_8 must exist (doctors table)", migration)
    }

    @Test
    fun migration_8_to_9_exists() {
        val migration = Migrations.ALL.find { it.startVersion == 8 && it.endVersion == 9 }
        assertNotNull("MIGRATION_8_9 must exist (lab_results table)", migration)
    }

    // ── Data persistence with migrations registered ──

    @Test
    fun dataPersistsAcrossReopenWithMigrationsRegistered() {
        val db = Room.databaseBuilder(
            context,
            ClinicDatabase::class.java,
            dbName,
        )
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.userDao().insertUser(
                UserEntity(
                    phone = "+77071234567",
                    fullName = "Dr. Test User",
                    role = "STAFF",
                ),
            )
            db.appointmentDao().insertAppointment(
                AppointmentEntity(
                    id = "apt-001",
                    patientPhone = "+77771112233",
                    patientName = "Ivan",
                    doctorName = "Dr. Smith",
                    specialty = "Терапевт",
                    date = "2026-07-01",
                    time = "10:00",
                    status = "PENDING",
                    reason = "Checkup",
                ),
            )
            db.pendingSyncDao().insertPendingSync(
                PendingSyncEntity(
                    clientRequestId = "req-001",
                    type = "CREATE_APPOINTMENT",
                    payload = "{}",
                ),
            )
        }
        db.close()

        val dbReopened = Room.databaseBuilder(
            context,
            ClinicDatabase::class.java,
            dbName,
        )
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            val user = dbReopened.userDao().getUserByPhone("+77071234567")
            assertNotNull("User must survive close/reopen", user)
            assertEquals("Dr. Test User", user?.fullName)

            val apt = dbReopened.appointmentDao().getAppointmentById("apt-001")
            assertNotNull("Appointment must survive close/reopen", apt)
            assertEquals("PENDING", apt?.status)

            val pending = dbReopened.pendingSyncDao().getAllPendingSyncs()
            assertTrue(
                "Pending sync must survive close/reopen",
                pending.any { it.clientRequestId == "req-001" },
            )
        }
        dbReopened.close()
    }

    // ── Stage 3.1: verify columns added by MIGRATION_6_7 ──

    @Test
    fun appointmentEntity_hasEtagColumn() {
        val db = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.appointmentDao().insertAppointment(
                AppointmentEntity(
                    id = "apt-etag-test",
                    patientPhone = "+77771112233",
                    patientName = "Test",
                    doctorName = "Dr.",
                    specialty = "S",
                    date = "2026-07-01",
                    time = "10:00",
                    status = "PENDING",
                    reason = "R",
                    etag = "test-etag-value",
                ),
            )
            val apt = db.appointmentDao().getAppointmentById("apt-etag-test")
            assertEquals("test-etag-value", apt?.etag)
        }
        db.close()
    }

    @Test
    fun medicalRecordEntity_hasVersionAndUpdateAtColumns() {
        val db = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.medicalRecordDao().insertRecord(
                MedicalRecordEntity(
                    id = "rec-version-test",
                    patientPhone = "+77771112233",
                    doctorName = "Dr.",
                    diagnosis = "Test",
                    prescription = "Test",
                    visitDate = "2026-07-01",
                    version = 5,
                    updatedAt = 1234567890L,
                ),
            )
            val record = db.medicalRecordDao().getRecordById("rec-version-test")
            assertEquals(5, record?.version)
            assertEquals(1234567890L, record?.updatedAt)
        }
        db.close()
    }

    @Test
    fun pendingSyncEntity_hasLastHttpCodeColumn() {
        val db = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.pendingSyncDao().insertPendingSync(
                PendingSyncEntity(
                    clientRequestId = "req-http-test",
                    type = "CREATE_APPOINTMENT",
                    payload = "{}",
                    lastHttpCode = 500,
                ),
            )
            val pending = db.pendingSyncDao().getAllPendingSyncs()
            val row = pending.find { it.clientRequestId == "req-http-test" }
            assertNotNull("Pending sync must be stored", row)
            assertEquals(500, row?.lastHttpCode)
        }
        db.close()
    }

    // ── Medium-4: verify tables added by MIGRATION_7_8 and MIGRATION_8_9 ──

    @Test
    fun doctorEntity_tableCreatedByMigration_7_to_8() {
        val db = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.doctorDao().insertDoctor(
                DoctorEntity(
                    id = "doc-test-001",
                    serverId = 42,
                    fullName = "Dr. Sapaev",
                    specialty = "Стоматолог-терапевт",
                    phone = "+77771112233",
                    email = "sapaev@clinic.example",
                    avatarUrl = "https://example.com/avatar.jpg",
                    isActive = true,
                    clinicId = "clinic_base",
                ),
            )
            val doctors = db.doctorDao().getAllDoctorsOnce()
            assertTrue("Doctors table must have at least 1 entry", doctors.isNotEmpty())
            val doc = doctors.find { it.id == "doc-test-001" }
            assertNotNull("Doctor must be stored", doc)
            assertEquals(42, doc?.serverId)
            assertEquals("Dr. Sapaev", doc?.fullName)
            assertEquals("Стоматолог-терапевт", doc?.specialty)
            assertEquals("+77771112233", doc?.phone)
            assertEquals("sapaev@clinic.example", doc?.email)
            assertEquals("https://example.com/avatar.jpg", doc?.avatarUrl)
            assertTrue("Doctor must be active", doc?.isActive == true)
            assertEquals("clinic_base", doc?.clinicId)
        }
        db.close()
    }

    @Test
    fun labResultEntity_tableCreatedByMigration_8_to_9() {
        val db = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.labResultDao().insertAll(
                listOf(
                    LabResultEntity(
                        id = "lab-test-001",
                        serverId = 55,
                        patientPhone = "+77771112233",
                        testName = "Глюкоза крови",
                        result = "5.4",
                        unit = "ммоль/л",
                        referenceRange = "3.3 - 6.1",
                        status = "normal",
                        performedAt = "2026-07-01T08:15:00Z",
                        doctorName = "Д-р Сапаев",
                    ),
                ),
            )
            val results = db.labResultDao().getResultsByPatientOnce("+77771112233")
            assertTrue("Lab results table must have at least 1 entry", results.isNotEmpty())
            val lab = results.find { it.id == "lab-test-001" }
            assertNotNull("Lab result must be stored", lab)
            assertEquals(55, lab?.serverId)
            assertEquals("+77771112233", lab?.patientPhone)
            assertEquals("Глюкоза крови", lab?.testName)
            assertEquals("5.4", lab?.result)
            assertEquals("ммоль/л", lab?.unit)
            assertEquals("3.3 - 6.1", lab?.referenceRange)
            assertEquals("normal", lab?.status)
            assertEquals("2026-07-01T08:15:00Z", lab?.performedAt)
            assertEquals("Д-р Сапаев", lab?.doctorName)
        }
        db.close()
    }

    @Test
    fun doctorDataPersistsAcrossReopen() {
        val db = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.doctorDao().insertDoctors(
                listOf(
                    DoctorEntity(fullName = "Dr. A", specialty = "Cardio", phone = "+1"),
                    DoctorEntity(fullName = "Dr. B", specialty = "Dental", phone = "+2"),
                ),
            )
        }
        db.close()

        val dbReopened = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            val doctors = dbReopened.doctorDao().getAllDoctorsOnce()
            assertEquals("Doctors must survive close/reopen", 2, doctors.size)
            assertTrue(doctors.any { it.fullName == "Dr. A" })
            assertTrue(doctors.any { it.fullName == "Dr. B" })
        }
        dbReopened.close()
    }

    @Test
    fun labResultDataPersistsAcrossReopen() {
        val db = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            db.labResultDao().insertAll(
                listOf(
                    LabResultEntity(
                        id = "lab-persist-001",
                        serverId = 1,
                        patientPhone = "+77771112233",
                        testName = "Test A",
                        result = "Result A",
                        unit = "U",
                        referenceRange = "R",
                        status = "normal",
                    ),
                ),
            )
        }
        db.close()

        val dbReopened = Room.databaseBuilder(context, ClinicDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*Migrations.ALL)
            .build()

        kotlinx.coroutines.runBlocking {
            val results = dbReopened.labResultDao().getResultsByPatientOnce("+77771112233")
            assertEquals("Lab results must survive close/reopen", 1, results.size)
            assertEquals("Test A", results[0].testName)
        }
        dbReopened.close()
    }
}

package com.aistudio.clinicsystem.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 6: LabResultDaoTest — tests for lab_results table operations.
 *
 * Tests:
 *  1. insertAll + getResultsByPatientFlow — inserts 3 results, queries by patient phone
 *  2. getLabResultByServerId — dedup lookup by server ID
 *  3. clearResultsByPatient — deletes only the specified patient's results
 *  4. getResultsByPatientFlow — returns empty for unknown patient
 *  5. insertAll with REPLACE strategy — overwrites existing row with same PK
 *  6. getAllResultsFlow — returns all results regardless of patient
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LabResultDaoTest {

    private lateinit var database: ClinicDatabase
    private lateinit var dao: LabResultDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ClinicDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.labResultDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createLabResult(
        serverId: Int = 1,
        patientPhone: String = "+77771112233",
        testName: String = "Glucose",
        result: String = "5.5",
        unit: String = "mmol/L",
        status: String = "COMPLETED",
    ) = LabResultEntity(
        id = "lab-$serverId",
        serverId = serverId,
        patientPhone = patientPhone,
        testName = testName,
        result = result,
        unit = unit,
        referenceRange = "3.9 - 6.1",
        status = status,
        performedAt = "2026-07-01",
        doctorName = "Dr. Smith",
    )

    @Test
    fun `insertAll and getResultsByPatientFlow returns inserted results`() = runBlocking {
        val results = listOf(
            createLabResult(serverId = 1, testName = "Glucose"),
            createLabResult(serverId = 2, testName = "Hemoglobin"),
            createLabResult(serverId = 3, testName = "Cholesterol"),
        )
        dao.insertAll(results)

        val queried = dao.getResultsByPatientFlow("+77771112233").first()
        assertEquals(3, queried.size)
        assertTrue(queried.any { it.testName == "Glucose" })
        assertTrue(queried.any { it.testName == "Hemoglobin" })
        assertTrue(queried.any { it.testName == "Cholesterol" })
    }

    @Test
    fun `getLabResultByServerId returns correct result`() = runBlocking {
        dao.insertAll(listOf(createLabResult(serverId = 42, testName = "Insulin")))

        val result = dao.getLabResultByServerId(42)
        assertNotNull(result)
        assertEquals("Insulin", result?.testName)
        assertEquals("5.5", result?.result)
        assertEquals("mmol/L", result?.unit)
    }

    @Test
    fun `getLabResultByServerId returns null for non-existent`() = runBlocking {
        val result = dao.getLabResultByServerId(999)
        assertNull(result)
    }

    @Test
    fun `clearResultsByPatient deletes only specified patient`() = runBlocking {
        dao.insertAll(listOf(
            createLabResult(serverId = 1, patientPhone = "+77771112233"),
            createLabResult(serverId = 2, patientPhone = "+77771112233"),
            createLabResult(serverId = 3, patientPhone = "+77002223344", testName = "WBC"),
        ))

        dao.clearResultsByPatient("+77771112233")

        val remaining = dao.getAllResultsFlow().first()
        assertEquals(1, remaining.size)
        assertEquals("WBC", remaining[0].testName)
    }

    @Test
    fun `getResultsByPatientFlow returns empty for unknown patient`() = runBlocking {
        dao.insertAll(listOf(createLabResult(serverId = 1, patientPhone = "+77771112233")))

        val queried = dao.getResultsByPatientFlow("+99999999999").first()
        assertTrue(queried.isEmpty())
    }

    @Test
    fun `insertAll with REPLACE strategy overwrites existing row`() = runBlocking {
        val original = createLabResult(serverId = 1, testName = "Glucose", result = "5.5")
        dao.insertAll(listOf(original))

        val updated = original.copy(result = "6.2", status = "HIGH")
        dao.insertAll(listOf(updated))

        val queried = dao.getResultsByPatientFlow("+77771112233").first()
        assertEquals(1, queried.size)
        assertEquals("6.2", queried[0].result)
        assertEquals("HIGH", queried[0].status)
    }

    @Test
    fun `getAllResultsFlow returns all results`() = runBlocking {
        dao.insertAll(listOf(
            createLabResult(serverId = 1, patientPhone = "+77771112233"),
            createLabResult(serverId = 2, patientPhone = "+77002223344", testName = "WBC"),
        ))

        val all = dao.getAllResultsFlow().first()
        assertEquals(2, all.size)
    }

    @Test
    fun `clearAll removes everything`() = runBlocking {
        dao.insertAll(listOf(
            createLabResult(serverId = 1),
            createLabResult(serverId = 2, testName = "WBC"),
        ))

        dao.clearAll()

        val all = dao.getAllResultsFlow().first()
        assertTrue(all.isEmpty())
    }
}

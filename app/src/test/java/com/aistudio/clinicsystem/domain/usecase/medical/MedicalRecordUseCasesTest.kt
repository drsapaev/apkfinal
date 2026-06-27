package com.aistudio.clinicsystem.domain.usecase.medical

import com.aistudio.clinicsystem.domain.model.MedicalRecord
import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * M3A/E6.2: Unit tests for [FetchMedicalRecordsUseCase] and [CreateMedicalRecordUseCase].
 */
class MedicalRecordUseCasesTest {

    private lateinit var repository: ClinicRepositoryInterface
    private lateinit var fetchUseCase: FetchMedicalRecordsUseCase
    private lateinit var createUseCase: CreateMedicalRecordUseCase

    private val testRecords = listOf(
        MedicalRecord(
            id = "1", patientPhone = "+77771112233", doctorName = "Dr. Smith",
            diagnosis = "Flu", prescription = "Rest", visitDate = "2026-06-01"
        )
    )

    @Before
    fun setUp() {
        repository = mockk()
        fetchUseCase = FetchMedicalRecordsUseCase(repository)
        createUseCase = CreateMedicalRecordUseCase(repository)
    }

    // ─── FetchMedicalRecordsUseCase ─────────────────────────────────

    @Test
    fun `fetch blank phone returns failure`() = runBlocking {
        val result = fetchUseCase.invoke("")
        assertTrue("Blank phone should fail", result.isFailure)
    }

    @Test
    fun `fetch valid phone delegates to repository`() = runBlocking {
        coEvery { repository.fetchMedicalRecords("+77771112233") } returns
            Result.success(testRecords)

        val result = fetchUseCase.invoke("+77771112233")

        assertTrue("Should succeed", result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
    }

    @Test
    fun `fetch repository failure is propagated`() = runBlocking {
        coEvery { repository.fetchMedicalRecords(any()) } returns
            Result.failure(RuntimeException("Network"))

        val result = fetchUseCase.invoke("+77771112233")

        assertTrue("Should fail", result.isFailure)
    }

    // ─── CreateMedicalRecordUseCase ─────────────────────────────────

    @Test
    fun `create blank patientPhone returns failure`() = runBlocking {
        val result = createUseCase.invoke("", "Dr.", "Diagnosis", "Rx")
        assertTrue("Blank patientPhone should fail", result.isFailure)
    }

    @Test
    fun `create blank doctorName returns failure`() = runBlocking {
        val result = createUseCase.invoke("+77771112233", "", "Diagnosis", "Rx")
        assertTrue("Blank doctorName should fail", result.isFailure)
    }

    @Test
    fun `create blank diagnosis returns failure`() = runBlocking {
        val result = createUseCase.invoke("+77771112233", "Dr.", "", "Rx")
        assertTrue("Blank diagnosis should fail", result.isFailure)
    }

    @Test
    fun `create valid input delegates to repository`() = runBlocking {
        val record = testRecords.first()
        coEvery {
            repository.createMedicalRecord("+77771112233", "Dr. Smith", "Flu", "Rest", "")
        } returns Result.success(record)

        val result = createUseCase.invoke("+77771112233", "Dr. Smith", "Flu", "Rest")

        assertTrue("Should succeed", result.isSuccess)
        assertEquals(record, result.getOrThrow())
    }
}

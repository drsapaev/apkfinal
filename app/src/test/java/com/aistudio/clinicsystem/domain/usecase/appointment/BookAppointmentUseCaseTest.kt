package com.aistudio.clinicsystem.domain.usecase.appointment

import com.aistudio.clinicsystem.domain.model.Appointment
import com.aistudio.clinicsystem.domain.model.AppointmentStatus
import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * M3A/E6.2: Unit tests for [BookAppointmentUseCase].
 *
 * Validates input (doctorId > 0, date/time not blank) before delegating.
 */
class BookAppointmentUseCaseTest {

    private lateinit var repository: ClinicRepositoryInterface
    private lateinit var useCase: BookAppointmentUseCase

    private val testAppointment = Appointment(
        id = "1", patientPhone = "+77771112233", patientName = "Test",
        doctorName = "Dr. Smith", specialty = "Cardiology",
        date = "2026-07-01", time = "10:00", status = AppointmentStatus.PENDING,
        reason = "Checkup"
    )

    @Before
    fun setUp() {
        repository = mockk()
        useCase = BookAppointmentUseCase(repository)
    }

    @Test
    fun `zero doctorId returns failure`() = runBlocking {
        val result = useCase.invoke(0, "2026-07-01", "10:00", "reason")
        assertTrue("doctorId=0 should fail", result.isFailure)
    }

    @Test
    fun `negative doctorId returns failure`() = runBlocking {
        val result = useCase.invoke(-1, "2026-07-01", "10:00", "reason")
        assertTrue("Negative doctorId should fail", result.isFailure)
    }

    @Test
    fun `blank date returns failure`() = runBlocking {
        val result = useCase.invoke(1, "", "10:00", "reason")
        assertTrue("Blank date should fail", result.isFailure)
    }

    @Test
    fun `blank time returns failure`() = runBlocking {
        val result = useCase.invoke(1, "2026-07-01", "", "reason")
        assertTrue("Blank time should fail", result.isFailure)
    }

    @Test
    fun `valid input delegates to repository`() = runBlocking {
        coEvery {
            repository.bookAppointment(1, "2026-07-01", "10:00", "reason", null)
        } returns Result.success(testAppointment)

        val result = useCase.invoke(1, "2026-07-01", "10:00", "reason")

        assertTrue("Should succeed", result.isSuccess)
        assertEquals(testAppointment, result.getOrThrow())
    }

    @Test
    fun `repository failure is propagated`() = runBlocking {
        coEvery { repository.bookAppointment(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("Server error"))

        val result = useCase.invoke(1, "2026-07-01", "10:00", "reason")

        assertTrue("Should fail", result.isFailure)
    }
}

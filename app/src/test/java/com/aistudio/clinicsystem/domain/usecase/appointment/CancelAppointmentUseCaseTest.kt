package com.aistudio.clinicsystem.domain.usecase.appointment

import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M3A/E6.2: Unit tests for [CancelAppointmentUseCase].
 */
class CancelAppointmentUseCaseTest {

    private lateinit var repository: ClinicRepositoryInterface
    private lateinit var useCase: CancelAppointmentUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = CancelAppointmentUseCase(repository)
    }

    @Test
    fun `zero appointmentId returns failure`() = runBlocking {
        val result = useCase.invoke(0, "reason")
        assertTrue("appointmentId=0 should fail", result.isFailure)
    }

    @Test
    fun `blank reason returns failure`() = runBlocking {
        val result = useCase.invoke(1, "")
        assertTrue("Blank reason should fail", result.isFailure)
    }

    @Test
    fun `valid input delegates to repository`() = runBlocking {
        coEvery { repository.cancelAppointment(1, "reason") } returns Result.success(Unit)

        val result = useCase.invoke(1, "reason")

        assertTrue("Should succeed", result.isSuccess)
    }

    @Test
    fun `repository failure is propagated`() = runBlocking {
        coEvery { repository.cancelAppointment(any(), any()) } returns
            Result.failure(RuntimeException("Not found"))

        val result = useCase.invoke(1, "reason")

        assertTrue("Should fail", result.isFailure)
    }
}

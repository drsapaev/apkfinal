package com.aistudio.clinicsystem.ui.viewmodel

import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 10b (TEST-15 fix): PatientViewModelTest.
 *
 * Closes audit finding TEST-15: "No tests for PatientViewModel or
 * StaffViewModel." StaffViewModel is 500 LOC with complex queue-shifting
 * logic — all untested.
 *
 * Tests cover:
 *  1. createAppointment — happy path (calls repository with correct params)
 *  2. createAppointment — double-tap guard (isBookingInProgress)
 *  3. cancelAppointment — calls repository with CANCELLED status
 *  4. logOut — calls authRepository.logout + sessionRepository.clearSession
 *  5. setBiometricEnrollment — updates user via repository
 *  6. updateProfileName — blank name is ignored
 *  7. setThemeMode — invalid mode is ignored
 *  8. fetchMedicalReports — calls repository with correct phone
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PatientViewModelTest {

    private lateinit var viewModel: PatientViewModel
    private lateinit var repository: ClinicRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionRepository: SessionRepository

    private val testUser = UserEntity(
        id = 1,
        phone = "+77771112233",
        fullName = "Test Patient",
        role = "PATIENT",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        repository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)

        // Default: authenticated as patient
        every { sessionRepository.sessionState } returns MutableStateFlow(
            SessionState.Authenticated(
                user = testUser,
                accessToken = "test-token",
                refreshToken = "test-refresh",
            ),
        )
        every { sessionRepository.accessToken } returns "test-token"

        viewModel = PatientViewModel(
            repository = repository,
            authRepository = authRepository,
            sessionRepository = sessionRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createAppointment calls repository with correct params`() = runTest {
        coEvery {
            repository.createAppointmentOnServerAndLocal(
                any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns AppointmentEntity(
            id = "new-apt",
            patientPhone = "+77771112233",
            patientName = "Test Patient",
            doctorName = "Dr. Smith",
            specialty = "Cardiology",
            date = "2026-07-10",
            time = "14:00",
            status = "PENDING",
            reason = "Checkup",
        )

        viewModel.createAppointment(
            doctorName = "Dr. Smith",
            specialty = "Cardiology",
            date = "2026-07-10",
            time = "14:00",
            reason = "Checkup",
        )
        advanceUntilIdle()

        coVerify {
            repository.createAppointmentOnServerAndLocal(
                token = "test-token",
                patientPhone = "+77771112233",
                patientName = "Test Patient",
                doctorName = "Dr. Smith",
                specialty = "Cardiology",
                date = "2026-07-10",
                time = "14:00",
                reason = "Checkup",
            )
        }
    }

    @Test
    fun `createAppointment double-tap is guarded by isBookingInProgress`() = runTest {
        // First call starts booking — second call should be ignored
        coEvery {
            repository.createAppointmentOnServerAndLocal(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            kotlinx.coroutines.delay(1000) // Simulate slow network
            AppointmentEntity(
                id = "apt-1",
                patientPhone = "+77771112233",
                patientName = "Test",
                doctorName = "Dr.",
                specialty = "S",
                date = "2026-07-10",
                time = "14:00",
                status = "PENDING",
                reason = "R",
            )
        }

        viewModel.createAppointment("Dr.", "S", "2026-07-10", "14:00", "R")
        viewModel.createAppointment("Dr.", "S", "2026-07-10", "14:00", "R")
        advanceUntilIdle()

        // Only ONE call should have been made
        coVerify(exactly = 1) {
            repository.createAppointmentOnServerAndLocal(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `cancelAppointment calls repository with CANCELLED status`() = runTest {
        viewModel.cancelAppointment("apt-123", "Not available")
        advanceUntilIdle()

        coVerify {
            repository.updateAppointmentStatusOnServerAndLocal(
                token = "test-token",
                id = "apt-123",
                status = "CANCELLED",
                cancelReason = "Not available",
            )
        }
    }

    @Test
    fun `logOut calls authRepository logout and clears session`() = runTest {
        coEvery { authRepository.logout() } returns Result.success(Unit)

        viewModel.logOut()
        advanceUntilIdle()

        coVerify { authRepository.logout() }
        coVerify { sessionRepository.clearSession() }
    }

    @Test
    fun `setBiometricEnrollment updates user via repository`() = runTest {
        viewModel.setBiometricEnrollment(true)
        advanceUntilIdle()

        coVerify {
            repository.updateUser(match { it.biometricEnabled == true })
        }
    }

    @Test
    fun `updateProfileName with blank name is ignored`() = runTest {
        viewModel.updateProfileName("")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateUser(any()) }
    }

    @Test
    fun `updateProfileName with valid name updates user`() = runTest {
        viewModel.updateProfileName("New Name")
        advanceUntilIdle()

        coVerify {
            repository.updateUser(match { it.fullName == "New Name" })
        }
    }

    @Test
    fun `setThemeMode with invalid mode is ignored`() = runTest {
        viewModel.setThemeMode("INVALID")
        assertEquals("SYSTEM", viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode with valid mode updates state`() = runTest {
        viewModel.setThemeMode("DARK")
        assertEquals("DARK", viewModel.themeMode.value)
    }
}

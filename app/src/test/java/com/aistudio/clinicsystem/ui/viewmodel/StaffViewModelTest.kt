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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 10b (TEST-15 fix): StaffViewModelTest.
 *
 * Closes audit finding TEST-15: "No tests for PatientViewModel or
 * StaffViewModel. StaffViewModel is 500 LOC with complex queue-shifting
 * logic — all untested."
 *
 * Tests cover:
 *  1. approveAppointment — calls repository with APPROVED status
 *  2. cancelAppointment — calls repository with CANCELLED status
 *  3. logOut — calls authRepository.logout + sessionRepository.clearSession
 *  4. setThemeMode — valid + invalid mode
 *  5. setUndoAction + triggerUndo + clearUndoAction — undo state machine
 *  6. clearMedicalRecordDraft — resets draft fields
 *  7. clearCreateAppointmentDraft — resets draft fields
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class StaffViewModelTest {
    private lateinit var viewModel: StaffViewModel
    private lateinit var repository: ClinicRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var realContext: android.content.Context
    private lateinit var realDatabase: com.aistudio.clinicsystem.data.db.ClinicDatabase

    private val testStaffUser =
        UserEntity(
            id = 2,
            phone = "+77071234567",
            fullName = "Dr. Staff",
            role = "STAFF",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        repository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)

        every { sessionRepository.sessionState } returns
            MutableStateFlow(
                SessionState.Authenticated(
                    user = testStaffUser,
                    accessToken = "staff-token",
                    refreshToken = "staff-refresh",
                ),
            )
        every { sessionRepository.accessToken } returns "staff-token"

        // FIX (UncaughtExceptionsBeforeTest): the StaffViewModel init block
        // collects webSocketClient.partialQueueEvents / recoveryEvents. On a
        // RELAXED mock those getters return mockk's default, and collect()
        // throws KotlinNothingValueException inside an eager
        // viewModelScope.launch on Main — the uncaught exception then poisons
        // every runTest in the class. Real never-emitting SharedFlows keep
        // the collectors suspended (harmless) instead of exploding.
        val webSocketClient = mockk<com.aistudio.clinicsystem.utils.ClinicWebSocketClient>(relaxed = true)
        every { webSocketClient.partialQueueEvents } returns MutableSharedFlow()
        every { webSocketClient.recoveryEvents } returns MutableSharedFlow()

        // FIX (mockk "class redefinition failed"): framework/concrete classes
        // (Context, ClinicDatabase) are inline-mocked by the mockk agent;
        // combined with Robolectric's per-sandbox classloaders that triggers
        // retransformation conflicts mid-suite. Real instances are drop-in:
        // SharedPreferences + getString work on the Robolectric context, and
        // an in-memory Room behaves better than relaxed no-op DAO mocks.
        realContext =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext()
        realDatabase =
            androidx.room.Room
                .inMemoryDatabaseBuilder(
                    realContext,
                    com.aistudio.clinicsystem.data.db.ClinicDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        viewModel =
            StaffViewModel(
                appContext = realContext,
                database = realDatabase,
                repository = repository,
                authRepository = authRepository,
                sessionRepository = sessionRepository,
                apiService = mockk(relaxed = true),
                webSocketClient = webSocketClient,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (this::realDatabase.isInitialized) {
            realDatabase.close()
        }
    }

    @Test
    fun `approveAppointment calls repository with APPROVED status`() =
        runTest {
            val appointment =
                AppointmentEntity(
                    id = "apt-001",
                    patientPhone = "+77771112233",
                    patientName = "Patient",
                    doctorName = "Dr. Staff",
                    specialty = "Cardiology",
                    date = "2026-07-10",
                    time = "14:00",
                    status = "PENDING",
                    reason = "Checkup",
                )
            coEvery { repository.getAppointmentById("apt-001") } returns appointment
            coEvery {
                repository.updateAppointmentStatusOnServerAndLocal(any(), any(), any(), any(), any())
            } returns
                com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Confirmed(
                    appointment.copy(status = "APPROVED"),
                    101,
                )

            viewModel.approveAppointment("apt-001")
            advanceUntilIdle()

            coVerify {
                repository.updateAppointmentStatusOnServerAndLocal(
                    token = "staff-token",
                    id = "apt-001",
                    status = "APPROVED",
                    cancelReason = "",
                    actorIsPatient = false,
                )
            }
        }

    @Test
    fun `cancelAppointment calls repository with CANCELLED status`() =
        runTest {
            viewModel.cancelAppointment("apt-002", "Patient requested")
            advanceUntilIdle()

            coVerify {
                repository.updateAppointmentStatusOnServerAndLocal(
                    token = "staff-token",
                    id = "apt-002",
                    status = "CANCELLED",
                    cancelReason = "Patient requested",
                )
            }
        }

    @Test
    fun `logOut calls authRepository and clears session`() =
        runTest {
            coEvery { authRepository.logout() } returns Result.success(Unit)

            viewModel.logOut()
            advanceUntilIdle()

            coVerify { authRepository.logout() }
            coVerify { sessionRepository.clearSession() }
        }

    @Test
    fun `setThemeMode with valid mode updates state`() {
        viewModel.setThemeMode("DARK")
        assertEquals("DARK", viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode with invalid mode is ignored`() {
        viewModel.setThemeMode("INVALID")
        assertEquals("SYSTEM", viewModel.themeMode.value)
    }

    @Test
    fun `setUndoAction stores action and triggerUndo executes it`() =
        runTest {
            val action = StaffViewModel.UndoAction.DeleteAppointment("apt-003")
            viewModel.setUndoAction(action)

            assertNotNull("Undo action should be stored", viewModel.undoAction.value)

            viewModel.triggerUndo()
            advanceUntilIdle()

            // After undo, action should be cleared
            assertNull("Undo action should be cleared after trigger", viewModel.undoAction.value)
        }

    @Test
    fun `clearUndoAction nullifies the undo state`() {
        viewModel.setUndoAction(StaffViewModel.UndoAction.DeleteAppointment("apt-004"))
        viewModel.clearUndoAction()
        assertNull(viewModel.undoAction.value)
    }

    @Test
    fun `clearMedicalRecordDraft resets diagnosis and prescription`() {
        viewModel.setDraftDiagnosis("Flu")
        viewModel.setDraftPrescription("Rest")
        viewModel.setDraftRecommendations("Drink water")

        viewModel.clearMedicalRecordDraft()

        assertEquals("", viewModel.draftDiagnosis.value)
        assertEquals("", viewModel.draftPrescription.value)
        assertEquals("", viewModel.draftRecommendations.value)
    }

    @Test
    fun `clearCreateAppointmentDraft resets all create fields`() {
        viewModel.setDraftCreatePatientPhone("+77771112233")
        viewModel.setDraftCreatePatientName("Test")
        viewModel.setDraftCreateDoctorSelected("Dr. Smith")
        viewModel.setDraftCreateReason("Checkup")

        viewModel.clearCreateAppointmentDraft()

        assertEquals("", viewModel.draftCreatePatientPhone.value)
        assertEquals("", viewModel.draftCreatePatientName.value)
    }
}

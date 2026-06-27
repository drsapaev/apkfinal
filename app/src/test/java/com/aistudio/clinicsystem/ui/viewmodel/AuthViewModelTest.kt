package com.aistudio.clinicsystem.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.repository.AuthError
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.data.api.UserDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * M3A/E6.5: Unit tests for [AuthViewModel].
 *
 * Tests the login flow state machine: input validation, success, 2FA,
 * error handling, and state management.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AuthViewModelTest {

    private lateinit var application: Application
    private lateinit var database: ClinicDatabase
    private lateinit var viewModel: AuthViewModel
    private lateinit var authRepository: AuthRepository

    private val testUser = UserDto(
        id = 1, phone = "+77771112233", fullName = "Test User",
        role = "PATIENT", dateOfBirth = "1990-01-01", biometricEnabled = false,
        telegramChatId = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            application, ClinicDatabase::class.java
        ).allowMainThreadQueries().build()

        // Mock the ClinicDatabase.getDatabase singleton to return our in-memory DB
        // BEFORE creating the ViewModel (which calls getDatabase in its constructor)
        databaseMockk()

        viewModel = AuthViewModel(application)

        authRepository = mockk(relaxed = true)
        val authRepoField = AuthViewModel::class.java.getDeclaredField("authRepository")
        authRepoField.isAccessible = true
        authRepoField.set(viewModel, authRepository)

        val clinicRepo = mockk<ClinicRepository>(relaxed = true)
        val clinicRepoField = AuthViewModel::class.java.getDeclaredField("repository")
        clinicRepoField.isAccessible = true
        clinicRepoField.set(viewModel, clinicRepo)
    }

    /** Mock ClinicDatabase.getDatabase() to return in-memory DB, avoiding SQLCipher. */
    private fun databaseMockk() {
        io.mockk.mockkObject(ClinicDatabase.Companion)
        io.mockk.every { ClinicDatabase.getDatabase(any()) } returns database
    }

    @After
    fun tearDown() {
        io.mockk.unmockkObject(ClinicDatabase.Companion)
        Dispatchers.resetMain()
        database.close()
    }

    private fun set2FAChallenge(token: String) {
        val field = AuthViewModel::class.java.getDeclaredField("_pending2FAChallenge")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<String?>).value = token
    }

    @Test
    fun `login with blank username sets authError`() = runTest {
        viewModel.updateUsernameInput("")
        viewModel.updatePasswordInput("password")
        viewModel.login(); advanceUntilIdle()
        assertEquals("Пожалуйста, введите имя пользователя и пароль", viewModel.authError.value)
    }

    @Test
    fun `login with blank password sets authError`() = runTest {
        viewModel.updateUsernameInput("user")
        viewModel.updatePasswordInput("")
        viewModel.login(); advanceUntilIdle()
        assertEquals("Пожалуйста, введите имя пользователя и пароль", viewModel.authError.value)
    }

    @Test
    fun `login success invokes onLoginSuccess callback`() = runTest {
        var called = false
        viewModel.onLoginSuccess = { called = true }
        coEvery { authRepository.login(any(), any()) } returns
            Result.success(LoginOutcome.Success(testUser))
        viewModel.updateUsernameInput("user")
        viewModel.updatePasswordInput("pass")
        viewModel.login(); advanceUntilIdle()
        // Debug: check what authRepository.login returned
        println("DEBUG: called=$called, authError=${viewModel.authError.value}")
        assertTrue("onLoginSuccess should be called. authError=${viewModel.authError.value}", called)
        assertNull("authError should be null on success", viewModel.authError.value)
    }

    @Test
    fun `login with 2FA required sets pending2FAChallenge`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.success(LoginOutcome.TwoFactorRequired("challenge-123"))
        viewModel.updateUsernameInput("user")
        viewModel.updatePasswordInput("pass")
        viewModel.login(); advanceUntilIdle()
        assertEquals("challenge-123", viewModel.pending2FAChallenge.value)
    }

    @Test
    fun `verify2FA success clears challenge and invokes onLoginSuccess`() = runTest {
        var called = false
        viewModel.onLoginSuccess = { called = true }
        set2FAChallenge("challenge-token")
        coEvery { authRepository.verify2FA(any(), any(), any()) } returns
            Result.success(LoginOutcome.Success(testUser))
        viewModel.verify2FA("123456", false); advanceUntilIdle()
        assertTrue(called)
        assertNull(viewModel.pending2FAChallenge.value)
    }

    @Test
    fun `verify2FA without challenge sets expired error`() = runTest {
        viewModel.verify2FA("123456", false); advanceUntilIdle()
        assertEquals("Сессия 2FA истекла, войдите заново", viewModel.authError.value)
    }

    @Test
    fun `verify2FA with wrong code length sets error`() = runTest {
        set2FAChallenge("challenge-token")
        viewModel.verify2FA("12345", false); advanceUntilIdle()
        assertEquals("Код должен состоять из 6 цифр", viewModel.authError.value)
    }

    @Test
    fun `verify2FA with invalid code sets authError`() = runTest {
        set2FAChallenge("challenge-token")
        coEvery { authRepository.verify2FA(any(), any(), any()) } returns
            Result.failure(AuthError.InvalidTwoFACode)
        viewModel.verify2FA("000000", false); advanceUntilIdle()
        assertEquals("Неверный код 2FA", viewModel.authError.value)
    }

    @Test
    fun `cancel2FAChallenge clears pending challenge and otp input`() = runTest {
        set2FAChallenge("challenge-token")
        viewModel.updatePasswordInput("somecode")
        viewModel.cancel2FAChallenge()
        assertNull(viewModel.pending2FAChallenge.value)
        assertEquals("", viewModel.otpInput.value)
    }

    @Test
    fun `login with invalid credentials sets specific error`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.failure(AuthError.InvalidCredentials)
        viewModel.updateUsernameInput("user")
        viewModel.updatePasswordInput("wrongpass")
        viewModel.login(); advanceUntilIdle()
        assertEquals("Неверный логин или пароль", viewModel.authError.value)
    }

    @Test
    fun `login with network error sets generic error`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.failure(RuntimeException("Network timeout"))
        viewModel.updateUsernameInput("user")
        viewModel.updatePasswordInput("pass")
        viewModel.login(); advanceUntilIdle()
        assertNotNull(viewModel.authError.value)
        assertTrue(viewModel.authError.value!!.contains("Network timeout"))
    }

    @Test
    fun `clearAuthError sets error to null`() = runTest {
        viewModel.updateUsernameInput("")
        viewModel.updatePasswordInput("")
        viewModel.login(); advanceUntilIdle()
        assertNotNull(viewModel.authError.value)
        viewModel.clearAuthError()
        assertNull(viewModel.authError.value)
    }

    @Test
    fun `updateUsernameInput updates phoneInput state`() {
        viewModel.updateUsernameInput("testuser")
        assertEquals("testuser", viewModel.phoneInput.value)
    }

    @Test
    fun `updatePasswordInput updates otpInput state`() {
        viewModel.updatePasswordInput("testpass")
        assertEquals("testpass", viewModel.otpInput.value)
    }
}

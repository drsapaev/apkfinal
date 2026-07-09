package com.aistudio.clinicsystem.ui.viewmodel

import com.aistudio.clinicsystem.data.repository.AuthError
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import com.aistudio.clinicsystem.data.api.UserDto
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 2.11: AuthViewModelTest — rewired for Hilt constructor injection.
 *
 * The previous test used reflection to set private `authRepository` and
 * `repository` fields after constructing the ViewModel via the deprecated
 * `AndroidViewModel(application)` form. Now that AuthViewModel is
 * `@HiltViewModel` with `@Inject constructor(repo, authRepo, sessionRepo)`,
 * the test simply passes mockk() instances to the constructor directly —
 * no reflection, no Application, no ClinicDatabase.
 *
 * Closes audit finding TEST-6: "AuthViewModelTest uses reflection to set
 * private fields — brittle".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var authRepository: AuthRepository
    private lateinit var repository: ClinicRepository
    private lateinit var sessionRepository: SessionRepository

    private val testUser = UserDto(
        id = 1, phone = "+77771112233", fullName = "Test User",
        role = "PATIENT", dateOfBirth = "1990-01-01", biometricEnabled = false,
        telegramChatId = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Stage 2.11: real mocks, no reflection.
        authRepository = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        // Default session state — Unauthenticated (the AuthScreen is showing).
        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)
        every { sessionRepository.accessToken } returns null

        viewModel = AuthViewModel(
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

    // ═══════════════════════════════════════════════════════════════════
    // P0-1 audit fix: biometric login tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `loginWithBiometrics with null cipher sets biometric unavailable error and does not call repository`() = runTest {
        viewModel.loginWithBiometrics("+77771112233", cipher = null); advanceUntilIdle()

        assertEquals(
            "Биометрический ключ недоступен. Войдите по паролю.",
            viewModel.authError.value,
        )
        // Repository must NOT be called when cipher is null — fail closed.
        coEvery { authRepository.verifyCurrentSession() } returns Result.success(testUser)
    }

    @Test
    fun `loginWithBiometrics success invokes onLoginSuccess callback`() = runTest {
        var called = false
        viewModel.onLoginSuccess = { called = true }

        // User cached in Room with biometricEnabled = true — passes the local guard.
        every { repository.getUserByPhone(testUser.phone) } returns com.aistudio.clinicsystem.data.db.UserEntity(
            id = 1,
            phone = testUser.phone,
            fullName = testUser.fullName,
            role = testUser.role,
            dateOfBirth = testUser.dateOfBirth,
            biometricEnabled = true,
            telegramChatId = null,
        )

        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.success(testUser)

        viewModel.loginWithBiometrics(testUser.phone, cipher); advanceUntilIdle()

        assertTrue("onLoginSuccess should be called on biometric success", called)
        assertNull("authError should be null on success", viewModel.authError.value)
    }

    @Test
    fun `loginWithBiometrics fails closed when user is not biometric-enabled in local cache`() = runTest {
        var called = false
        viewModel.onLoginSuccess = { called = true }

        // User has biometricEnabled = false locally — even if backend accepted the
        // refresh-token exchange, the local guard must reject the login.
        every { repository.getUserByPhone(testUser.phone) } returns com.aistudio.clinicsystem.data.db.UserEntity(
            id = 1,
            phone = testUser.phone,
            fullName = testUser.fullName,
            role = testUser.role,
            dateOfBirth = testUser.dateOfBirth,
            biometricEnabled = false,
            telegramChatId = null,
        )

        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.success(testUser)

        viewModel.loginWithBiometrics(testUser.phone, cipher); advanceUntilIdle()

        assertFalse("onLoginSuccess must NOT be called when local biometricEnabled=false", called)
        assertEquals(
            "Биометрический вход заблокирован во внешнем профиле клиники",
            viewModel.authError.value,
        )
    }

    @Test
    fun `loginWithBiometrics repository failure sets token failure error`() = runTest {
        var called = false
        viewModel.onLoginSuccess = { called = true }

        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.failure(IllegalStateException("Не удалось расшифровать refresh token"))

        viewModel.loginWithBiometrics(testUser.phone, cipher); advanceUntilIdle()

        assertFalse(called)
        assertNotNull(viewModel.authError.value)
        assertTrue(viewModel.authError.value!!.contains("Сбой биометрического токена"))
    }

    @Test
    fun `loginWithBiometrics repository failure when user not in local cache sets token failure error`() = runTest {
        // User not in local cache — local guard rejects, but this branch should
        // still surface the repository error to the user.
        var called = false
        viewModel.onLoginSuccess = { called = true }

        every { repository.getUserByPhone(any()) } returns null

        val cipher = mockk<javax.crypto.Cipher>(relaxed = true)
        coEvery { authRepository.loginWithBiometricRefreshToken(any(), any()) } returns
            Result.success(testUser)

        viewModel.loginWithBiometrics(testUser.phone, cipher); advanceUntilIdle()

        assertFalse(called)
        assertEquals(
            "Биометрический вход заблокирован во внешнем профиле клиники",
            viewModel.authError.value,
        )
    }
}

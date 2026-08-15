package com.aistudio.clinicsystem.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.repository.AuthError
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import com.aistudio.clinicsystem.domain.usecase.auth.LoginUseCase
import com.aistudio.clinicsystem.domain.usecase.auth.LoginWithBiometricsUseCase
import com.aistudio.clinicsystem.domain.usecase.auth.Request2FARecoveryUseCase
import com.aistudio.clinicsystem.domain.usecase.auth.Verify2FARecoveryUseCase
import com.aistudio.clinicsystem.domain.usecase.auth.Verify2FAUseCase
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stage 2.7: AuthViewModel is now @HiltViewModel. All four dependencies
 * are injected; no more manual construction of ClinicDatabase / SessionManager /
 * ApiClient. The ViewModel reads session state from [SessionRepository]
 * (the SSOT) instead of maintaining its own.
 *
 * High-5 audit fix: AuthViewModel now delegates to auth use cases
 * (LoginUseCase, Verify2FAUseCase, etc.) instead of calling
 * AuthRepository directly. This restores the intended Clean Architecture
 * layering: ViewModel → UseCase → Repository. Use cases encapsulate
 * input validation (blank checks, TOTP code format, etc.) so the
 * ViewModel focuses on UI state management.
 *
 * The [ClinicRepository] is still injected for side effects (sync log
 * writes, user cache lookups for biometric verification).
 * [AuthRepository] is no longer injected — all auth operations go
 * through use cases. [LogoutUseCase] is not injected here because
 * logout is initiated from PatientViewModel/StaffViewModel/ClinicViewModel
 * (the screens that have a logout button), not from AuthViewModel.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ClinicRepository,
    private val sessionRepository: SessionRepository,
    // High-5 audit fix: use cases replace direct authRepository calls.
    private val loginUseCase: LoginUseCase,
    private val verify2FAUseCase: Verify2FAUseCase,
    private val request2FARecoveryUseCase: Request2FARecoveryUseCase,
    private val verify2FARecoveryUseCase: Verify2FARecoveryUseCase,
    private val loginWithBiometricsUseCase: LoginWithBiometricsUseCase,
) : ViewModel() {

    // R-2: helper for localized error messages
    private fun getString(resId: Int) = appContext.getString(resId)

    private val _usernameInput = MutableStateFlow("+7 ")
    val usernameInput: StateFlow<String> = _usernameInput.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    private val _isOtpSent = MutableStateFlow(false)
    val isOtpSent: StateFlow<Boolean> = _isOtpSent.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _biometricHardwareSupported = MutableStateFlow(true)
    val biometricHardwareSupported: StateFlow<Boolean> = _biometricHardwareSupported.asStateFlow()

    // M1/E3.4: 2FA challenge state — when set, the UI must show a 2FA code input
    // and call [verify2FA] instead of [login].
    private val _pending2FAChallenge = MutableStateFlow<String?>(null)
    val pending2FAChallenge: StateFlow<String?> = _pending2FAChallenge.asStateFlow()

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateUsernameInput(value: String) {
        _usernameInput.value = value
    }

    fun updatePasswordInput(value: String) {
        _passwordInput.value = value
    }

    fun clearAuthError() {
        _authError.value = null
    }

    /** Clears the 2FA challenge state — used when user cancels the 2FA flow. */
    fun cancel2FAChallenge() {
        _pending2FAChallenge.value = null
        _passwordInput.value = ""
    }

    // Callbacks to notify parent/navigation graph
    var onLoginSuccess: (() -> Unit)? = null

    /**
     * M1/E3.4: login now handles three outcomes:
     *  1. Success → user is logged in, navigate to main screen
     *  2. TwoFactorRequired → set [_pending2FAChallenge], UI shows 2FA input
     *  3. Failure → set [_authError]
     *
     * High-5 audit fix: now delegates to [LoginUseCase] which validates
     * input (blank username/password) before calling the repository.
     * The ViewModel previously did its own blank-check here AND the
     * repository would have rejected blank inputs anyway — the use
     * case centralises validation in one place.
     */
    fun login() {
        val username = _usernameInput.value.trim()
        val password = _passwordInput.value.trim()

        if (username.isBlank() || password.isBlank()) {
            _authError.value = "Пожалуйста, введите имя пользователя и пароль"
            return
        }

        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true

            val result = loginUseCase(username, password)
            _isSyncing.value = false

            result.onSuccess { outcome ->
                when (outcome) {
                    is LoginOutcome.Success -> {
                        repository.addSyncLog(
                            logMessage = "🟢 Успешный вход через API FastAPI.",
                            direction = "SYSTEM_SYNC"
                        )
                        onLoginSuccess?.invoke()
                    }
                    is LoginOutcome.TwoFactorRequired -> {
                        _pending2FAChallenge.value = outcome.challengeToken
                        _passwordInput.value = ""  // clear password, prepare for TOTP code
                        repository.addSyncLog(
                            logMessage = "🔐 Требуется двухфакторная аутентификация.",
                            direction = "SYSTEM_SYNC"
                        )
                    }
                }
            }.onFailure { error ->
                _authError.value = when (error) {
                    is AuthError.InvalidCredentials -> getString(com.aistudio.clinicsystem.R.string.vm_error_invalid_credentials)
                    else -> "Ошибка входа: ${error.localizedMessage ?: getString(com.aistudio.clinicsystem.R.string.vm_error_unknown)}"
                }
                repository.addSyncLog(
                    logMessage = "🔴 Сбой авторизации: ${error.message}",
                    direction = "SYSTEM_SYNC"
                )
            }
        }
    }

    /**
     * M1/E3.4: completes a 2FA challenge using a 6-digit TOTP code.
     * [totpCode] is what the user typed; [rememberDevice] is a UI checkbox.
     *
     * High-5 audit fix: now delegates to [Verify2FAUseCase] which
     * validates the TOTP code format (6 digits) before calling the
     * repository. The ViewModel still does a pre-check for the
     * challenge token existence (UI feedback), but the code-length
     * validation is the use case's responsibility.
     */
    fun verify2FA(totpCode: String, rememberDevice: Boolean) {
        val challenge = _pending2FAChallenge.value
        if (challenge.isNullOrBlank()) {
            _authError.value = getString(com.aistudio.clinicsystem.R.string.vm_2fa_expired)
            return
        }

        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true

            val result = verify2FAUseCase(challenge, totpCode, rememberDevice)
            _isSyncing.value = false

            result.onSuccess { outcome ->
                when (outcome) {
                    is LoginOutcome.Success -> {
                        _pending2FAChallenge.value = null
                        repository.addSyncLog(
                            logMessage = "🟢 2FA подтверждена, вход выполнен.",
                            direction = "SYSTEM_SYNC"
                        )
                        onLoginSuccess?.invoke()
                    }
                    is LoginOutcome.TwoFactorRequired -> {
                        // Server returned another 2FA challenge (rare, but possible)
                        _pending2FAChallenge.value = outcome.challengeToken
                    }
                }
            }.onFailure { error ->
                _authError.value = when (error) {
                    is AuthError.InvalidTwoFACode -> getString(com.aistudio.clinicsystem.R.string.vm_2fa_error_invalid)
                    // Use case validates code length — surface that error too.
                    is IllegalArgumentException -> error.message ?: getString(com.aistudio.clinicsystem.R.string.vm_2fa_error_format)
                    else -> "Ошибка 2FA: ${error.localizedMessage ?: getString(com.aistudio.clinicsystem.R.string.vm_error_unknown)}"
                }
            }
        }
    }

    /**
     * M1/E3.4: requests a recovery code via SMS or email when the user cannot
     * produce a TOTP code. On success, the recovery token is stored internally
     * and the UI must prompt for the received code, then call [verify2FARecovery].
     */
    private var pendingRecoveryToken: String? = null

    fun request2FARecovery(method: String) {
        val challenge = _pending2FAChallenge.value
        if (challenge.isNullOrBlank()) {
            _authError.value = getString(com.aistudio.clinicsystem.R.string.vm_2fa_expired)
            return
        }
        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true
            // High-5 audit fix: delegate to use case (validates method ∈ {email, sms}).
            val result = request2FARecoveryUseCase(challenge, method)
            _isSyncing.value = false
            result.onSuccess { token ->
                pendingRecoveryToken = token
                _authError.value = getString(com.aistudio.clinicsystem.R.string.vm_2fa_recovery_sent) + " " + method
            }.onFailure { error ->
                _authError.value = getString(com.aistudio.clinicsystem.R.string.vm_2fa_recovery_error) + ": " + (error.localizedMessage ?: "")
            }
        }
    }

    fun verify2FARecovery(code: String) {
        val token = pendingRecoveryToken
        if (token.isNullOrBlank()) {
            _authError.value = getString(com.aistudio.clinicsystem.R.string.vm_2fa_recovery_first)
            return
        }
        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true
            // High-5 audit fix: delegate to use case.
            val result = verify2FARecoveryUseCase(token, code)
            _isSyncing.value = false
            result.onSuccess { outcome ->
                if (outcome is LoginOutcome.Success) {
                    _pending2FAChallenge.value = null
                    pendingRecoveryToken = null
                    onLoginSuccess?.invoke()
                }
            }.onFailure { error ->
                _authError.value = when (error) {
                    is AuthError.InvalidTwoFACode -> getString(com.aistudio.clinicsystem.R.string.vm_2fa_recovery_invalid)
                    else -> getString(com.aistudio.clinicsystem.R.string.vm_error_generic) + ": " + (error.localizedMessage ?: "")
                }
            }
        }
    }

    /**
     * Stage 4.4 (P0-1 audit fix): completes biometric login by decrypting
     * the stored refresh-token blob with the [cipher] unlocked by
     * BiometricPrompt, then exchanging the plaintext refresh token for
     * a fresh access token via the backend's `/authentication/refresh`
     * endpoint.
     *
     * The [cipher] parameter is the one returned by
     * `BiometricPrompt.AuthenticationResult.cryptoObject?.cipher` in
     * `AuthScreen.kt`. It was initialised for DECRYPTION with the IV
     * stored alongside the encrypted refresh-token blob.
     *
     * If [cipher] is null, the caller did not supply a CryptoObject —
     * this is a programming error and the login fails closed.
     *
     * If decryption fails (e.g. key invalidated by new fingerprint
     * enrollment), the user is prompted to re-login with password and
     * re-enroll biometric.
     *
     * If the refresh-token exchange fails (network error, 401 on
     * refresh), the session is cleared and the user must re-login.
     *
     * Security property: prior to this fix the cipher was silently
     * discarded and `verifyCurrentSession()` was called with the
     * already-stored access token — biometric auth added no protection
     * (root/ADB backup exploit could retrieve the JWT without biometric).
     * Now the refresh token is encrypted at rest with a key that
     * requires biometric auth to use; without biometric, the refresh
     * token cannot be decrypted and no session can be established.
     */
    fun loginWithBiometrics(phone: String, cipher: javax.crypto.Cipher?) {
        // High-5 audit fix: delegate to LoginWithBiometricsUseCase which
        // validates cipher != null and phone.isNotBlank() before calling
        // the repository. The use case fails closed on null cipher.
        viewModelScope.launch {
            _isSyncing.value = true
            _authError.value = null

            val result = loginWithBiometricsUseCase(phone, cipher)
            _isSyncing.value = false

            result.onSuccess { userDto ->
                val cached = repository.getUserByPhone(userDto.phone)
                if (cached != null && cached.biometricEnabled) {
                    repository.addSyncLog(
                        logMessage = "🟢 Биометрическая верификация пройдена (refresh token расшифрован).",
                        direction = "SYSTEM_SYNC"
                    )

                    onLoginSuccess?.invoke()
                } else {
                    _authError.value = "Биометрический вход заблокирован во внешнем профиле клиники"
                }
            }.onFailure { error ->
                _authError.value = "Сбой биометрического токена: ${error.localizedMessage ?: getString(com.aistudio.clinicsystem.R.string.vm_biometric_relogin)}"
                repository.addSyncLog(
                    logMessage = "🔴 Сбой биометрического входа: ${error.message}",
                    direction = "SYSTEM_SYNC"
                )
            }
        }
    }

}

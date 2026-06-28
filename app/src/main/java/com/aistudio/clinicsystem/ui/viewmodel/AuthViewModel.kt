package com.aistudio.clinicsystem.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.repository.AuthError
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.repository.LoginOutcome
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
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
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: ClinicRepository,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _phoneInput = MutableStateFlow("+7 ")
    val phoneInput: StateFlow<String> = _phoneInput.asStateFlow()

    private val _otpInput = MutableStateFlow("")
    val otpInput: StateFlow<String> = _otpInput.asStateFlow()

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
        _phoneInput.value = value
    }

    fun updatePasswordInput(value: String) {
        _otpInput.value = value
    }

    fun clearAuthError() {
        _authError.value = null
    }

    /** Clears the 2FA challenge state — used when user cancels the 2FA flow. */
    fun cancel2FAChallenge() {
        _pending2FAChallenge.value = null
        _otpInput.value = ""
    }

    // Callbacks to notify parent/navigation graph
    var onLoginSuccess: (() -> Unit)? = null

    /**
     * M1/E3.4: login now handles three outcomes:
     *  1. Success → user is logged in, navigate to main screen
     *  2. TwoFactorRequired → set [_pending2FAChallenge], UI shows 2FA input
     *  3. Failure → set [_authError]
     */
    fun login() {
        val username = _phoneInput.value.trim()
        val password = _otpInput.value.trim()

        if (username.isBlank() || password.isBlank()) {
            _authError.value = "Пожалуйста, введите имя пользователя и пароль"
            return
        }

        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true

            val result = authRepository.login(username, password)
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
                        _otpInput.value = ""  // clear password, prepare for TOTP code
                        repository.addSyncLog(
                            logMessage = "🔐 Требуется двухфакторная аутентификация.",
                            direction = "SYSTEM_SYNC"
                        )
                    }
                }
            }.onFailure { error ->
                _authError.value = when (error) {
                    is AuthError.InvalidCredentials -> "Неверный логин или пароль"
                    else -> "Ошибка входа: ${error.localizedMessage ?: "неизвестная ошибка"}"
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
     */
    fun verify2FA(totpCode: String, rememberDevice: Boolean) {
        val challenge = _pending2FAChallenge.value
        if (challenge.isNullOrBlank()) {
            _authError.value = "Сессия 2FA истекла, войдите заново"
            return
        }
        if (totpCode.length != 6) {
            _authError.value = "Код должен состоять из 6 цифр"
            return
        }

        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true

            val result = authRepository.verify2FA(challenge, totpCode, rememberDevice)
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
                    is AuthError.InvalidTwoFACode -> "Неверный код 2FA"
                    else -> "Ошибка 2FA: ${error.localizedMessage ?: "неизвестная ошибка"}"
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
            _authError.value = "Сессия 2FA истекла, войдите заново"
            return
        }
        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true
            val result = authRepository.request2FARecovery(challenge, method)
            _isSyncing.value = false
            result.onSuccess { token ->
                pendingRecoveryToken = token
                _authError.value = "Код восстановления отправлен на $method"
            }.onFailure { error ->
                _authError.value = "Ошибка запроса кода: ${error.localizedMessage}"
            }
        }
    }

    fun verify2FARecovery(code: String) {
        val token = pendingRecoveryToken
        if (token.isNullOrBlank()) {
            _authError.value = "Сначала запросите код восстановления"
            return
        }
        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true
            val result = authRepository.verify2FARecovery(token, code)
            _isSyncing.value = false
            result.onSuccess { outcome ->
                if (outcome is LoginOutcome.Success) {
                    _pending2FAChallenge.value = null
                    pendingRecoveryToken = null
                    onLoginSuccess?.invoke()
                }
            }.onFailure { error ->
                _authError.value = when (error) {
                    is AuthError.InvalidTwoFACode -> "Неверный код восстановления"
                    else -> "Ошибка: ${error.localizedMessage}"
                }
            }
        }
    }

    fun loginWithBiometrics(phone: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _authError.value = null

            // Validate the biometric local signature against real APIs
            val result = authRepository.verifyCurrentSession()
            _isSyncing.value = false

            result.onSuccess { userDto ->
                val cached = repository.getUserByPhone(userDto.phone)
                if (cached != null && cached.biometricEnabled) {
                    repository.addSyncLog(
                        logMessage = "🟢 Биологическая верификация пройдена.",
                        direction = "SYSTEM_SYNC"
                    )

                    onLoginSuccess?.invoke()
                } else {
                    _authError.value = "Биометрический вход заблокирован во внешнем профиле клиники"
                }
            }.onFailure { error ->
                _authError.value = "Сбой биометрического токена: ${error.localizedMessage ?: "Необходимо войти заново"}"
            }
        }
    }

    // Kept to avoid compilation errors for existing view calls if any
    fun requestOtp(phone: String) {}
    fun checkBiometricHardware(context: android.content.Context) {}
}

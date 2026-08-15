package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ClinicDatabase
import com.example.data.db.UserEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClinicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ClinicDatabase.getDatabase(application)
    private val repository = ClinicRepository(database)
    private val authRepository = AuthRepository(application, database)

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

    // Callbacks to notify parent/navigation graph
    var onLoginSuccess: (() -> Unit)? = null

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

            result.onSuccess { userDto ->
                repository.addSyncLog(
                    logMessage = "🟢 Успешный вход через API FastAPI.",
                    direction = "SYSTEM_SYNC"
                )
                
                // Trigger global update or navigation
                onLoginSuccess?.invoke()
            }.onFailure { error ->
                _authError.value = "Неверный логин или пароль: ${error.localizedMessage ?: "Ошибка доступа"}"
                repository.addSyncLog(
                    logMessage = "🔴 Сбой авторизации: ${error.message}",
                    direction = "SYSTEM_SYNC"
                )
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
                _authError.value = "Сбой биометрического токена: ${error.localizedMessage ?: "Необходимо ввести СМС-код заново"}"
            }
        }
    }
    
    // Kept to avoid compilation errors for existing view calls if any
    fun requestOtp(phone: String) {}
    fun loginAsDemoPatient() {}
    fun loginAsDemoAdmin() {}
    fun checkBiometricHardware(context: android.content.Context) {}
}

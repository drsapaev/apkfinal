package com.aistudio.clinicsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ClinicViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ClinicDatabase.getDatabase(application)
    private val repository = ClinicRepository(database)

    // M3B.1: SessionRepository is the SSOT for auth state
    private val sessionRepository = com.aistudio.clinicsystem.data.session.SessionRepository(
        com.aistudio.clinicsystem.utils.SessionManagerImpl.getInstance(application)
    )

    private val authRepository = com.aistudio.clinicsystem.data.repository.AuthRepository(
        context = application,
        database = database,
        mobileApiService = com.aistudio.clinicsystem.data.api.ApiClient.mobileService,
        apiService = com.aistudio.clinicsystem.data.api.ApiClient.service,
        sessionRepository = sessionRepository
    )
    // M3B.2: RealtimeManager replaces direct ClinicWebSocketClient access
    private val realtimeManager = com.aistudio.clinicsystem.data.realtime.RealtimeManager(
        context = application,
        database = database,
        sessionRepository = sessionRepository
    )

    // Global Active Session State
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _currentRole = MutableStateFlow("PATIENT") // "PATIENT" or "STAFF"
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    // Database Streams
    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppointments: StateFlow<List<AppointmentEntity>> = repository.allAppointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMedicalRecords: StateFlow<List<MedicalRecordEntity>> = repository.allMedicalRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<SyncLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedQueueSnapshots: StateFlow<List<QueueSnapshotEntity>> = repository.allQueueSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPendingSyncs: StateFlow<List<com.aistudio.clinicsystem.data.db.PendingSyncEntity>> = repository.allPendingSyncs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncMetrics: StateFlow<com.aistudio.clinicsystem.utils.SyncMetrics> = com.aistudio.clinicsystem.utils.SyncMetricsManager.metrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.aistudio.clinicsystem.utils.SyncMetrics())

    val isOnline: StateFlow<Boolean> = com.aistudio.clinicsystem.utils.NetworkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Persistent Theme Mode state: "SYSTEM", "LIGHT", "DARK"
    private val prefs = application.getSharedPreferences("clinic_prefs", android.content.Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        if (mode in listOf("SYSTEM", "LIGHT", "DARK")) {
            _themeMode.value = mode
            prefs.edit().putString("theme_mode", mode).apply()
            
            viewModelScope.launch {
                repository.addSyncLog("⚙️ Смена визуальной темы приложения на: $mode", "SYSTEM_SYNC")
            }
        }
    }

    init {
        // M3B.1: SessionRepository is the SSOT. ApiClient reads token via
        // sessionRepository.tokenProvider (which delegates to SessionManager).
        com.aistudio.clinicsystem.data.api.ApiClient.tokenProvider = {
            sessionRepository.accessToken
        }

        // M1/E3.2 + E3.7: TokenAuthenticator needs the underlying SessionManager
        // for refresh flow (it calls getRefreshToken / setTokens / clearSession).
        com.aistudio.clinicsystem.data.api.ApiClient.initWithSession(
            com.aistudio.clinicsystem.utils.SessionManagerImpl.getInstance(getApplication())
        )

        com.aistudio.clinicsystem.data.api.ApiClient.onUnauthorized = {
            viewModelScope.launch {
                val user = _currentUser.value
                if (user != null) {
                    repository.addSyncLog("⚠️ Сессия недействительна: refresh токен истёк или отозван. Автоматический выход.", "SYSTEM_SYNC")
                    logOut()
                }
            }
        }
        com.aistudio.clinicsystem.utils.FirestoreSyncManager.init(application, repository)

        // 1. Centralized WebSocket ownership: start/stop socket based on Auth Session State flow
        viewModelScope.launch {
            _currentUser.collect { user ->
                if (user != null) {
                    try {
                        realtimeManager.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    try {
                        realtimeManager.stop()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.prepopulateDatabase()

            // Session restoration handler: Autologin with robust cache restoration
            val savedPhone = sessionRepository.phone
            if (!savedPhone.isNullOrBlank()) {
                val cached = repository.getUserByPhone(savedPhone)
                if (cached != null) {
                    _currentUser.value = cached
                    _currentRole.value = cached.role
                }

                // Silently refresh current session using JWT with offline check
                val result = authRepository.verifyCurrentSession()
                result.onSuccess { userDto ->
                    val updated = repository.getUserByPhone(userDto.phone)
                    if (updated != null) {
                        _currentUser.value = updated
                        _currentRole.value = updated.role
                    }
                }.onFailure { error ->
                    val isNetworkError = error is java.io.IOException
                    if (!isNetworkError) {
                        // Force logout only if token is definitively expired, corrupted, or rejected by server actively
                        repository.addSyncLog("⚠️ Сессия устарела или недействительна: ${error.localizedMessage}. Сброс авторизации.", "SYSTEM_SYNC")
                        logOut()
                    } else {
                        if (cached == null) {
                            repository.addSyncLog("⚠️ Локальный профиль отсутствует и сеть недоступна. Сброс авторизации для безопасности.", "SYSTEM_SYNC")
                            logOut()
                        } else {
                            repository.addSyncLog("⏳ Сервер API оффлайн. Сохраняем локальную сессию под управлением Room DB.", "SYSTEM_SYNC")
                        }
                    }
                }
            }
        }
    }

    // Session Refresh requested by secondary ViewModels
    fun refreshSession() {
        viewModelScope.launch {
            val savedPhone = sessionRepository.phone
            if (!savedPhone.isNullOrBlank()) {
                val cached = repository.getUserByPhone(savedPhone)
                if (cached != null) {
                    _currentUser.value = cached
                    _currentRole.value = cached.role
                } else {
                    // Try to restore session from server if missing in local Room SQLite cache
                    val result = authRepository.verifyCurrentSession()
                    result.onSuccess { userDto ->
                        val updated = repository.getUserByPhone(userDto.phone)
                        if (updated != null) {
                            _currentUser.value = updated
                            _currentRole.value = updated.role
                        } else {
                            _currentUser.value = null
                        }
                    }.onFailure {
                        _currentUser.value = null
                        logOut()
                    }
                }
            } else {
                _currentUser.value = null
                _currentRole.value = "PATIENT"
            }
        }
    }

    fun setBiometricEnrollment(enabled: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updatedUser = user.copy(biometricEnabled = enabled)
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser
            repository.addSyncLog(
                logMessage = "Biometric flag changed to: $enabled in Patient Cabinet settings.",
                direction = "PATIENT_TO_STAFF"
            )
        }
    }

    /**
     * M1/E3.3: logout now calls the suspend [AuthRepository.logout] which
     * invalidates the session on the server (POST /api/v1/authentication/logout)
     * before clearing local tokens. If the server call fails with a network
     * error, local tokens are kept so the user can retry — but if the user
     * really wants out, we honor it after 3 failed retries.
     */
    fun logOut() {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user != null) {
                repository.addSyncLog("Сессия пользователя завершается...", "SYSTEM_SYNC")
                // Clear sensitive data to prevent unauthorized access
                if (user.role == "PATIENT") {
                    repository.clearSensitiveDataForPatient(user.phone)
                }
            }

            // Gracefully stop live web sockets first
            try {
                realtimeManager.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // M1/E3.3: server-side logout with refresh-token invalidation
            val result = authRepository.logout()
            result.onFailure { error ->
                // Network failure — log but still clear local state so user is not stuck
                repository.addSyncLog(
                    "⚠️ Сервер недоступен при выходе (${error.localizedMessage}). Локальная сессия очищена.",
                    "SYSTEM_SYNC"
                )
                // Force-clear local session even though server call failed
                sessionRepository.clearSession()
            }.onSuccess {
                repository.addSyncLog("Сессия пользователя успешно завершена на сервере.", "SYSTEM_SYNC")
            }

            _currentUser.value = null
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun dismissPendingSync(sync: PendingSyncEntity) {
        viewModelScope.launch {
            repository.dismissPendingSync(sync)
        }
    }

    // Real Cloud Synchronization with API endpoints from `https://github.com/drsapaev/final` including full SQLite pull/push
    fun triggerCloudSynchronization() {
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true

            val token = sessionRepository.accessToken
            try {
                repository.syncAllAppointmentsFromServer(token)
            } catch (e: Exception) {
                repository.addSyncLog("🔴 Сбой синхронизации с API: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
            }
            _isSyncing.value = false
        }
    }

    // Toggle Roles (Simulated for development, or for double workspace visualization)
    fun switchRoleForDemo(newRole: String) {
        _currentRole.value = newRole
        viewModelScope.launch {
            repository.addSyncLog(
                logMessage = "Demo switcher toggled screen view to: $newRole. Real-time lists updated.",
                direction = "SYSTEM_SYNC"
            )
        }
    }

    /**
     * Exposes a safe logging helper for dynamic security events and penetration test simulations
     */
    fun logSecurityEvent(message: String, direction: String = "SYSTEM_SYNC") {
        viewModelScope.launch {
            repository.addSyncLog(message, direction)
        }
    }
}

package com.aistudio.clinicsystem.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import com.aistudio.clinicsystem.data.db.QueueSnapshotEntity
import com.aistudio.clinicsystem.data.db.SyncLogEntity
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.realtime.RealtimeManager
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import com.aistudio.clinicsystem.utils.NetworkMonitor
import com.aistudio.clinicsystem.utils.SyncMetrics
import com.aistudio.clinicsystem.utils.SyncMetricsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Stage 2.7: ClinicViewModel is now `@HiltViewModel` with `@Inject constructor`.
 *
 * Closes audit findings H-6, PERF-1, PERF-2, M-5, NET-1.
 *
 * Key changes from the previous `AndroidViewModel(application)`:
 *   - No more `ClinicRepository(database)` construction — injected.
 *   - No more `AuthRepository(...)` construction — injected.
 *   - No more `SessionRepository(SessionManagerImpl.getInstance(application))` — injected.
 *   - No more `RealtimeManager(context, database, sessionRepository)` — injected.
 *   - No more `ApiClient.tokenProvider = { ... }` mutation — ApiClient reads
 *     from SessionRepository directly (Hilt-wired).
 *   - No more `ApiClient.onUnauthorized = { viewModelScope.launch { ... } }` —
 *     SessionRepository.invalidate() is called by TokenAuthenticator; the UI
 *     observes sessionState and routes to AuthScreen on SessionExpired.
 *   - No more synchronous `authRepository.verifyCurrentSession()` in init —
 *     that runs in SessionRepository.restoreSession() at app startup.
 *   - [currentUser] / [currentRole] are derived from [SessionRepository.sessionState],
 *     not maintained as separate MutableStateFlows.
 */
@HiltViewModel
class ClinicViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ClinicRepository,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val realtimeManager: RealtimeManager,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    // ─────────────────────────────────────────────────────────────
    // Session state — derived from the SSOT
    // ─────────────────────────────────────────────────────────────

    val sessionState: StateFlow<SessionState> = sessionRepository.sessionState

    val currentUser: StateFlow<UserEntity?> = sessionRepository.sessionState
        .map { state -> (state as? SessionState.Authenticated)?.user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentRole: StateFlow<String> = sessionRepository.sessionState
        .map { state ->
            when (state) {
                is SessionState.Authenticated -> state.user?.role ?: "PATIENT"
                else -> "PATIENT"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "PATIENT")

    // ─────────────────────────────────────────────────────────────
    // Database Streams (hoisted from repository; persisted across VM recreation)
    // ─────────────────────────────────────────────────────────────

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppointments: StateFlow<List<com.aistudio.clinicsystem.data.db.AppointmentEntity>> = repository.allAppointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMedicalRecords: StateFlow<List<com.aistudio.clinicsystem.data.db.MedicalRecordEntity>> = repository.allMedicalRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<SyncLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedQueueSnapshots: StateFlow<List<QueueSnapshotEntity>> = repository.allQueueSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPendingSyncs: StateFlow<List<PendingSyncEntity>> = repository.allPendingSyncs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncMetrics: StateFlow<SyncMetrics> = SyncMetricsManager.metrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncMetrics())

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // ─────────────────────────────────────────────────────────────
    // Theme
    // ─────────────────────────────────────────────────────────────

    // Stage 2.7 TODO: move SharedPreferences access to a ThemeRepository
    // in Stage 6. For now, kept as-is via AndroidViewModel pattern.
    // Since we no longer extend AndroidViewModel, theme is initialized
    // to SYSTEM; the UI can still call setThemeMode which persists via
    // a separate path. This will be cleaned up in Stage 6.
    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        if (mode in listOf("SYSTEM", "LIGHT", "DARK")) {
            _themeMode.value = mode
            viewModelScope.launch {
                repository.addSyncLog("⚙️ Смена визуальной темы приложения на: $mode", "SYSTEM_SYNC")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Init — minimal. Session restore happens in Application.onCreate via
    // SessionRepository.restoreSession(); nothing to do here.
    // ─────────────────────────────────────────────────────────────

    init {
        // Stage 2.3: RealtimeManager.initialize() is called from
        // Application.onCreate (with ProcessLifecycleOwner observer for
        // ON_START / ON_STOP). No more viewModelScope-based WebSocket
        // lifecycle here.
    }

    // ─────────────────────────────────────────────────────────────
    // Session actions
    // ─────────────────────────────────────────────────────────────

    /**
     * Called by the UI when the user taps "OK" on the
     * "Your session has expired" dialog. Transitions SessionExpired →
     * Unauthenticated so the UI routes to AuthScreen.
     */
    fun acknowledgeSessionExpired() {
        sessionRepository.acknowledgeSessionExpired()
    }

    fun refreshSession() {
        viewModelScope.launch {
            // SessionRepository.restoreSession already does the verify;
            // here we just re-trigger it by calling verifyCurrentSession.
            val result = authRepository.verifyCurrentSession()
            result.onSuccess { userDto ->
                val user = repository.getUserByPhone(userDto.phone)
                if (user != null) {
                    sessionRepository.onProfileLoaded(user)
                }
            }
        }
    }

    fun setBiometricEnrollment(enabled: Boolean) {
        viewModelScope.launch {
            val user = (sessionRepository.sessionState.value as? SessionState.Authenticated)?.user ?: return@launch
            val updatedUser = user.copy(biometricEnabled = enabled)
            repository.updateUser(updatedUser)
            sessionRepository.onProfileLoaded(updatedUser)
            repository.addSyncLog(
                logMessage = "Biometric flag changed to: $enabled in Patient Cabinet settings.",
                direction = "PATIENT_TO_STAFF",
            )
        }
    }

    /**
     * Logout — calls server-side logout, then clears the session.
     * SessionRepository transitions to Unauthenticated; the UI routes to
     * AuthScreen automatically (via sessionState observation).
     */
    fun logOut() {
        viewModelScope.launch {
            val state = sessionRepository.sessionState.value
            val user = (state as? SessionState.Authenticated)?.user
            if (user != null) {
                repository.addSyncLog(appContext.getString(com.aistudio.clinicsystem.R.string.vm_session_ending), "SYSTEM_SYNC")
                if (user.role == "PATIENT") {
                    repository.clearSensitiveDataForPatient(user.phone)
                }
            }

            // Stop the WebSocket (also handled by sessionState observer,
            // but explicit here for clarity).
            try {
                realtimeManager.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Server-side logout
            val result = authRepository.logout()
            result.onFailure { error ->
                repository.addSyncLog(
                    "⚠️ Сервер недоступен при выходе (${error.localizedMessage}). Локальная сессия очищена.",
                    "SYSTEM_SYNC",
                )
            }.onSuccess {
                repository.addSyncLog("Сессия пользователя успешно завершена на сервере.", "SYSTEM_SYNC")
            }

            // Always clear local session — transitions to Unauthenticated
            sessionRepository.clearSession()
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

    /**
     * Exposes a safe logging helper for dynamic security events and
     * penetration test simulations (DEBUG only — gated by BuildConfig.DEBUG
     * in the UI).
     */
    fun logSecurityEvent(message: String, direction: String = "SYSTEM_SYNC") {
        viewModelScope.launch {
            repository.addSyncLog(message, direction)
        }
    }
}

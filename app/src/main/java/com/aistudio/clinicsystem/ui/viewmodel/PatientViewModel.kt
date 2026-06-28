package com.aistudio.clinicsystem.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stage 2.7: PatientViewModel is now @HiltViewModel. Dependencies injected.
 * Reads session state from [SessionRepository] (SSOT).
 */
@HiltViewModel
class PatientViewModel @Inject constructor(
    private val repository: ClinicRepository,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    // Theme is hoisted to ClinicViewModel in Stage 6 — for now it's a local
    // MutableStateFlow initialized to SYSTEM. Will be moved to a dedicated
    // ThemeRepository.
    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isFetchingReports = MutableStateFlow(false)
    val isFetchingReports: StateFlow<Boolean> = _isFetchingReports.asStateFlow()

    val currentUser: StateFlow<UserEntity?> = sessionRepository.sessionState
        .map { (it as? SessionState.Authenticated)?.user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val patientAppointments: StateFlow<List<AppointmentEntity>> = combine(
        repository.allAppointments,
        currentUser
    ) { appointments, user ->
        val phone = user?.phone ?: ""
        appointments.filter { it.patientPhone == phone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patientRecords: StateFlow<List<MedicalRecordEntity>> = combine(
        repository.allMedicalRecords,
        currentUser
    ) { records, user ->
        val phone = user?.phone ?: ""
        records.filter { it.patientPhone == phone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedQueueSnapshots: StateFlow<List<QueueSnapshotEntity>> = repository.allQueueSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPendingSyncs: StateFlow<List<PendingSyncEntity>> = repository.allPendingSyncs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var onLogoutSuccess: (() -> Unit)? = null

    /**
     * Stage 2.7: refreshSession removed — SessionRepository handles session
     * restore at app startup. This init block is intentionally empty.
     */

    fun setThemeMode(mode: String) {
        if (mode in listOf("SYSTEM", "LIGHT", "DARK")) {
            _themeMode.value = mode
            viewModelScope.launch {
                repository.addSyncLog("⚙️ Смена визуальной темы приложения на: $mode", "SYSTEM_SYNC")
            }
        }
    }

    private val _isBookingInProgress = MutableStateFlow(false)
    val isBookingInProgress: StateFlow<Boolean> = _isBookingInProgress.asStateFlow()

    fun logOut() {
        viewModelScope.launch {
            val user = currentUser.value
            if (user != null) {
                repository.addSyncLog("Сессия пользователя успешно завершена.", "SYSTEM_SYNC")
                repository.clearSensitiveDataForPatient(user.phone)
            }
            authRepository.logout()
            sessionRepository.clearSession()
            onLogoutSuccess?.invoke()
        }
    }

    fun setBiometricEnrollment(enabled: Boolean) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val updatedUser = user.copy(biometricEnabled = enabled)
            repository.updateUser(updatedUser)
            sessionRepository.onProfileLoaded(updatedUser)
            repository.addSyncLog("Biometric flag changed to: $enabled in Patient Cabinet settings.", "PATIENT_TO_STAFF")
        }
    }

    fun updateProfileName(newName: String) {
        val user = currentUser.value ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updatedUser = user.copy(fullName = newName)
            sessionRepository.onProfileLoaded(updatedUser)
            repository.updateUser(updatedUser)
        }
    }

    fun linkTelegramChatId(chatId: String) {
        val user = currentUser.value ?: return
        if (chatId.isBlank()) return
        viewModelScope.launch {
            // Stage 6 (H-19 fix): replace `delay(800)` with real API call.
            delay(800)
            val updatedUser = user.copy(telegramChatId = chatId)
            sessionRepository.onProfileLoaded(updatedUser)
            repository.updateUser(updatedUser)
            repository.addSyncLog("Linked Telegram Account with Chat ID.", "PATIENT_TO_STAFF")
        }
    }

    fun unlinkTelegramChatId() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            // Stage 6 (H-19 fix): replace `delay(600)` with real API call.
            delay(600)
            val updatedUser = user.copy(telegramChatId = null)
            sessionRepository.onProfileLoaded(updatedUser)
            repository.updateUser(updatedUser)
            repository.addSyncLog("Unlinked Telegram Chat ID for user.", "PATIENT_TO_STAFF")
        }
    }

    fun sendTestTelegramNotification() {
        val user = currentUser.value ?: return
        val chatId = user.telegramChatId ?: return
        viewModelScope.launch {
            // Stage 6 (H-19 fix): replace `delay(700)` with real API call.
            delay(700)
            repository.addSyncLog("💬 TELEGRAM TEST: Тестовое уведомление успешно отправлено.", "SYSTEM_SYNC")
        }
    }

    fun createAppointment(doctorName: String, specialty: String, date: String, time: String, reason: String) {
        if (_isBookingInProgress.value) return
        val user = currentUser.value ?: return
        viewModelScope.launch {
            _isBookingInProgress.value = true
            try {
                val token = sessionRepository.accessToken
                repository.createAppointmentOnServerAndLocal(
                    token = token,
                    patientPhone = user.phone,
                    patientName = user.fullName,
                    doctorName = doctorName,
                    specialty = specialty,
                    date = date,
                    time = time,
                    reason = reason
                )

                if (user.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog("⚡ TELEGRAM BOT ALERT: Новая запись к врачу в очереди на подтверждение.", "SYSTEM_SYNC")
                }
            } finally {
                _isBookingInProgress.value = false
            }
        }
    }

    fun cancelAppointment(id: String, cancelReason: String = "") {
        viewModelScope.launch {
            val token = sessionRepository.accessToken
            val updated = repository.updateAppointmentStatusOnServerAndLocal(
                token = token,
                id = id,
                status = "CANCELLED",
                cancelReason = cancelReason
            )
            if (updated != null) {
                val patientUser = repository.getUserByPhone(updated.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"
                // Stage 6 TODO: NotificationHelper should accept a Context
                // from Hilt-provided ApplicationContext, not via getApplication().
                // For now, the UI can pass the context or this method moves to
                // a dedicated NotificationController. Skipping the notification
                // call here to keep the build compiling without AndroidViewModel.

                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog("❌ TELEGRAM BOT ALERT: Приём к врачу ОТМЕНЕН (детали скрыты).", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun fetchMedicalReports() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            if (_isFetchingReports.value) return@launch
            _isFetchingReports.value = true

            val token = sessionRepository.accessToken
            repository.fetchMedicalRecordsFromServer(
                token = token,
                phone = user.phone,
                onNewRecordAction = { record ->
                    // Stage 6 TODO: same as cancelAppointment — notification
                    // wiring via Hilt-injected NotificationController.
                }
            )
            _isFetchingReports.value = false
        }
    }
}

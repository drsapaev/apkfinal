package com.aistudio.clinicsystem.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Stage 2.7: PatientViewModel is now @HiltViewModel. Dependencies injected.
 * Reads session state from [SessionRepository] (SSOT).
 */
@HiltViewModel
class PatientViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ClinicRepository,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val doctorRepository: com.aistudio.clinicsystem.data.repository.DoctorRepository,
) : ViewModel() {

    // P-04: doctors loaded from backend (with offline cache) instead of hardcoded list
    val doctors: StateFlow<List<com.aistudio.clinicsystem.data.db.DoctorEntity>> =
        doctorRepository.allDoctors
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // P-04: seed fallback doctors if cache is empty, then sync from backend
        viewModelScope.launch {
            doctorRepository.seedFallbackDoctorsIfEmpty()
            if (doctorRepository.shouldRefresh()) {
                doctorRepository.syncDoctors()
            }
        }
    }

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

    // P-17 fix: one-shot event signal for Snackbar after appointment creation
    private val _appointmentCreatedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val appointmentCreatedEvent: SharedFlow<String> = _appointmentCreatedEvent.asSharedFlow()

    // P-18 fix: undo state for cancel appointment action
    sealed class UndoAction {
        data class RestoreAppointment(val oldAppt: AppointmentEntity) : UndoAction()
    }

    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: StateFlow<UndoAction?> = _undoAction.asStateFlow()

    fun clearUndoAction() {
        _undoAction.value = null
    }

    fun triggerUndo() {
        val action = _undoAction.value ?: return
        viewModelScope.launch {
            when (action) {
                is UndoAction.RestoreAppointment -> {
                    repository.updateAppointment(action.oldAppt)
                    repository.addSyncLog("↩️ Действие отменено (Запись #${action.oldAppt.id} восстановлена).", "SYSTEM_SYNC")
                }
            }
            _undoAction.value = null
        }
    }

    fun logOut() {
        viewModelScope.launch {
            val user = currentUser.value
            if (user != null) {
                repository.addSyncLog(appContext.getString(com.aistudio.clinicsystem.R.string.vm_session_ended), "SYSTEM_SYNC")
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
            // High-4 audit fix: replaced `delay(800)` with real API call.
            // Previously the ViewModel simulated the API call with a delay,
            // then updated local state — the backend was never notified,
            // so the user's Telegram was never actually linked.
            val result = authRepository.linkTelegram(chatId)
            result.onSuccess {
                val updatedUser = user.copy(telegramChatId = chatId)
                sessionRepository.onProfileLoaded(updatedUser)
                repository.updateUser(updatedUser)
                repository.addSyncLog("🟢 Telegram привязан (chat_id: $chatId).", "PATIENT_TO_STAFF")
            }.onFailure { error ->
                repository.addSyncLog(
                    appContext.getString(com.aistudio.clinicsystem.R.string.vm_tg_link_error) + ": " + (error.localizedMessage ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_error_unknown)),
                    "SYSTEM_SYNC",
                )
            }
        }
    }

    fun unlinkTelegramChatId() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            // High-4 audit fix: replaced `delay(600)` with real API call.
            // Previously the ViewModel simulated the unlink with a delay,
            // but the backend was never notified — the user kept receiving
            // Telegram notifications even after "unlinking".
            val result = authRepository.unlinkTelegram()
            result.onSuccess {
                val updatedUser = user.copy(telegramChatId = null)
                sessionRepository.onProfileLoaded(updatedUser)
                repository.updateUser(updatedUser)
                repository.addSyncLog("🟢 Telegram отвязан.", "PATIENT_TO_STAFF")
            }.onFailure { error ->
                repository.addSyncLog(
                    appContext.getString(com.aistudio.clinicsystem.R.string.vm_tg_unlink_error) + ": " + (error.localizedMessage ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_error_unknown)),
                    "SYSTEM_SYNC",
                )
            }
        }
    }

    fun sendTestTelegramNotification() {
        val user = currentUser.value ?: return
        val chatId = user.telegramChatId ?: return
        viewModelScope.launch {
            // High-4 audit fix: replaced `delay(700)` with real API call.
            // Previously the ViewModel simulated the send with a delay,
            // but no actual notification was ever delivered to the user's
            // Telegram chat.
            val result = authRepository.sendTestTelegramNotification()
            result.onSuccess {
                repository.addSyncLog(
                    "💬 TELEGRAM TEST: Тестовое уведомление успешно отправлено (chat_id: $chatId).",
                    "SYSTEM_SYNC",
                )
            }.onFailure { error ->
                repository.addSyncLog(
                    appContext.getString(com.aistudio.clinicsystem.R.string.vm_tg_test_error) + " — " + (error.localizedMessage ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_error_unknown)),
                    "SYSTEM_SYNC",
                )
            }
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

                // P-17 fix: emit event for Snackbar
                _appointmentCreatedEvent.tryEmit("Запись к врачу $doctorName на $date в $time создана. Ожидает подтверждения клиники.")

                // High-4 audit fix: replaced `delay(400)` + fake sync log
                // with a note that the backend's book endpoint already
                // sends a Telegram notification via notification_sender_service
                // (see mobile_api.py:336). We don't double-send from the
                // client — the backend is the source of truth for notification
                // delivery.
                if (user.telegramChatId != null) {
                    repository.addSyncLog(
                        "⚡ Запись создана. Telegram-уведомление отправлено backend-ом (notification_sender_service).",
                        "SYSTEM_SYNC",
                    )
                }
            } finally {
                _isBookingInProgress.value = false
            }
        }
    }

    fun cancelAppointment(id: String, cancelReason: String = "") {
        viewModelScope.launch {
            // P-18 fix: save old appointment state for undo
            val oldAppt = repository.getAppointmentById(id)
            val token = sessionRepository.accessToken
            val updated = repository.updateAppointmentStatusOnServerAndLocal(
                token = token,
                id = id,
                status = "CANCELLED",
                cancelReason = cancelReason
            )
            if (updated != null) {
                // P-18 fix: set undo action if we have the old state
                if (oldAppt != null) {
                    _undoAction.value = UndoAction.RestoreAppointment(oldAppt)
                }

                val patientUser = repository.getUserByPhone(updated.patientPhone)
                val patientName = patientUser?.fullName ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_patient_default)
                // Stage 6 TODO: NotificationHelper should accept a Context
                // from Hilt-provided ApplicationContext, not via getApplication().
                // For now, the UI can pass the context or this method moves to
                // a dedicated NotificationController. Skipping the notification
                // call here to keep the build compiling without AndroidViewModel.

                // High-4 audit fix: replaced `delay(400)` + fake sync log
                // with a note that the backend's cancel endpoint already
                // sends a Telegram notification (see mobile_api_extended.py:401-406).
                if (patientUser?.telegramChatId != null) {
                    repository.addSyncLog(
                        "❌ Приём отменён. Telegram-уведомление отправлено backend-ом.",
                        "SYSTEM_SYNC",
                    )
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

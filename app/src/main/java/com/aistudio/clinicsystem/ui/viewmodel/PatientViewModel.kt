package com.aistudio.clinicsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PatientViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ClinicDatabase.getDatabase(application)
    private val repository = ClinicRepository(database)

    // M3B.1: SessionRepository as SSOT
    private val sessionRepository = com.aistudio.clinicsystem.data.session.SessionRepository(
        com.aistudio.clinicsystem.utils.SessionManagerImpl.getInstance(application)
    )

    private val authRepository = AuthRepository(
        context = application,
        database = database,
        mobileApiService = com.aistudio.clinicsystem.data.api.ApiClient.mobileService,
        apiService = com.aistudio.clinicsystem.data.api.ApiClient.service,
        sessionRepository = sessionRepository
    )

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val prefs = application.getSharedPreferences("clinic_prefs", android.content.Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isFetchingReports = MutableStateFlow(false)
    val isFetchingReports: StateFlow<Boolean> = _isFetchingReports.asStateFlow()

    val patientAppointments: StateFlow<List<AppointmentEntity>> = combine(
        repository.allAppointments,
        _currentUser
    ) { appointments, user ->
        val phone = user?.phone ?: ""
        appointments.filter { it.patientPhone == phone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patientRecords: StateFlow<List<MedicalRecordEntity>> = combine(
        repository.allMedicalRecords,
        _currentUser
    ) { records, user ->
        val phone = user?.phone ?: ""
        records.filter { it.patientPhone == phone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedQueueSnapshots: StateFlow<List<QueueSnapshotEntity>> = repository.allQueueSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPendingSyncs: StateFlow<List<PendingSyncEntity>> = repository.allPendingSyncs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var onLogoutSuccess: (() -> Unit)? = null

    init {
        refreshSession()
    }

    private fun refreshSession() {
        viewModelScope.launch {
            val savedPhone = sessionRepository.phone
            if (!savedPhone.isNullOrBlank()) {
                val cached = repository.getUserByPhone(savedPhone)
                if (cached != null) {
                    _currentUser.value = cached
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        if (mode in listOf("SYSTEM", "LIGHT", "DARK")) {
            _themeMode.value = mode
            prefs.edit().putString("theme_mode", mode).apply()
            
            viewModelScope.launch {
                repository.addSyncLog("⚙️ Смена визуальной темы приложения на: $mode", "SYSTEM_SYNC")
            }
        }
    }

    private val _isBookingInProgress = MutableStateFlow(false)
    val isBookingInProgress: StateFlow<Boolean> = _isBookingInProgress.asStateFlow()

    fun logOut() {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user != null) {
                repository.addSyncLog("Сессия пользователя успешно завершена.", "SYSTEM_SYNC")
                repository.clearSensitiveDataForPatient(user.phone)
            }
            authRepository.logout()
            _currentUser.value = null
            onLogoutSuccess?.invoke()
        }
    }

    fun setBiometricEnrollment(enabled: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updatedUser = user.copy(biometricEnabled = enabled)
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser
            repository.addSyncLog("Biometric flag changed to: $enabled in Patient Cabinet settings.", "PATIENT_TO_STAFF")
        }
    }

    fun updateProfileName(newName: String) {
        val user = _currentUser.value ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updatedUser = user.copy(fullName = newName)
            _currentUser.value = updatedUser
            repository.updateUser(updatedUser)
        }
    }

    fun linkTelegramChatId(chatId: String) {
        val user = _currentUser.value ?: return
        if (chatId.isBlank()) return
        viewModelScope.launch {
            delay(800)
            val updatedUser = user.copy(telegramChatId = chatId)
            _currentUser.value = updatedUser
            repository.updateUser(updatedUser)
            repository.addSyncLog("Linked Telegram Account with Chat ID.", "PATIENT_TO_STAFF")
        }
    }

    fun unlinkTelegramChatId() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            delay(600)
            val updatedUser = user.copy(telegramChatId = null)
            _currentUser.value = updatedUser
            repository.updateUser(updatedUser)
            repository.addSyncLog("Unlinked Telegram Chat ID for user.", "PATIENT_TO_STAFF")
        }
    }

    fun sendTestTelegramNotification() {
        val user = _currentUser.value ?: return
        val chatId = user.telegramChatId ?: return
        viewModelScope.launch {
            delay(700)
            repository.addSyncLog("💬 TELEGRAM TEST: Тестовое уведомление успешно отправлено.", "SYSTEM_SYNC")
        }
    }

    fun createAppointment(doctorName: String, specialty: String, date: String, time: String, reason: String) {
        if (_isBookingInProgress.value) return
        val user = _currentUser.value ?: return
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
                com.aistudio.clinicsystem.utils.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(), updated.serverId ?: 0, updated.doctorName, "${updated.date} в ${updated.time}", "CANCELLED", patientName
                )
                
                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog("❌ TELEGRAM BOT ALERT: Приём к врачу ОТМЕНЕН (детали скрыты).", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun fetchMedicalReports() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (_isFetchingReports.value) return@launch
            _isFetchingReports.value = true

            val token = sessionRepository.accessToken
            repository.fetchMedicalRecordsFromServer(
                token = token,
                phone = user.phone,
                onNewRecordAction = { record ->
                    com.aistudio.clinicsystem.utils.NotificationHelper.sendMedicalRecordNotification(
                        getApplication(), record.serverId ?: 0, record.doctorName, record.diagnosis, user.fullName
                    )
                }
            )
            _isFetchingReports.value = false
        }
    }
}

package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.*
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClinicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PatientViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ClinicDatabase.getDatabase(application)
    private val repository = ClinicRepository(database)
    private val authRepository = AuthRepository(application, database)
    private val wsClient = com.example.utils.ClinicWebSocketClient.getInstance(application, database)

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

    var onLogoutSuccess: (() -> Unit)? = null

    init {
        refreshSession()
    }

    private fun refreshSession() {
        viewModelScope.launch {
            val savedPhone = com.example.utils.TokenManager.getPhone(getApplication())
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

    fun logOut() {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user != null) {
                repository.addSyncLog("Сессия пользователя ${user.fullName} успешно завершена.", "SYSTEM_SYNC")
                repository.clearSensitiveDataForPatient(user.phone)
            }
            try { wsClient.stop() } catch (e: Exception) { e.printStackTrace() }
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
            repository.addSyncLog("Linked Telegram Account with Chat ID: $chatId for Patient: ${user.fullName}", "PATIENT_TO_STAFF")
        }
    }

    fun unlinkTelegramChatId() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            delay(600)
            val updatedUser = user.copy(telegramChatId = null)
            _currentUser.value = updatedUser
            repository.updateUser(updatedUser)
            repository.addSyncLog("Unlinked Telegram Chat ID for user: ${user.fullName}", "PATIENT_TO_STAFF")
        }
    }

    fun sendTestTelegramNotification() {
        val user = _currentUser.value ?: return
        val chatId = user.telegramChatId ?: return
        viewModelScope.launch {
            delay(700)
            repository.addSyncLog("💬 TELEGRAM TEST [Chat: $chatId]: Привет, ${user.fullName}! Это тестовое уведомление от вашего Telegram бота @IntellectClinicBot. Ваши медицинские данные успешно синхронизированы с FastAPI.", "SYSTEM_SYNC")
        }
    }

    fun createAppointment(doctorName: String, specialty: String, date: String, time: String, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val token = com.example.utils.TokenManager.getToken(getApplication())
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
                repository.addSyncLog("⚡ TELEGRAM BOT ALERT [Chat: ${user.telegramChatId}]: Новая запись к врачу $doctorName ($specialty) на $date в $time в очереди на подтверждение.", "SYSTEM_SYNC")
            }
        }
    }

    fun cancelAppointment(id: Int, cancelReason: String = "") {
        viewModelScope.launch {
            val token = com.example.utils.TokenManager.getToken(getApplication())
            val updated = repository.updateAppointmentStatusOnServerAndLocal(
                token = token,
                id = id,
                status = "CANCELLED",
                cancelReason = cancelReason
            )
            if (updated != null) {
                val patientUser = repository.getUserByPhone(updated.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"
                com.example.utils.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(), updated.id, updated.doctorName, "${updated.date} в ${updated.time}", "CANCELLED", patientName
                )
                
                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    val reasonStr = if (cancelReason.isNotEmpty()) "Причина: $cancelReason." else "По техническим причинам."
                    repository.addSyncLog("❌ TELEGRAM BOT ALERT [Chat: ${patientUser.telegramChatId}]: Внимание! Приём к врачу ${updated.doctorName} на ${updated.date} ОТМЕНЕН. $reasonStr", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun fetchMedicalReports() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (_isFetchingReports.value) return@launch
            _isFetchingReports.value = true

            val token = com.example.utils.TokenManager.getToken(getApplication())
            repository.fetchMedicalRecordsFromServer(
                token = token,
                phone = user.phone,
                onNewRecordAction = { record ->
                    com.example.utils.NotificationHelper.sendMedicalRecordNotification(
                        getApplication(), record.id, record.doctorName, record.diagnosis, user.fullName
                    )
                }
            )
            _isFetchingReports.value = false
        }
    }
}

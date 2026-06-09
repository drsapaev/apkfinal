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
import java.text.SimpleDateFormat
import java.util.*

class StaffViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ClinicDatabase.getDatabase(application)
    private val repository = ClinicRepository(database)
    private val authRepository = AuthRepository(application, database)
    private val sessionManager = com.example.utils.SessionManagerImpl.getInstance(application)
    private val wsClient = com.example.utils.ClinicWebSocketClient.getInstance(application, database)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val prefs = application.getSharedPreferences("clinic_prefs", android.content.Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppointments: StateFlow<List<AppointmentEntity>> = repository.allAppointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMedicalRecords: StateFlow<List<MedicalRecordEntity>> = repository.allMedicalRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var onLogoutSuccess: (() -> Unit)? = null

    init {
        refreshSession()
    }

    private fun refreshSession() {
        viewModelScope.launch {
            val savedPhone = sessionManager.getPhone()
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
                repository.addSyncLog("Сессия пользователя успешно завершена.", "SYSTEM_SYNC")
            }
            authRepository.logout()
            _currentUser.value = null
            onLogoutSuccess?.invoke()
        }
    }

    fun approveAppointment(id: Int) {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            val updated = repository.updateAppointmentStatusOnServerAndLocal(
                token = token,
                id = id,
                status = "APPROVED"
            )
            if (updated != null) {
                val patientUser = repository.getUserByPhone(updated.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"
                
                com.example.utils.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(), updated.id, updated.doctorName, "${updated.date} в ${updated.time}", "APPROVED", patientName
                )

                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog("⚡ TELEGRAM BOT ALERT: Запись пациента успешно ПОДТВЕРЖДЕНА ✔.", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun cancelAppointment(id: Int, cancelReason: String = "") {
        viewModelScope.launch {
            val token = sessionManager.getToken()
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
                    repository.addSyncLog("❌ TELEGRAM BOT ALERT: Приём к врачу ОТМЕНЕН. $reasonStr", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun addStaffNotesToAppointment(id: Int, notes: String) {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            if (appointment != null) {
                val updated = appointment.copy(notes = notes)
                repository.updateAppointment(updated)
                com.example.utils.FirestoreSyncManager.publishAppointment(updated)
            }
        }
    }

    fun createMedicalRecord(patientPhone: String, diagnosis: String, prescription: String, recommendations: String) {
        viewModelScope.launch {
            val activeUser = _currentUser.value
            val doctor = activeUser?.fullName ?: "Дежурный Врач"
            val token = sessionManager.getToken()

            val saved = repository.createMedicalRecordOnServerAndLocal(
                token = token,
                patientPhone = patientPhone,
                doctorName = doctor,
                diagnosis = diagnosis,
                prescription = prescription,
                recommendations = recommendations
            )

            val patientUser = repository.getUserByPhone(patientPhone)
            val patientName = patientUser?.fullName ?: "Пациент"

            com.example.utils.NotificationHelper.sendMedicalRecordNotification(
                getApplication(), saved.id, doctor, diagnosis, patientName
            )

            if (patientUser?.telegramChatId != null) {
                delay(400)
                repository.addSyncLog("📋 TELEGRAM BOT ALERT: Доктор добавил запись в медицинскую карту.", "SYSTEM_SYNC")
            }
        }
    }
    
    fun triggerCloudSynchronization() {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            repository.syncAllAppointmentsFromServer(token)
        }
    }
}

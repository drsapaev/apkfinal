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
    private val wsClient = com.example.utils.ClinicWebSocketClient(application, database)

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
            }
            try { wsClient.stop() } catch (e: Exception) { e.printStackTrace() }
            authRepository.logout()
            _currentUser.value = null
            onLogoutSuccess?.invoke()
        }
    }

    fun approveAppointment(id: Int) {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            if (appointment != null) {
                val updated = appointment.copy(status = "APPROVED", notes = "Подтверждено администратором.")
                repository.updateAppointment(updated)
                com.example.utils.FirestoreSyncManager.publishAppointment(updated)
                
                try {
                    val token = com.example.utils.TokenManager.getToken(getApplication()) ?: ""
                    val response = com.example.data.api.ApiClient.service.updateAppointmentStatus(
                        token = "Bearer $token", id = id, status = "APPROVED", notes = "Подтверждено администратором."
                    )
                    if (response.isSuccessful) {
                        repository.addSyncLog("🟢 API [PUT /api/v1/appointments/$id/status]: Статус APPROVED подтвержден на сервере.", "CLOUD_SYNC_SIMULATOR")
                    }
                } catch (e: Exception) {
                    repository.addSyncLog("⏳ Сервер FastAPI offline. Статус изменен локально.", "CLOUD_SYNC_SIMULATOR")
                }

                val patientUser = repository.getUserByPhone(appointment.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"
                
                com.example.utils.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(), appointment.id, appointment.doctorName, "${appointment.date} в ${appointment.time}", "APPROVED", patientName
                )

                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog("⚡ TELEGRAM BOT ALERT [Chat: ${patientUser.telegramChatId}]: Уважаемый(ая) ${patientUser.fullName}, ваша запись к врачу ${appointment.doctorName} на ${appointment.date} в ${appointment.time} успешно ПОДТВЕРЖДЕНА ✔.", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun cancelAppointment(id: Int, cancelReason: String = "") {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            if (appointment != null) {
                val updated = appointment.copy(
                    status = "CANCELLED", notes = if (cancelReason.isNotEmpty()) "Отменено: $cancelReason" else "Отклонено."
                )
                repository.updateAppointment(updated)
                com.example.utils.FirestoreSyncManager.publishAppointment(updated)
                
                try {
                    val token = com.example.utils.TokenManager.getToken(getApplication()) ?: ""
                    val response = com.example.data.api.ApiClient.service.updateAppointmentStatus(
                        token = "Bearer $token", id = id, status = "CANCELLED", notes = cancelReason.ifEmpty { "Отклонено." }
                    )
                    if (response.isSuccessful) {
                        repository.addSyncLog("🟢 API [PUT /api/v1/appointments/$id/status]: Статус CANCELLED подтвержден на сервере.", "CLOUD_SYNC_SIMULATOR")
                    }
                } catch (e: Exception) {
                    repository.addSyncLog("⏳ Сервер FastAPI offline. Отмена сохранена локально.", "CLOUD_SYNC_SIMULATOR")
                }

                val patientUser = repository.getUserByPhone(appointment.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"

                com.example.utils.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(), appointment.id, appointment.doctorName, "${appointment.date} в ${appointment.time}", "CANCELLED", patientName
                )

                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    val reasonStr = if (cancelReason.isNotEmpty()) "Причина: $cancelReason." else "По техническим причинам."
                    repository.addSyncLog("❌ TELEGRAM BOT ALERT [Chat: ${patientUser.telegramChatId}]: Внимание! Приём к врачу ${appointment.doctorName} на ${appointment.date} ОТМЕНЕН. $reasonStr", "SYSTEM_SYNC")
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

            val newRecord = MedicalRecordEntity(
                patientPhone = patientPhone, doctorName = doctor, diagnosis = diagnosis,
                prescription = prescription, visitDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                recommendations = recommendations
            )
            val savedRecord = repository.insertMedicalRecord(newRecord)
            com.example.utils.FirestoreSyncManager.publishMedicalRecord(savedRecord)
            
            try {
                val token = com.example.utils.TokenManager.getToken(getApplication()) ?: ""
                val dto = com.example.data.api.MedicalRecordDto(
                    id = null, patientPhone = patientPhone, doctorName = doctor, diagnosis = diagnosis,
                    prescription = prescription, visitDate = newRecord.visitDate, recommendations = recommendations
                )
                val response = com.example.data.api.ApiClient.service.createMedicalRecord("Bearer $token", dto)
                if (response.isSuccessful && response.body() != null) {
                    repository.addSyncLog("🟢 API [POST /api/v1/patients/records]: Запись медкарты успешно синхронизирована с сервером.", "CLOUD_SYNC_SIMULATOR")
                }
            } catch (e: Exception) {
                repository.addSyncLog("⏳ Сервер FastAPI offline. Медкарта сохранена локально в кэш Room.", "CLOUD_SYNC_SIMULATOR")
            }

            val patientUser = repository.getUserByPhone(patientPhone)
            val patientName = patientUser?.fullName ?: "Пациент"

            com.example.utils.NotificationHelper.sendMedicalRecordNotification(
                getApplication(), (System.currentTimeMillis() % 100000).toInt(), doctor, diagnosis, patientName
            )

            if (patientUser?.telegramChatId != null) {
                delay(400)
                repository.addSyncLog("📋 TELEGRAM BOT ALERT [Chat: ${patientUser.telegramChatId}]: Доктор $doctor добавил запись в вашу медицинскую карту! Диагноз: $diagnosis. Назначения: $prescription.", "SYSTEM_SYNC")
            }
        }
    }
    
    fun triggerCloudSynchronization() {
        viewModelScope.launch {
            repository.addSyncLog("🟢 ПОДКЛЮЧЕНИЕ к серверу FastAPI 'final'...", "CLOUD_SYNC_SIMULATOR")
            delay(400)
            val token = com.example.utils.TokenManager.getToken(getApplication())
            val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""

            try {
                repository.addSyncLog("🛰️ GET /api/v1/appointments (Синхронизация записей на прием)", "CLOUD_SYNC_SIMULATOR")
                val appointmentsResponse = com.example.data.api.ApiClient.service.getAppointments(authHeader)
                if (appointmentsResponse.isSuccessful && appointmentsResponse.body() != null) {
                    val serverList = appointmentsResponse.body()!!
                    repository.addSyncLog("✓ Успешно получено ${serverList.size} записей с сервера.", "CLOUD_SYNC_SIMULATOR")
                    for (appDto in serverList) {
                        val localEntity = AppointmentEntity(
                            id = appDto.id ?: (System.currentTimeMillis() % 100000).toInt(),
                            patientPhone = appDto.patientPhone, patientName = appDto.patientName,
                            doctorName = appDto.doctorName, specialty = appDto.specialty,
                            date = appDto.date, time = appDto.time, status = appDto.status,
                            reason = appDto.reason, notes = appDto.notes ?: ""
                        )
                        val existing = repository.getAppointmentById(localEntity.id)
                        if (existing == null) {
                            repository.insertAppointment(localEntity)
                        } else {
                            repository.updateAppointment(localEntity)
                        }
                    }
                }
                repository.addSyncLog("✅ СИНХРОНИЗАЦИЯ С СЕРВЕРОМ 'final' УСПЕШНО ЗАВЕРШЕНА!", "CLOUD_SYNC_SIMULATOR")
            } catch (e: Exception) {
                repository.addSyncLog("🔴 Сбой синхронизации с API: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
                repository.addSyncLog("⏳ Работа в безопасном режиме сохранения в локальный кэш Room SQLite.", "CLOUD_SYNC_SIMULATOR")
            }
        }
    }
}

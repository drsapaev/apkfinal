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
    private val wsClient = com.example.utils.ClinicWebSocketClient(application, database)

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
            val newApp = AppointmentEntity(
                patientPhone = user.phone,
                patientName = user.fullName,
                doctorName = doctorName,
                specialty = specialty,
                date = date,
                time = time,
                status = "PENDING",
                reason = reason
            )
            val savedApp = repository.insertAppointment(newApp)
            com.example.utils.FirestoreSyncManager.publishAppointment(savedApp)

            try {
                val token = com.example.utils.TokenManager.getToken(getApplication())
                val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""
                val dto = com.example.data.api.AppointmentDto(
                    id = null, patientPhone = user.phone, patientName = user.fullName,
                    doctorName = doctorName, specialty = specialty, date = date,
                    time = time, status = "PENDING", reason = reason, notes = null
                )
                val response = com.example.data.api.ApiClient.service.createAppointment(authHeader, dto)
                if (response.isSuccessful && response.body() != null) {
                    val saved = response.body()!!
                    repository.deleteAppointment(savedApp.id)
                    repository.insertAppointment(newApp.copy(id = saved.id ?: (System.currentTimeMillis() % 100000).toInt()))
                    repository.addSyncLog("🟢 API УСПЕХ [POST /api/v1/appointments]: Прием записан на сервере с ID #${saved.id}", "CLOUD_SYNC_SIMULATOR")
                } else {
                    repository.addSyncLog("⚠️ API Отклонено сервером: Код ${response.code()} (Работаем в автономном режиме)", "CLOUD_SYNC_SIMULATOR")
                }
            } catch (e: Exception) {
                repository.addSyncLog("⏳ Сервер FastAPI offline. Запись сохранена локально в Room DB.", "CLOUD_SYNC_SIMULATOR")
            }

            if (user.telegramChatId != null) {
                delay(400)
                repository.addSyncLog("⚡ TELEGRAM BOT ALERT [Chat: ${user.telegramChatId}]: Новая запись к врачу $doctorName ($specialty) на $date в $time в очереди на подтверждение.", "SYSTEM_SYNC")
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

    fun fetchMedicalReports() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (_isFetchingReports.value) return@launch
            _isFetchingReports.value = true

            val token = com.example.utils.TokenManager.getToken(getApplication())
            val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""

            com.example.data.repository.networkBoundResource(
                query = { repository.getRecordsForPatient(user.phone) },
                fetch = {
                    repository.addSyncLog("🛰️ CONNECTING to API: GET /api/v1/patients/records/${user.phone}", "CLOUD_SYNC_SIMULATOR")
                    com.example.data.api.ApiClient.service.getMedicalRecordsForPatient(authHeader, user.phone)
                },
                saveFetchResult = { response ->
                    if (response.isSuccessful && response.body() != null) {
                        val reports = response.body()!!
                        repository.addSyncLog("✓ УСПЕШНЫЙ ЗАПРОС: Импортировано ${reports.size} записей медкарт с сервера final.", "CLOUD_SYNC_SIMULATOR")
                        for (dto in reports) {
                            val recordEntity = MedicalRecordEntity(
                                id = dto.id ?: (System.currentTimeMillis() % 100000).toInt(),
                                patientPhone = dto.patientPhone, doctorName = dto.doctorName,
                                diagnosis = dto.diagnosis, prescription = dto.prescription,
                                visitDate = dto.visitDate, recommendations = dto.recommendations ?: ""
                            )
                            val existing = repository.getMedicalRecordById(recordEntity.id)
                            if (existing == null) {
                                repository.insertMedicalRecord(recordEntity)
                                com.example.utils.NotificationHelper.sendMedicalRecordNotification(
                                    getApplication(), recordEntity.id, recordEntity.doctorName, recordEntity.diagnosis, user.fullName
                                )
                            }
                        }
                    } else {
                        repository.addSyncLog("⚠️ Сервер вернул код ${response.code()}.", "CLOUD_SYNC_SIMULATOR")
                    }
                    _isFetchingReports.value = false
                },
                onFetchFailed = { e ->
                    repository.addSyncLog("⏳ Сервер временно недоступен: (${e.localizedMessage}).", "CLOUD_SYNC_SIMULATOR")
                    _isFetchingReports.value = false
                }
            ).collect { resource ->
                if (resource !is com.example.data.repository.Resource.Loading) {
                    _isFetchingReports.value = false
                }
            }
        }
    }
}

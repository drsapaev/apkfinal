package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.repository.ClinicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ClinicViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ClinicDatabase.getDatabase(application)
    private val repository = ClinicRepository(database)

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

    // Derived states for Patient's screen specifically
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

    // Authentication UI States
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

    private val _isFetchingReports = MutableStateFlow(false)
    val isFetchingReports: StateFlow<Boolean> = _isFetchingReports.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Setup biometric flag locally
    private val _biometricHardwareSupported = MutableStateFlow(true)
    val biometricHardwareSupported: StateFlow<Boolean> = _biometricHardwareSupported.asStateFlow()

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
        com.example.util.FirestoreSyncManager.init(application, repository)
        viewModelScope.launch {
            repository.prepopulateDatabase()
        }
    }

    // Auth Actions
    fun updatePhoneInput(value: String) {
        if (value.startsWith("+7")) {
            _phoneInput.value = value
        } else if (value.isEmpty() || value == "+") {
            _phoneInput.value = "+7 "
        }
    }

    fun updateOtpInput(value: String) {
        if (value.length <= 4 && value.all { it.isDigit() }) {
            _otpInput.value = value
        }
    }

    fun requestOtp() {
        val rawPhone = _phoneInput.value.replace("\\s".toRegex(), "")
        if (rawPhone.length < 11) {
            _authError.value = "Пожалуйста, введите корректный номер телефона (минимум 11 цифр)"
            return
        }

        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true
            delay(1200) // Beautiful network simulation
            _isOtpSent.value = true
            _isSyncing.value = false
            _otpInput.value = "1234" // Default convenience OTP
            repository.addSyncLog(
                logMessage = "SMS OTP code [1234] sent to $rawPhone (Simulating FastAPI SMS microservice)",
                direction = "SYSTEM_SYNC"
            )

            // Start countdown timer
            _timerSeconds.value = 60
            while (_timerSeconds.value > 0) {
                delay(1000)
                _timerSeconds.value -= 1
            }
        }
    }

    fun verifyOtp() {
        val rawPhone = _phoneInput.value.trim().replace("\\s".toRegex(), "")
        if (_otpInput.value != "1234" && _otpInput.value != "4321") {
            _authError.value = "Неверный код СМС! Попробуйте ввести 1234."
            return
        }

        viewModelScope.launch {
            _authError.value = null
            _isSyncing.value = true
            delay(800)

            // Look up or sign up the user
            val existingUser = repository.getUserByPhone(rawPhone)
            if (existingUser != null) {
                _currentUser.value = existingUser
                _currentRole.value = existingUser.role
                repository.addSyncLog(
                    logMessage = "Authorized existing user: ${existingUser.fullName} as ${existingUser.role}",
                    direction = "SYSTEM_SYNC"
                )
            } else {
                // Register as new PATIENT
                val newUser = UserEntity(
                    phone = rawPhone,
                    fullName = "Новый Пациент (${rawPhone.takeLast(4)})",
                    role = "PATIENT",
                    biometricEnabled = false
                )
                repository.insertUser(newUser)
                val createdUser = repository.getUserByPhone(rawPhone)
                _currentUser.value = createdUser
                _currentRole.value = "PATIENT"
                repository.addSyncLog(
                    logMessage = "Autocreated new Patient account for: $rawPhone",
                    direction = "SYSTEM_SYNC"
                )
            }
            _isSyncing.value = false
        }
    }

    fun loginWithBiometrics(phone: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _authError.value = null
            delay(1000)
            val user = repository.getUserByPhone(phone)
            if (user != null && user.biometricEnabled) {
                _currentUser.value = user
                _currentRole.value = user.role
                _isSyncing.value = false
                repository.addSyncLog(
                    logMessage = "Biometric authentication approved for ${user.fullName} (${user.role})",
                    direction = "SYSTEM_SYNC"
                )
            } else {
                _isSyncing.value = false
                _authError.value = "Биометрический вход отключен или профиль не найден!"
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

    fun logOut() {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user != null) {
                repository.addSyncLog("User ${user.fullName} signed out.", "SYSTEM_SYNC")
            }
            _currentUser.value = null
            _isOtpSent.value = false
            _otpInput.value = ""
            _authError.value = null
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

    // Appointment Booking Actions (From Patient Cabin)
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
            com.example.util.FirestoreSyncManager.publishAppointment(savedApp)
            if (user.telegramChatId != null) {
                delay(400)
                repository.addSyncLog(
                    logMessage = "⚡ TELEGRAM BOT ALERT [Chat: ${user.telegramChatId}]: Новая запись к врачу $doctorName ($specialty) на $date в $time в очереди на подтверждение персоналом клиники.",
                    direction = "SYSTEM_SYNC"
                )
            }
        }
    }

    // Appointment Management Actions (From Staff Panel)
    fun approveAppointment(id: Int) {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            if (appointment != null) {
                val updated = appointment.copy(status = "APPROVED", notes = "Подтверждено администратором.")
                repository.updateAppointment(updated)
                com.example.util.FirestoreSyncManager.publishAppointment(updated)
                
                val patientUser = repository.getUserByPhone(appointment.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"
                
                // Trigger Real-time System Push Notification
                com.example.util.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(),
                    appointment.id,
                    appointment.doctorName,
                    "${appointment.date} в ${appointment.time}",
                    "APPROVED",
                    patientName
                )

                // Simulate Telegram Bot notifying patient
                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog(
                        logMessage = "⚡ TELEGRAM BOT ALERT [Chat: ${patientUser.telegramChatId}]: Уважаемый(ая) ${patientUser.fullName}, ваша запись к врачу ${appointment.doctorName} на ${appointment.date} в ${appointment.time} успешно ПОДТВЕРЖДЕНА ✔.",
                        direction = "SYSTEM_SYNC"
                    )
                }
            }
        }
    }

    fun cancelAppointment(id: Int, cancelReason: String = "") {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            if (appointment != null) {
                val updated = appointment.copy(
                    status = "CANCELLED",
                    notes = if (cancelReason.isNotEmpty()) "Отменено: $cancelReason" else "Отклонено."
                )
                repository.updateAppointment(updated)
                com.example.util.FirestoreSyncManager.publishAppointment(updated)
                
                val patientUser = repository.getUserByPhone(appointment.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"

                // Trigger Real-time System Push Notification
                com.example.util.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(),
                    appointment.id,
                    appointment.doctorName,
                    "${appointment.date} в ${appointment.time}",
                    "CANCELLED",
                    patientName
                )

                // Simulate Telegram Bot notifying patient of cancellation
                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    val reasonStr = if (cancelReason.isNotEmpty()) "Причина: $cancelReason." else "По техническим причинам."
                    repository.addSyncLog(
                        logMessage = "❌ TELEGRAM BOT ALERT [Chat: ${patientUser.telegramChatId}]: Внимание! Приём к врачу ${appointment.doctorName} на ${appointment.date} ОТМЕНЕН. $reasonStr Пожалуйста, запишитесь на другое время.",
                        direction = "SYSTEM_SYNC"
                    )
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
                com.example.util.FirestoreSyncManager.publishAppointment(updated)
            }
        }
    }

    // Medical History additions (From Staff Panel)
    fun createMedicalRecord(patientPhone: String, diagnosis: String, prescription: String, recommendations: String) {
        viewModelScope.launch {
            val activeUser = _currentUser.value
            val doctor = activeUser?.fullName ?: "Дежурный Врач"

            val newRecord = MedicalRecordEntity(
                patientPhone = patientPhone,
                doctorName = doctor,
                diagnosis = diagnosis,
                prescription = prescription,
                visitDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                recommendations = recommendations
            )
            val savedRecord = repository.insertMedicalRecord(newRecord)
            com.example.util.FirestoreSyncManager.publishMedicalRecord(savedRecord)
            
            val patientUser = repository.getUserByPhone(patientPhone)
            val patientName = patientUser?.fullName ?: "Пациент"

            // Local System Push Notification for New Medical Report
            com.example.util.NotificationHelper.sendMedicalRecordNotification(
                getApplication(),
                (System.currentTimeMillis() % 100000).toInt(),
                doctor,
                diagnosis,
                patientName
            )

            // Simulate Telegram Bot notifying patient of new medical record entry
            if (patientUser?.telegramChatId != null) {
                delay(400)
                repository.addSyncLog(
                    logMessage = "📋 TELEGRAM BOT ALERT [Chat: ${patientUser.telegramChatId}]: Доктор $doctor добавил запись в вашу медицинскую карту! Диагноз: $diagnosis. Назначения: $prescription.",
                    direction = "SYSTEM_SYNC"
                )
            }
        }
    }

    // Simulated Cloud sync with API endpoints from `https://github.com/drsapaev/final` including Telegram syncs
    fun triggerCloudSynchronization() {
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true

            repository.addSyncLog("CONNECTING to FastAPI server database...", "CLOUD_SYNC_SIMULATOR")
            delay(500)
            repository.addSyncLog("GET /api/v1/sync_status HTTP/1.1 - 200 OK (Checking schemas)", "CLOUD_SYNC_SIMULATOR")
            delay(500)
            repository.addSyncLog("POST /api/v1/appointments/batch-sync (Local snapshots: PENDING/APPROVED states uploaded)", "CLOUD_SYNC_SIMULATOR")
            delay(600)
            repository.addSyncLog("GET /api/v1/telegram/webhook_status HTTP/1.1 - 200 OK (Checking routing for @IntellectClinicBot)", "CLOUD_SYNC_SIMULATOR")
            delay(500)
            repository.addSyncLog("POST /api/v1/telegram/sync_users_chats (Syncing active patient webhook ChatIDs)", "CLOUD_SYNC_SIMULATOR")
            delay(500)
            repository.addSyncLog("GET /api/v1/patients/records/all HTTP/1.1 - 200 OK (Synched medical histories)", "CLOUD_SYNC_SIMULATOR")
            delay(500)
            repository.addSyncLog("✓ SYNCHRONIZATION SUCCESSFUL! FastAPI backend and PostgreSQL DB at 'drsapaev/final' are fully up to date with active Telegram bot webhooks.", "CLOUD_SYNC_SIMULATOR")

            _isSyncing.value = false
        }
    }

    // Telegram Bot connection operations
    fun linkTelegramChatId(chatId: String) {
        val user = _currentUser.value ?: return
        if (chatId.isBlank()) return
        viewModelScope.launch {
            _isSyncing.value = true
            delay(800)
            val updatedUser = user.copy(telegramChatId = chatId)
            _currentUser.value = updatedUser
            repository.updateUser(updatedUser)
            _isSyncing.value = false

            repository.addSyncLog(
                logMessage = "Linked Telegram Account with Chat ID: $chatId for Patient: ${user.fullName}",
                direction = "PATIENT_TO_STAFF"
            )
            repository.addSyncLog(
                logMessage = "POST /api/v1/telegram/link HTTP/1.1 - 200 OK (Linked chat_id $chatId with phone ${user.phone})",
                direction = "CLOUD_SYNC_SIMULATOR"
            )
        }
    }

    fun unlinkTelegramChatId() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            delay(600)
            val updatedUser = user.copy(telegramChatId = null)
            _currentUser.value = updatedUser
            repository.updateUser(updatedUser)
            _isSyncing.value = false

            repository.addSyncLog(
                logMessage = "Unlinked Telegram Chat ID for user: ${user.fullName}",
                direction = "PATIENT_TO_STAFF"
            )
            repository.addSyncLog(
                logMessage = "POST /api/v1/telegram/unlink HTTP/1.1 - 200 OK (Released webhook routing)",
                direction = "CLOUD_SYNC_SIMULATOR"
            )
        }
    }

    fun sendTestTelegramNotification() {
        val user = _currentUser.value ?: return
        val chatId = user.telegramChatId ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            delay(700)
            _isSyncing.value = false

            repository.addSyncLog(
                logMessage = "💬 TELEGRAM TEST [Chat: $chatId]: Привет, ${user.fullName}! Это тестовое уведомление от вашего Telegram бота @IntellectClinicBot. Ваши медицинские данные успешно синхронизированы с FastAPI.",
                direction = "SYSTEM_SYNC"
            )
            repository.addSyncLog(
                logMessage = "POST /api/v1/telegram/send_test_notification HTTP/1.1 - 200 OK (Response: Delivery status: DELIVERED)",
                direction = "CLOUD_SYNC_SIMULATOR"
            )
        }
    }

    fun fetchMedicalReports() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (_isFetchingReports.value) return@launch
            _isFetchingReports.value = true
            repository.addSyncLog("CONNECTING to API: GET /api/v1/patients/records/all HTTP/1.1", "CLOUD_SYNC_SIMULATOR")
            delay(1500) // Beautiful simulated latency
            
            val currentRecords = patientRecords.value
            if (currentRecords.isEmpty()) {
                val newRecord = MedicalRecordEntity(
                    patientPhone = user.phone,
                    doctorName = "Dr. Rustam Sapaev",
                    diagnosis = "Хронический глубокий пульпит зуба 36",
                    prescription = "Амоксициллин 500мг х 3 раза в день (7 дней). Линкомицин мазь.",
                    visitDate = "2026-06-05",
                    recommendations = "Соблюдать гигиену полости рта. Повторный осмотр через неделю."
                )
                repository.insertMedicalRecord(newRecord)
                repository.addSyncLog("✓ FETCH SUCCESS: Imported 1 dental medical report from Cloud database for ${user.fullName}", "CLOUD_SYNC_SIMULATOR")
                
                // Trigger Real-time push notification for imported report
                com.example.util.NotificationHelper.sendMedicalRecordNotification(
                    getApplication(),
                    101,
                    newRecord.doctorName,
                    newRecord.diagnosis,
                    user.fullName
                )
            } else {
                val hasKardiolog = currentRecords.any { it.doctorName.contains("Elena") || it.doctorName.contains("Petrova") }
                if (!hasKardiolog) {
                    val cardState = MedicalRecordEntity(
                        patientPhone = user.phone,
                        doctorName = "Dr. Elena Petrova",
                        diagnosis = "Синусовая тахикардия средней степени",
                        prescription = "Бисопролол 2.5мг утром натощак. Магний B6 Форте х 2 раза в день.",
                        visitDate = "2026-06-04",
                        recommendations = "Ограничить кофеин/напитки энергетики. ЭКГ мониторинг каждые 3 месяца."
                    )
                    repository.insertMedicalRecord(cardState)
                    repository.addSyncLog("✓ FETCH SUCCESS: Imported 1 cardiology report from Cloud database for ${user.fullName}", "CLOUD_SYNC_SIMULATOR")
                    
                    // Trigger Real-time push notification for imported cardiology report
                    com.example.util.NotificationHelper.sendMedicalRecordNotification(
                        getApplication(),
                        102,
                        cardState.doctorName,
                        cardState.diagnosis,
                        user.fullName
                    )
                } else {
                    val hasSmirnov = currentRecords.any { it.doctorName.contains("Alexander") || it.doctorName.contains("Smirnov") }
                    if (!hasSmirnov) {
                        val smirnovState = MedicalRecordEntity(
                            patientPhone = user.phone,
                            doctorName = "Dr. Alexander Smirnov",
                            diagnosis = "Острый ринофарингит (ОРВИ)",
                            prescription = "Спрей назальный с морской водой. Осельтамивир 75мг х 2 раза в день. Витамин C 1000мг.",
                            visitDate = "2026-06-03",
                            recommendations = "Обильное теплое питье, постельный режим 3 дня, домашний контроль температуры."
                        )
                        repository.insertMedicalRecord(smirnovState)
                        repository.addSyncLog("✓ FETCH SUCCESS: Imported 1 general practitioner report from Cloud database for ${user.fullName}", "CLOUD_SYNC_SIMULATOR")
                        
                        // Trigger Real-time push notification for imported GP report
                        com.example.util.NotificationHelper.sendMedicalRecordNotification(
                            getApplication(),
                            103,
                            smirnovState.doctorName,
                            smirnovState.diagnosis,
                            user.fullName
                        )
                    } else {
                        repository.addSyncLog("✓ FETCH SUCCESS: All medical reports are up to date. (0 new changes)", "CLOUD_SYNC_SIMULATOR")
                    }
                }
            }
            _isFetchingReports.value = false
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
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
}

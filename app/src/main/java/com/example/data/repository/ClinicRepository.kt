package com.example.data.repository

import com.example.data.db.*
import com.example.data.api.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClinicRepository(private val database: ClinicDatabase) {
    private val userDao = database.userDao()
    private val appointmentDao = database.appointmentDao()
    private val medicalRecordDao = database.medicalRecordDao()
    private val syncLogDao = database.syncLogDao()

    // Expose flows to the ViewModel
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
    val allAppointments: Flow<List<AppointmentEntity>> = appointmentDao.getAllAppointmentsFlow()
    val allMedicalRecords: Flow<List<MedicalRecordEntity>> = medicalRecordDao.getAllRecordsFlow()
    val recentLogs: Flow<List<SyncLogEntity>> = syncLogDao.getRecentLogsFlow()

    // User Operations
    suspend fun getUserByPhone(phone: String): UserEntity? = userDao.getUserByPhone(phone)
    suspend fun insertUser(user: UserEntity): Long {
        val id = userDao.insertUser(user)
        addSyncLog("Registered/updated user: ${user.fullName} (${user.role})", "PATIENT_TO_STAFF")
        return id
    }
    suspend fun updateUser(user: UserEntity) {
        userDao.updateUser(user)
        addSyncLog("Updated profile for: ${user.fullName}", "PATIENT_TO_STAFF")
    }

    // Appointment Operations
    fun getAppointmentsForPatient(phone: String): Flow<List<AppointmentEntity>> =
        appointmentDao.getAppointmentsByPatientFlow(phone)

    suspend fun getAppointmentById(id: Int): AppointmentEntity? =
        appointmentDao.getAppointmentById(id)

    suspend fun insertAppointment(appointment: AppointmentEntity): AppointmentEntity {
        val id = appointmentDao.insertAppointment(appointment).toInt()
        val saved = appointment.copy(id = id)
        addSyncLog(
            logMessage = "Created appointment: ${appointment.patientName} -> ${appointment.doctorName} (${appointment.date} ${appointment.time})",
            direction = "PATIENT_TO_STAFF"
        )
        return saved
    }

    suspend fun updateAppointment(appointment: AppointmentEntity) {
        appointmentDao.updateAppointment(appointment)
        addSyncLog(
            logMessage = "Updated appointment ID #${appointment.id} state to: ${appointment.status}",
            direction = "STAFF_TO_PATIENT"
        )
    }

    suspend fun deleteAppointment(id: Int) {
        appointmentDao.deleteAppointmentById(id)
        addSyncLog("Deleted appointment ID #${id}", "SYSTEM_SYNC")
    }

    // Medical Records Operations
    fun getRecordsForPatient(phone: String): Flow<List<MedicalRecordEntity>> =
        medicalRecordDao.getRecordsByPatientFlow(phone)

    suspend fun getMedicalRecordById(id: Int): MedicalRecordEntity? =
        medicalRecordDao.getRecordById(id)

    suspend fun insertMedicalRecord(record: MedicalRecordEntity): MedicalRecordEntity {
        val id = medicalRecordDao.insertRecord(record).toInt()
        val saved = record.copy(id = id)
        addSyncLog(
            logMessage = "New medical record for patient phone ${record.patientPhone}: Diagnosis: ${record.diagnosis}",
            direction = "STAFF_TO_PATIENT"
        )
        return saved
    }

    suspend fun clearSensitiveDataForPatient(phone: String) {
        appointmentDao.deleteAppointmentsByPatient(phone)
        medicalRecordDao.deleteRecordsByPatient(phone)
        addSyncLog("Cleared sensitive medical data from local cache for user upon logout.", "SYSTEM_SYNC")
    }

    // Logging & Simulating Sync
    suspend fun addSyncLog(logMessage: String, direction: String) {
        syncLogDao.insertLog(
            SyncLogEntity(
                logMessage = logMessage,
                direction = direction,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearLogs() = syncLogDao.clearLogs()

    // Database Seeding
    suspend fun prepopulateDatabase() {
        val staffList = userDao.getStaffUsers()
        if (staffList.isEmpty()) {
            // Seed staff (Doctors)
            val drRustam = UserEntity(
                phone = "+77071234567",
                fullName = "Dr. Rustam Sapaev",
                role = "STAFF",
                dateOfBirth = "1984-08-12",
                biometricEnabled = true
            )
            val drElena = UserEntity(
                phone = "+77019876543",
                fullName = "Dr. Elena Petrova",
                role = "STAFF",
                dateOfBirth = "1979-04-22",
                biometricEnabled = false
            )
            val drAlexander = UserEntity(
                phone = "+77025554433",
                fullName = "Dr. Alexander Smirnov",
                role = "STAFF",
                dateOfBirth = "1988-11-30",
                biometricEnabled = false
            )

            userDao.insertUser(drRustam)
            userDao.insertUser(drElena)
            userDao.insertUser(drAlexander)

            // Seed default Patient with sample phone "+77771112233" for easy testing
            val patientIvan = UserEntity(
                phone = "+77771112233",
                fullName = "Иванов Иван Иванович",
                role = "PATIENT",
                dateOfBirth = "1994-05-15",
                biometricEnabled = false
            )
            val patientJane = UserEntity(
                phone = "+77002223344",
                fullName = "Смирнова Елена Сергеевна",
                role = "PATIENT",
                dateOfBirth = "1998-09-24",
                biometricEnabled = false
            )

            val ivanId = userDao.insertUser(patientIvan)
            val janeId = userDao.insertUser(patientJane)

            // Seed some default appointments
            appointmentDao.insertAppointment(
                AppointmentEntity(
                    patientPhone = "+77771112233",
                    patientName = "Иванов Иван Иванович",
                    doctorName = "Dr. Rustam Sapaev",
                    specialty = "Стоматолог-Хирург (Dentist-Surgeon)",
                    date = getFutureDateString(1),
                    time = "10:00",
                    status = "PENDING",
                    reason = "Острая боль, консультация по имплантации"
                )
            )

            appointmentDao.insertAppointment(
                AppointmentEntity(
                    patientPhone = "+77002223344",
                    patientName = "Смирнова Елена Сергеевна",
                    doctorName = "Dr. Elena Petrova",
                    specialty = "Кардиолог (Cardiologist)",
                    date = getFutureDateString(2),
                    time = "14:30",
                    status = "APPROVED",
                    reason = "Плановый осмотр и кардиограмма"
                )
            )

            // Seed medical record
            medicalRecordDao.insertRecord(
                MedicalRecordEntity(
                    patientPhone = "+77771112233",
                    doctorName = "Dr. Rustam Sapaev",
                    diagnosis = "Кариес глубокий, пульпит 24 зуба",
                    prescription = "Ибупрофен 400мг при болях. Рекомендовано лечение каналов.",
                    visitDate = getFutureDateString(-5),
                    recommendations = "Провести рентген снимок, чистка налета"
                )
            )

            addSyncLog("Initialization of local database: Doctor profiles seeded.", "SYSTEM_SYNC")
            addSyncLog("Demo Patient profiles: Ivanov (+77771112233) & Smirnova (+77002223344) created.", "SYSTEM_SYNC")
            addSyncLog("A dental consultation with Dr. Sapaev scheduled for tomorrow.", "SYSTEM_SYNC")
        }
    }

    private fun getFutureDateString(daysAhead: Int): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = Date(System.currentTimeMillis() + (daysAhead * 24 * 60 * 60 * 1000L))
        return dateFormat.format(date)
    }

    // API/Web Service Operations (Encapsulated)
    suspend fun createAppointmentOnServerAndLocal(
        token: String?,
        patientPhone: String,
        patientName: String,
        doctorName: String,
        specialty: String,
        date: String,
        time: String,
        reason: String
    ): AppointmentEntity {
        val newApp = AppointmentEntity(
            patientPhone = patientPhone,
            patientName = patientName,
            doctorName = doctorName,
            specialty = specialty,
            date = date,
            time = time,
            status = "PENDING",
            reason = reason
        )
        // 1. Save in local Room DB for offline fallback
        val savedApp = insertAppointment(newApp)
        com.example.utils.FirestoreSyncManager.publishAppointment(savedApp)

        // 2. Transmit to real FastAPI "final" backend
        try {
            val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""
            val dto = AppointmentDto(
                id = null, patientPhone = patientPhone, patientName = patientName,
                doctorName = doctorName, specialty = specialty, date = date,
                time = time, status = "PENDING", reason = reason, notes = null
            )
            val response = ApiClient.service.createAppointment(authHeader, dto)
            if (response.isSuccessful && response.body() != null) {
                val saved = response.body()!!
                deleteAppointment(savedApp.id)
                val finalApp = newApp.copy(id = saved.id ?: (System.currentTimeMillis() % 100000).toInt())
                insertAppointment(finalApp)
                addSyncLog("🟢 API УСПЕХ [POST /api/v1/appointments]: Прием записан на сервере с ID #${saved.id}", "CLOUD_SYNC_SIMULATOR")
                return finalApp
            } else {
                addSyncLog("⚠️ API Отклонено сервером: Код ${response.code()} (Работаем в автономном режиме)", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("⏳ Сервер FastAPI offline. Запись сохранена локально в Room DB: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
        }
        return savedApp
    }

    suspend fun updateAppointmentStatusOnServerAndLocal(
        token: String?,
        id: Int,
        status: String,
        cancelReason: String = ""
    ): AppointmentEntity? {
        val appointment = getAppointmentById(id) ?: return null
        val notesText = if (status == "CANCELLED") {
            if (cancelReason.isNotEmpty()) "Отменено: $cancelReason" else "Отклонено."
        } else {
            "Подтверждено администратором."
        }
        val updated = appointment.copy(status = status, notes = notesText)
        updateAppointment(updated)
        com.example.utils.FirestoreSyncManager.publishAppointment(updated)

        try {
            val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""
            val response = ApiClient.service.updateAppointmentStatus(
                token = authHeader, id = id, status = status, notes = cancelReason.ifEmpty { "Отклонено." }
            )
            if (response.isSuccessful) {
                addSyncLog("🟢 API [PUT /api/v1/appointments/$id/status]: Статус $status подтвержден на сервере.", "CLOUD_SYNC_SIMULATOR")
            } else {
                addSyncLog("⚠️ API Статус отклонен сервером: Код ${response.code()}", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("⏳ Сервер FastAPI offline. Изменения статуса сохранены локально: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
        }
        return updated
    }

    suspend fun createMedicalRecordOnServerAndLocal(
        token: String?,
        patientPhone: String,
        doctorName: String,
        diagnosis: String,
        prescription: String,
        recommendations: String
    ): MedicalRecordEntity {
        val newRecord = MedicalRecordEntity(
            patientPhone = patientPhone, doctorName = doctorName, diagnosis = diagnosis,
            prescription = prescription, visitDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            recommendations = recommendations
        )
        // 1. Save in local SQLite
        val savedRecord = insertMedicalRecord(newRecord)
        com.example.utils.FirestoreSyncManager.publishMedicalRecord(savedRecord)
        
        // 2. Transmit to real FastAPI "final" server
        try {
            val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""
            val dto = MedicalRecordDto(
                id = null, patientPhone = patientPhone, doctorName = doctorName, diagnosis = diagnosis,
                prescription = prescription, visitDate = newRecord.visitDate, recommendations = recommendations
            )
            val response = ApiClient.service.createMedicalRecord(authHeader, dto)
            if (response.isSuccessful && response.body() != null) {
                addSyncLog("🟢 API [POST /api/v1/patients/records]: Запись медкарты успешно синхронизирована с сервером.", "CLOUD_SYNC_SIMULATOR")
            } else {
                addSyncLog("⚠️ API Медкарта отклонена сервером: Код ${response.code()}", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("⏳ Сервер FastAPI offline. Медкарта сохранена локально в кэш Room: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
        }
        return savedRecord
    }

    suspend fun fetchMedicalRecordsFromServer(
        token: String?,
        phone: String,
        onNewRecordAction: (MedicalRecordEntity) -> Unit = {}
    ): List<MedicalRecordEntity> {
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""
        addSyncLog("🛰️ CONNECTING to API: GET /api/v1/patients/records/$phone", "CLOUD_SYNC_SIMULATOR")
        try {
            val response = ApiClient.service.getMedicalRecordsForPatient(authHeader, phone)
            if (response.isSuccessful && response.body() != null) {
                val reports = response.body()!!
                addSyncLog("✓ УСПЕШНЫЙ ЗАПРОС: Импортировано ${reports.size} записей медкарт с сервера final.", "CLOUD_SYNC_SIMULATOR")
                val results = mutableListOf<MedicalRecordEntity>()
                for (dto in reports) {
                    val recordEntity = MedicalRecordEntity(
                        id = dto.id ?: (System.currentTimeMillis() % 100000).toInt(),
                        patientPhone = dto.patientPhone, doctorName = dto.doctorName,
                        diagnosis = dto.diagnosis, prescription = dto.prescription,
                        visitDate = dto.visitDate, recommendations = dto.recommendations ?: ""
                    )
                    val existing = getMedicalRecordById(recordEntity.id)
                    if (existing == null) {
                        insertMedicalRecord(recordEntity)
                        onNewRecordAction(recordEntity)
                    }
                    results.add(recordEntity)
                }
                return results
            } else {
                addSyncLog("⚠️ Сервер вернул код ${response.code()}.", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("⏳ Сервер временно недоступен: (${e.localizedMessage}).", "CLOUD_SYNC_SIMULATOR")
        }
        return emptyList()
    }

    suspend fun syncAllAppointmentsFromServer(token: String?): Boolean {
        addSyncLog("🟢 ПОДКЛЮЧЕНИЕ к серверу FastAPI 'final'...", "CLOUD_SYNC_SIMULATOR")
        kotlinx.coroutines.delay(400)
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""

        try {
            // Check Profile
            addSyncLog("🛰️ GET /api/v1/users/me (Проверка аутентификации сессии)", "CLOUD_SYNC_SIMULATOR")
            val userResponse = ApiClient.service.getProfile(authHeader)
            if (userResponse.isSuccessful && userResponse.body() != null) {
                addSyncLog("✓ Сессия подтверждена для: ${userResponse.body()!!.fullName}", "CLOUD_SYNC_SIMULATOR")
            }

            // Sync Active Queue Status
            addSyncLog("🛰️ GET /api/v1/queue (Запрос текущей живой очереди клиники)", "CLOUD_SYNC_SIMULATOR")
            val queueResponse = ApiClient.service.getQueue(authHeader)
            if (queueResponse.isSuccessful && queueResponse.body() != null) {
                val queueList = queueResponse.body()!!
                addSyncLog("✓ Активная очередь: ${queueList.size} пациент(ов) в кабинетах ожидания.", "CLOUD_SYNC_SIMULATOR")
            }

            // Sync Appointments
            addSyncLog("🛰️ GET /api/v1/appointments (Синхронизация записей на прием)", "CLOUD_SYNC_SIMULATOR")
            val appointmentsResponse = ApiClient.service.getAppointments(authHeader)
            if (appointmentsResponse.isSuccessful && appointmentsResponse.body() != null) {
                val serverList = appointmentsResponse.body()!!
                addSyncLog("✓ Успешно получено ${serverList.size} записей с сервера.", "CLOUD_SYNC_SIMULATOR")
                for (appDto in serverList) {
                    val localEntity = AppointmentEntity(
                        id = appDto.id ?: (System.currentTimeMillis() % 100000).toInt(),
                        patientPhone = appDto.patientPhone, patientName = appDto.patientName,
                        doctorName = appDto.doctorName, specialty = appDto.specialty,
                        date = appDto.date, time = appDto.time, status = appDto.status,
                        reason = appDto.reason, notes = appDto.notes ?: ""
                    )
                    val existing = getAppointmentById(localEntity.id)
                    if (existing == null) {
                        insertAppointment(localEntity)
                    } else {
                        updateAppointment(localEntity)
                    }
                }
                addSyncLog("✅ СИНХРОНИЗАЦИЯ С СЕРВЕРОМ 'final' УСПЕШНО ЗАВЕРШЕНА!", "CLOUD_SYNC_SIMULATOR")
                return true
            } else {
                addSyncLog("⚠️ Сервер вернул код ${appointmentsResponse.code()}.", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("🔴 Сбой синхронизации с API: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
            addSyncLog("⏳ Работа в безопасном режиме сохранения в локальный кэш Room SQLite.", "CLOUD_SYNC_SIMULATOR")
        }
        return false
    }
}

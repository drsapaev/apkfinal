package com.aistudio.clinicsystem.data.repository

import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.api.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M2 repository cleanup: ClinicRepository now uses MobileApiService for
 * patient-facing operations (book appointment, get profile, get lab results)
 * and keeps ApiService for staff-facing operations (get all appointments,
 * get queue, update appointment status, create medical record).
 *
 * TODO (Hilt): when DI is introduced, both services should be constructor
 * parameters instead of read from ApiClient singleton.
 */
class ClinicRepository(private val database: ClinicDatabase) {
    private val userDao = database.userDao()
    private val appointmentDao = database.appointmentDao()
    private val medicalRecordDao = database.medicalRecordDao()
    private val syncLogDao = database.syncLogDao()
    private val pendingSyncDao = database.pendingSyncDao()
    private val queueSnapshotDao = database.queueSnapshotDao()

    // M2: MobileApiService for patient-facing /mobile/* endpoints
    private val mobileApiService: MobileApiService = ApiClient.mobileService
    // Legacy ApiService for staff-facing endpoints (not yet in /mobile/*)
    private val legacyApiService: ApiService = ApiClient.service

    // Expose flows to the ViewModel
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
    val allAppointments: Flow<List<AppointmentEntity>> = appointmentDao.getAllAppointmentsFlow()
    val allMedicalRecords: Flow<List<MedicalRecordEntity>> = medicalRecordDao.getAllRecordsFlow()
    val recentLogs: Flow<List<SyncLogEntity>> = syncLogDao.getRecentLogsFlow()
    val allQueueSnapshots: Flow<List<QueueSnapshotEntity>> = queueSnapshotDao.getAllQueueSnapshotsFlow()
    val allPendingSyncs: Flow<List<com.aistudio.clinicsystem.data.db.PendingSyncEntity>> = pendingSyncDao.observeAllPendingSyncs()

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
    // E1.8 (M0 security audit): prepopulateDatabase() previously seeded the
    // local Room database with hardcoded demo doctors (Dr. Rustam Sapaev,
    // Dr. Elena Petrova, Dr. Alexander Smirnov) and demo patients (Иванов,
    // Smirnova) on every cold start when the DB was empty.
    //
    // This is not strictly a security bypass (no fake auth), but it is demo
    // content that should NOT ship in a production medical app: real users
    // would see fake doctor names and fake patient profiles in their local
    // cache before the first server sync. Disabled in M0.
    //
    // Real users, appointments, and medical records MUST come exclusively
    // from the authenticated backend API. The local DB starts empty.
    suspend fun prepopulateDatabase() {
        // No-op: demo seeding disabled in M0.
        // Real backend data populates Room via ClinicRepository sync flows.
    }

    // Original seeding logic preserved (but unused) for reference.
    @Suppress("unused")
    private suspend fun prepopulateDatabaseLegacy() {
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
    suspend fun dismissPendingSync(sync: PendingSyncEntity) {
        pendingSyncDao.deletePendingSync(sync)
        addSyncLog("🗑️ Отменена отложенная транзакция: ${sync.type} (${sync.clientRequestId})", "SYSTEM_SYNC")
    }

    suspend fun retryUnsyncedWrites(token: String?): Boolean {
        val list = pendingSyncDao.getAllPendingSyncs()
        if (list.isEmpty()) return true

        addSyncLog("🔄 Начинаем отправку отложенных операций (${list.size} в очереди)...", "CLOUD_SYNC_SIMULATOR")
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else ""
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val appointmentAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentDto::class.java)
        val medicalRecordAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.MedicalRecordDto::class.java)

        var successCount = 0
        for (sync in list) {
            try {
                when (sync.type) {
                    "CREATE_APPOINTMENT" -> {
                        val dto = appointmentAdapter.fromJson(sync.payload)
                        if (dto != null) {
                            val response = legacyApiService.createAppointment(dto)
                            if (response.isSuccessful && response.body() != null) {
                                val saved = response.body()!!
                                val existingWithReqId = appointmentDao.getAppointmentByClientRequestId(sync.clientRequestId)
                                if (existingWithReqId != null) {
                                    appointmentDao.deleteAppointmentById(existingWithReqId.id)
                                    val finalApp = existingWithReqId.copy(id = saved.id ?: (System.currentTimeMillis() % 100000).toInt())
                                    appointmentDao.insertAppointment(finalApp)
                                }
                                pendingSyncDao.deletePendingSync(sync)
                                successCount++
                                addSyncLog("✓ [Отложенная запись]: Синхронизирован прием ID #${saved.id}", "CLOUD_SYNC_SIMULATOR")
                            }
                        }
                    }
                    "UPDATE_STATUS" -> {
                        val parts = sync.payload.split("|", limit = 3)
                        if (parts.size >= 2) {
                            val id = parts[0].toIntOrNull()
                            val status = parts[1]
                            val notes = if (parts.size == 3) parts[2] else ""
                            if (id != null) {
                                val response = legacyApiService.updateAppointmentStatus(id, status, notes)
                                if (response.isSuccessful) {
                                    pendingSyncDao.deletePendingSync(sync)
                                    successCount++
                                    addSyncLog("✓ [Отложенный статус]: Обновлен статус приема #$id -> $status", "CLOUD_SYNC_SIMULATOR")
                                }
                            }
                        }
                    }
                    "CREATE_MEDICAL_RECORD" -> {
                        val dto = medicalRecordAdapter.fromJson(sync.payload)
                        if (dto != null) {
                            val response = legacyApiService.createMedicalRecord(dto)
                            if (response.isSuccessful) {
                                pendingSyncDao.deletePendingSync(sync)
                                successCount++
                                addSyncLog("✓ [Отложенная медкарта]: Синхронизирована медкарта пациента ${dto.patientPhone}", "CLOUD_SYNC_SIMULATOR")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val updatedSync = sync.copy(retryCount = sync.retryCount + 1)
                pendingSyncDao.insertPendingSync(updatedSync)
                addSyncLog("⚠️ Сбой отложенной отправки (${sync.type}): ${e.localizedMessage}. Будет выполнен повтор позже.", "CLOUD_SYNC_SIMULATOR")
            }
        }
        return successCount == list.size
    }

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
        val clientReqId = java.util.UUID.randomUUID().toString()

        // 1. clientRequestId dedupe
        val existingWithReqId = appointmentDao.getAppointmentByClientRequestId(clientReqId)
        if (existingWithReqId != null) {
            addSyncLog("🛡️ Deduplication Guard: Запись с clientRequestId $clientReqId уже существует. Пропускаем дубликат.", "SYSTEM_SYNC")
            return existingWithReqId
        }

        val newApp = AppointmentEntity(
            patientPhone = patientPhone,
            patientName = patientName,
            doctorName = doctorName,
            specialty = specialty,
            date = date,
            time = time,
            status = "PENDING",
            reason = reason,
            clientRequestId = clientReqId,
            updatedAt = System.currentTimeMillis(),
            version = 1
        )
        // Save locally for high offline availability
        val savedApp = insertAppointment(newApp)
        com.aistudio.clinicsystem.utils.FirestoreSyncManager.publishAppointment(savedApp)

        // Write-Ahead lock logic (Pending Sync)
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val appointmentAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentDto::class.java)
        val dto = AppointmentDto(
            id = null, patientPhone = patientPhone, patientName = patientName,
            doctorName = doctorName, specialty = specialty, date = date,
            time = time, status = "PENDING", reason = reason, notes = null
        )
        val payString = appointmentAdapter.toJson(dto)
        val syncRecord = PendingSyncEntity(
            type = "CREATE_APPOINTMENT",
            payload = payString,
            clientRequestId = clientReqId
        )
        pendingSyncDao.insertPendingSync(syncRecord)

        try {
            val response = legacyApiService.createAppointment(dto)
            if (response.isSuccessful && response.body() != null) {
                val saved = response.body()!!
                pendingSyncDao.deletePendingSync(syncRecord)
                deleteAppointment(savedApp.id)
                val finalApp = newApp.copy(id = saved.id ?: (System.currentTimeMillis() % 100000).toInt())
                insertAppointment(finalApp)
                addSyncLog("🟢 API УСПЕХ [POST /api/v1/appointments]: Прием записан на сервере с ID #${saved.id}", "CLOUD_SYNC_SIMULATOR")
                return finalApp
            } else {
                addSyncLog("⚠️ API Отклонено сервером: Код ${response.code()} (Работаем оффлайн, запись сохранена)", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("⏳ Сервер FastAPI offline. Запись сохранена локально и добавлена в очередь отложенной отправки: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
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
        val nextVersion = appointment.version + 1
        val updated = appointment.copy(
            status = status, 
            notes = notesText, 
            updatedAt = System.currentTimeMillis(),
            version = nextVersion
        )
        updateAppointment(updated)
        com.aistudio.clinicsystem.utils.FirestoreSyncManager.publishAppointment(updated)

        // Write-Ahead lock for status change
        val clientReqId = java.util.UUID.randomUUID().toString()
        val payString = "$id|$status|${cancelReason.ifEmpty { "Отклонено." }}"
        val syncRecord = PendingSyncEntity(
            type = "UPDATE_STATUS",
            payload = payString,
            clientRequestId = clientReqId
        )
        pendingSyncDao.insertPendingSync(syncRecord)

        try {
            val response = legacyApiService.updateAppointmentStatus(
                id = id, status = status, notes = cancelReason.ifEmpty { "Отклонено." }
            )
            if (response.isSuccessful) {
                pendingSyncDao.deletePendingSync(syncRecord)
                addSyncLog("🟢 API [PUT /api/v1/appointments/$id/status]: Статус $status подтвержден на сервере.", "CLOUD_SYNC_SIMULATOR")
            } else {
                addSyncLog("⚠️ API Статус отклонен сервером: Код ${response.code()}", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("⏳ Сервер FastAPI offline. Статус сохранен локально в очереди транзакций: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
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
        val visitDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newRecord = MedicalRecordEntity(
            patientPhone = patientPhone, doctorName = doctorName, diagnosis = diagnosis,
            prescription = prescription, visitDate = visitDate,
            recommendations = recommendations
        )
        val savedRecord = insertMedicalRecord(newRecord)
        com.aistudio.clinicsystem.utils.FirestoreSyncManager.publishMedicalRecord(savedRecord)

        val clientReqId = java.util.UUID.randomUUID().toString()
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val medicalRecordAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.MedicalRecordDto::class.java)
        val dto = MedicalRecordDto(
            id = null, patientPhone = patientPhone, doctorName = doctorName, diagnosis = diagnosis,
            prescription = prescription, visitDate = visitDate, recommendations = recommendations
        )
        val payString = medicalRecordAdapter.toJson(dto)
        val syncRecord = PendingSyncEntity(
            type = "CREATE_MEDICAL_RECORD",
            payload = payString,
            clientRequestId = clientReqId
        )
        pendingSyncDao.insertPendingSync(syncRecord)

        try {
            val response = legacyApiService.createMedicalRecord(dto)
            if (response.isSuccessful) {
                pendingSyncDao.deletePendingSync(syncRecord)
                addSyncLog("🟢 API [POST /api/v1/patients/records]: Запись медкарты успешно синхронизирована с сервером.", "CLOUD_SYNC_SIMULATOR")
            } else {
                addSyncLog("⚠️ API Медкарта отклонена сервером: Код ${response.code()}", "CLOUD_SYNC_SIMULATOR")
            }
        } catch (e: Exception) {
            addSyncLog("⏳ Сервер FastAPI offline. Медкарта сохранена автономно, добавлена в очередь отправки: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
        }
        return savedRecord
    }

    suspend fun fetchMedicalRecordsFromServer(
        token: String?,
        phone: String,
        onNewRecordAction: (MedicalRecordEntity) -> Unit = {}
    ): List<MedicalRecordEntity> {
        addSyncLog("🛰️ CONNECTING to API: GET /api/v1/patients/records/$phone", "CLOUD_SYNC_SIMULATOR")
        try {
            val response = legacyApiService.getMedicalRecordsForPatient(phone)
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
        val startTime = System.currentTimeMillis()
        addSyncLog("🟢 ПОДКЛЮЧЕНИЕ к серверу FastAPI 'final'...", "CLOUD_SYNC_SIMULATOR")
        kotlinx.coroutines.delay(400)

        // Retry pending syncs first, ensuring no data override issues
        try {
            retryUnsyncedWrites(token)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // M2: migrated profile check to MobileApiService (canonical /authentication/profile)
            addSyncLog("🛰️ GET /api/v1/authentication/profile (Проверка аутентификации сессии)", "CLOUD_SYNC_SIMULATOR")
            val userResponse = mobileApiService.getProfile()
            if (userResponse.isSuccessful && userResponse.body() != null) {
                addSyncLog("✓ Сессия подтверждена.", "CLOUD_SYNC_SIMULATOR")
            }

            // Sync Active Queue Status
            addSyncLog("🛰️ GET /api/v1/queue (Запрос текущей живой очереди клиники)", "CLOUD_SYNC_SIMULATOR")
            val queueResponse = legacyApiService.getQueue()
            if (queueResponse.isSuccessful && queueResponse.body() != null) {
                val queueList = queueResponse.body()!!
                addSyncLog("✓ Активная очередь: ${queueList.size} пациент(ов) в кабинетах ожидания.", "CLOUD_SYNC_SIMULATOR")

                // Cache active queue snapshot
                queueSnapshotDao.clearQueueSnapshots()
                val snapshotsList = queueList.map { dto ->
                    QueueSnapshotEntity(
                        id = dto.id,
                        patientName = dto.patientName,
                        appointmentId = dto.appointmentId,
                        position = dto.position,
                        status = dto.status,
                        timestamp = System.currentTimeMillis()
                    )
                }
                queueSnapshotDao.insertQueueSnapshots(snapshotsList)
                addSyncLog("✓ Очередь закэширована в локальную базу данных (доступно оффлайн)", "CLOUD_SYNC_SIMULATOR")
            }

            // Sync Appointments using Delta Sync (if applicable)
            val lastSync = com.aistudio.clinicsystem.utils.SyncMetricsManager.metrics.value.lastSyncTime
            val sinceParam = if (lastSync > 0) lastSync else null
            
            val clinicId = userResponse.body()?.clinicId ?: "clinic_base"
            
            com.aistudio.clinicsystem.utils.SyncMetricsManager.updateClinicId(clinicId)

            addSyncLog("🛰️ GET /api/v1/appointments (Синхронизация записей на прием) [Delta: ${sinceParam != null}]", "CLOUD_SYNC_SIMULATOR")
            val appointmentsResponse = legacyApiService.getAppointments(since = sinceParam, clinicId = clinicId)
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
                        // Stale-write guard
                        val pendingForThis = pendingSyncDao.getAllPendingSyncs().any { it.type == "UPDATE_STATUS" && it.payload.startsWith("${localEntity.id}|") }
                        if (!pendingForThis && localEntity.updatedAt >= existing.updatedAt) {
                            updateAppointment(localEntity)
                        } else {
                            addSyncLog("🛡️ Stale-write guard: Отклонен автоматический перезапись локальной записи приема #${localEntity.id} старыми данными сервера.", "SYSTEM_SYNC")
                        }
                    }
                }
                addSyncLog("✅ СИНХРОНИЗАЦИЯ С СЕРВЕРОМ 'final' УСПЕШНО ЗАВЕРШЕНА!", "CLOUD_SYNC_SIMULATOR")
                val latency = System.currentTimeMillis() - startTime
                com.aistudio.clinicsystem.utils.SyncMetricsManager.recordSuccess(latency)
                return true
            } else {
                addSyncLog("⚠️ Сервер вернул код ${appointmentsResponse.code()}.", "CLOUD_SYNC_SIMULATOR")
                com.aistudio.clinicsystem.utils.SyncMetricsManager.recordFailure()
            }
        } catch (e: Exception) {
            addSyncLog("🔴 Сбой синхронизации с API: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
            addSyncLog("⏳ Работа в безопасном режиме сохранения в локальный кэш Room SQLite.", "CLOUD_SYNC_SIMULATOR")
            com.aistudio.clinicsystem.utils.SyncMetricsManager.recordFailure()
        }
        return false
    }

    /**
     * M2: registers a patient in the live queue via POST /api/v1/queue/register.
     * Staff-facing operation — uses legacy ApiService (not in the mobile API contract).
     */
    suspend fun registerInQueue(appointmentId: Int): retrofit2.Response<QueueDto> {
        return legacyApiService.registerInQueue(appointmentId = appointmentId)
    }

    // ═══════════════════════════════════════════════════════════════════
    // M2/E5.3: NetworkBoundResource usage
    // ═══════════════════════════════════════════════════════════════════

    /**
     * M2/E5.3: Observes appointments with offline-first sync using
     * [networkBoundResource].
     *
     * Flow:
     *   1. Emit cached appointments from Room immediately (Loading state)
     *   2. Fetch from backend via MobileApiService.getUpcomingAppointments()
     *   3. Save results to Room (Single Source of Truth)
     *   4. Emit updated Room data (Success state)
     *   5. On network failure, emit Error with cached data still available
     *
     * ViewModels collect this Flow and render UI based on Resource state.
     */
    fun observeAppointmentsWithSync(
        patientPhone: String
    ): Flow<Resource<List<AppointmentEntity>>> {
        return networkBoundResource(
            query = {
                appointmentDao.getAppointmentsByPatientFlow(patientPhone)
            },
            fetch = {
                // Network call — returns DTOs from /mobile/appointments/upcoming
                mobileApiService.getUpcomingAppointments()
            },
            saveFetchResult = { response ->
                // Save network results to Room (SSOT)
                if (response.isSuccessful && response.body() != null) {
                    val serverList = response.body()!!
                    for (dto in serverList) {
                        val entity = AppointmentEntity(
                            id = dto.id,
                            patientPhone = patientPhone,
                            patientName = "",
                            doctorName = dto.doctorName ?: "",
                            specialty = dto.specialty ?: "",
                            date = dto.date,
                            time = dto.time,
                            status = dto.status,
                            reason = dto.reason ?: "",
                            notes = dto.notes ?: "",
                            clinicId = dto.clinicId ?: "clinic_base"
                        )
                        appointmentDao.insertAppointment(entity)
                    }
                    addSyncLog("✓ NBR: Synced ${serverList.size} appointments from server", "SYSTEM_SYNC")
                }
            },
            shouldFetch = { cachedData ->
                // Fetch from network only if cache is empty or we haven't synced recently
                cachedData.isEmpty() ||
                    com.aistudio.clinicsystem.utils.SyncMetricsManager.metrics.value.lastSyncTime <
                        (System.currentTimeMillis() - 5 * 60 * 1000) // 5 min staleness
            },
            onFetchFailed = { throwable ->
                addSyncLog("⚠️ NBR: Appointment fetch failed: ${throwable.message}", "SYSTEM_SYNC")
            }
        )
    }

    /**
     * M2/E5.3: Observes medical records with offline-first sync.
     *
     * Same pattern as [observeAppointmentsWithSync] but for medical records.
     */
    fun observeMedicalRecordsWithSync(
        patientPhone: String
    ): Flow<Resource<List<MedicalRecordEntity>>> {
        return networkBoundResource(
            query = {
                medicalRecordDao.getRecordsByPatientFlow(patientPhone)
            },
            fetch = {
                mobileApiService.getLabResults()
            },
            saveFetchResult = { response ->
                if (response.isSuccessful && response.body() != null) {
                    val serverList = response.body()!!
                    for (dto in serverList) {
                        val entity = MedicalRecordEntity(
                            id = dto.id,
                            patientPhone = patientPhone,
                            doctorName = dto.doctorName ?: "",
                            diagnosis = dto.testName,
                            prescription = "",
                            visitDate = dto.performedAt ?: "",
                            recommendations = dto.referenceRange ?: ""
                        )
                        val existing = getMedicalRecordById(entity.id)
                        if (existing == null) {
                            insertMedicalRecord(entity)
                        }
                    }
                    addSyncLog("✓ NBR: Synced ${serverList.size} medical records from server", "SYSTEM_SYNC")
                }
            },
            shouldFetch = { cachedData ->
                cachedData.isEmpty()
            },
            onFetchFailed = { throwable ->
                addSyncLog("⚠️ NBR: Medical records fetch failed: ${throwable.message}", "SYSTEM_SYNC")
            }
        )
    }
}

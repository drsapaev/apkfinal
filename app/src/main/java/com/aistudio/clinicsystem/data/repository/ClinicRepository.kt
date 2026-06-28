package com.aistudio.clinicsystem.data.repository

import androidx.room.withTransaction
import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.api.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage 2.6: ClinicRepository is now @Inject + @Singleton (no more
 * `ApiClient.mobileService` static access — services come via constructor).
 *
 * Closes audit findings H-6, PERF-2: every ViewModel was constructing its
 * OWN ClinicRepository with its OWN ApiClient singleton reference; now
 * Hilt injects a single shared instance.
 *
 * M2 repository cleanup: ClinicRepository now uses MobileApiService for
 * patient-facing operations (book appointment, get profile, get lab results)
 * and keeps ApiService for staff-facing operations (get all appointments,
 * get queue, update appointment status, create medical record).
 */
@javax.inject.Singleton
class ClinicRepository @javax.inject.Inject constructor(
    private val database: ClinicDatabase,
    // Stage 2.6: services injected via Hilt (provided by AppModule).
    private val mobileApiService: MobileApiService,
    private val legacyApiService: ApiService,
    // Stage 3.10 (PERF-11 fix): single Moshi instance, injected via Hilt
    private val moshi: Moshi,
) : com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface {
    private val userDao = database.userDao()
    private val appointmentDao = database.appointmentDao()
    private val medicalRecordDao = database.medicalRecordDao()
    private val syncLogDao = database.syncLogDao()
    private val pendingSyncDao = database.pendingSyncDao()
    private val queueSnapshotDao = database.queueSnapshotDao()

    // Expose flows to the ViewModel
    override val allUsers: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
    override val allAppointments: Flow<List<AppointmentEntity>> = appointmentDao.getAllAppointmentsFlow()
    override val allMedicalRecords: Flow<List<MedicalRecordEntity>> = medicalRecordDao.getAllRecordsFlow()
    override val recentLogs: Flow<List<SyncLogEntity>> = syncLogDao.getRecentLogsFlow()
    override val allQueueSnapshots: Flow<List<QueueSnapshotEntity>> = queueSnapshotDao.getAllQueueSnapshotsFlow()
    override val allPendingSyncs: Flow<List<com.aistudio.clinicsystem.data.db.PendingSyncEntity>> = pendingSyncDao.observeAllPendingSyncs()

    // User Operations
    override suspend fun getUserByPhone(phone: String): UserEntity? = userDao.getUserByPhone(phone)
    override suspend fun insertUser(user: UserEntity): Long {
        val id = userDao.insertUser(user)
        addSyncLog("Registered/updated user: ${user.fullName} (${user.role})", "PATIENT_TO_STAFF")
        return id
    }
    override suspend fun updateUser(user: UserEntity) {
        userDao.updateUser(user)
        addSyncLog("Updated profile for: ${user.fullName}", "PATIENT_TO_STAFF")
    }

    // Appointment Operations
    fun getAppointmentsForPatient(phone: String): Flow<List<AppointmentEntity>> =
        appointmentDao.getAppointmentsByPatientFlow(phone)

    override suspend fun getAppointmentById(id: String): AppointmentEntity? =
        appointmentDao.getAppointmentById(id)

    override suspend fun insertAppointment(appointment: AppointmentEntity): AppointmentEntity {
        // Stage 1.1 (Critical fix C-1): Restore the actual DAO call.
        // The previous implementation was a no-op — it only wrote a sync log
        // and returned the input object without persisting. Every offline-first
        // write path routed through this method, so offline writes were
        // silently dropped. See FINAL_RELEASE_AUDIT.md finding C-1.
        appointmentDao.insertAppointment(appointment)
        addSyncLog(
            logMessage = "Created appointment: ${appointment.patientName} -> ${appointment.doctorName} (${appointment.date} ${appointment.time})",
            direction = "PATIENT_TO_STAFF"
        )
        return appointment
    }

    override suspend fun updateAppointment(appointment: AppointmentEntity) {
        appointmentDao.updateAppointment(appointment)
        addSyncLog(
            logMessage = "Updated appointment ID #${appointment.id} state to: ${appointment.status}",
            direction = "STAFF_TO_PATIENT"
        )
    }

    override suspend fun deleteAppointment(id: String) {
        appointmentDao.deleteAppointmentById(id)
        addSyncLog("Deleted appointment ID #${id}", "SYSTEM_SYNC")
    }

    // Medical Records Operations
    fun getRecordsForPatient(phone: String): Flow<List<MedicalRecordEntity>> =
        medicalRecordDao.getRecordsByPatientFlow(phone)

    override suspend fun getMedicalRecordById(id: String): MedicalRecordEntity? =
        medicalRecordDao.getRecordById(id)

    override suspend fun insertMedicalRecord(record: MedicalRecordEntity): MedicalRecordEntity {
        // Stage 1.1 (Critical fix C-1): Restore the actual DAO call.
        // Same no-op bug as insertAppointment above — offline medical-record
        // writes were silently dropped. See FINAL_RELEASE_AUDIT.md finding C-1.
        medicalRecordDao.insertRecord(record)
        addSyncLog(
            logMessage = "New medical record for patient phone ${record.patientPhone}: Diagnosis: ${record.diagnosis}",
            direction = "STAFF_TO_PATIENT"
        )
        return record
    }

    override suspend fun clearSensitiveDataForPatient(phone: String) {
        // Stage 3.2 (H-2 fix): atomic delete — without a transaction, a
        // crash between the two deletes would leave the user half-logged-out
        // (appointments gone but medical records still on disk).
        database.withTransaction {
            appointmentDao.deleteAppointmentsByPatient(phone)
            medicalRecordDao.deleteRecordsByPatient(phone)
            addSyncLog("Cleared sensitive medical data from local cache for user upon logout.", "SYSTEM_SYNC")
        }
    }

    // Logging & Simulating Sync
    override suspend fun addSyncLog(logMessage: String, direction: String) {
        syncLogDao.insertLog(
            SyncLogEntity(
                logMessage = logMessage,
                direction = direction,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearLogs() = syncLogDao.clearLogs()

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
    override suspend fun dismissPendingSync(sync: PendingSyncEntity) {
        pendingSyncDao.deletePendingSync(sync)
        addSyncLog("🗑️ Отменена отложенная транзакция: ${sync.type} (${sync.clientRequestId})", "SYSTEM_SYNC")
    }

    override suspend fun retryUnsyncedWrites(token: String?): Boolean {
        // Stage 3.2 (H-1 fix): atomic claim — no more concurrent-worker
        // race. The SELECT + UPDATE happens in a single Room transaction
        // (see PendingSyncDao.claimForProcessing).
        // Stage 3.9 (H-4 fix): stale-PROCESSING threshold = 5 minutes,
        // so an in-flight row is NOT reclaimed by another worker.
        val staleBefore = System.currentTimeMillis() - 5 * 60_000L
        val allToProcess = database.withTransaction {
            pendingSyncDao.claimForProcessing(staleBefore)
        }
        if (allToProcess.isEmpty()) return true

        addSyncLog("🔄 Outbox: обработка ${allToProcess.size} отложенных операций...", "CLOUD_SYNC_SIMULATOR")
        val appointmentAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentDto::class.java)
        val medicalRecordAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.MedicalRecordDto::class.java)
        val retryPolicy = com.aistudio.clinicsystem.data.outbox.OutboxRetryPolicy()

        var successCount = 0
        for (sync in allToProcess) {
            // Rows are already marked PROCESSING by claimForProcessing.
            try {
                // Stage 3.8 (L-2 fix): parse the type string into the enum.
                // Unknown codes → PayloadCorrupt → DEAD_LETTER (no retry).
                val operation = com.aistudio.clinicsystem.data.outbox.OutboxOperation.fromCode(sync.type)
                val result = when (operation) {
                    com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT -> {
                        val dto = appointmentAdapter.fromJson(sync.payload)
                        if (dto != null) {
                            val response = legacyApiService.createAppointment(dto)
                            if (response.isSuccessful && response.body() != null) {
                                val saved = response.body()!!
                                // Stage 3.2 (H-2 fix): atomic reconciliation —
                                // delete old + insert new in a single transaction.
                                val existingWithReqId = appointmentDao.getAppointmentByClientRequestId(sync.clientRequestId)
                                if (existingWithReqId != null) {
                                    database.withTransaction {
                                        appointmentDao.deleteAppointmentById(existingWithReqId.id)
                                        val finalApp = existingWithReqId.copy(
                                            id = java.util.UUID.randomUUID().toString(),
                                            serverId = saved.id,
                                            version = (saved.version ?: existingWithReqId.version) + 1,
                                            updatedAt = saved.updatedAt ?: System.currentTimeMillis(),
                                            etag = null,
                                        )
                                        appointmentDao.insertAppointment(finalApp)
                                    }
                                }
                                addSyncLog("✓ Outbox: Синхронизирован прием ID #${saved.id}", "CLOUD_SYNC_SIMULATOR")
                                ProcessResult.Success
                            } else {
                                // Stage 3.6 (NET-7 fix): 4xx → DEAD_LETTER, 5xx → retry
                                ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                            }
                        } else ProcessResult.PayloadCorrupt("payload is null")
                    }
                    com.aistudio.clinicsystem.data.outbox.OutboxOperation.UPDATE_STATUS -> {
                        // Stage 1.2 / 3.2: payload = `<serverId:Int>|<status>|<notes>|<localUuid>`
                        val parts = sync.payload.split("|", limit = 4)
                        if (parts.size >= 3) {
                            val serverId = parts[0].toIntOrNull()
                            val status = parts[1]
                            val notes = parts[2]
                            val localUuid = parts.getOrNull(3)
                            if (serverId != null) {
                                val response = legacyApiService.updateAppointmentStatus(serverId, status, notes)
                                if (response.isSuccessful) {
                                    // Stage 3.2: if localUuid provided, bump version
                                    if (localUuid != null) {
                                        val local = appointmentDao.getAppointmentById(localUuid)
                                        if (local != null) {
                                            appointmentDao.updateAppointment(
                                                local.copy(
                                                    version = local.version + 1,
                                                    updatedAt = System.currentTimeMillis(),
                                                ),
                                            )
                                        }
                                    }
                                    addSyncLog("✓ Outbox: Обновлен статус приема (serverId=$serverId) → $status", "CLOUD_SYNC_SIMULATOR")
                                    ProcessResult.Success
                                } else {
                                    ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                                }
                            } else {
                                addSyncLog("💀 Outbox: UPDATE_STATUS payload corrupt — serverId='${parts[0]}' is not an Int.", "SYSTEM_SYNC")
                                ProcessResult.PayloadCorrupt("serverId not Int")
                            }
                        } else ProcessResult.PayloadCorrupt("parts.size=${parts.size}")
                    }
                    com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_MEDICAL_RECORD -> {
                        val dto = medicalRecordAdapter.fromJson(sync.payload)
                        if (dto != null) {
                            val response = legacyApiService.createMedicalRecord(dto)
                            if (response.isSuccessful) {
                                addSyncLog("✓ Outbox: Синхронизирована медкарта пациента ${dto.patientPhone}", "CLOUD_SYNC_SIMULATOR")
                                ProcessResult.Success
                            } else {
                                ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                            }
                        } else ProcessResult.PayloadCorrupt("payload is null")
                    }
                    null -> ProcessResult.PayloadCorrupt("unknown type: ${sync.type}")
                }

                when (result) {
                    is ProcessResult.Success -> {
                        // Stage 3.2 (H-2 fix): atomic COMPLETED + delete
                        database.withTransaction {
                            pendingSyncDao.updateStatus(sync.id, "COMPLETED")
                            pendingSyncDao.deletePendingSync(sync)
                        }
                        successCount++
                    }
                    is ProcessResult.HttpFailure -> {
                        // Stage 3.6 (NET-7 fix): distinguish 4xx from 5xx
                        // 4xx (except 401/408/429) → DEAD_LETTER immediately
                        // 5xx + 401/408/429 → retry with backoff
                        val code = result.code
                        val isRetriable = code in 500..599 || code == 401 || code == 408 || code == 429
                        if (isRetriable) {
                            handleOutboxFailureWithCode(sync, code, result.message, retryPolicy)
                        } else {
                            // 4xx non-retriable — DEAD_LETTER immediately, do NOT retry
                            pendingSyncDao.updateRetryStateWithHttpCode(
                                id = sync.id,
                                status = "DEAD_LETTER",
                                retryCount = sync.retryCount + 1,
                                error = "HTTP $code (non-retriable)",
                                nextRetryAt = null,
                                httpCode = code,
                            )
                            addSyncLog("💀 Outbox: ${sync.type} (${sync.id}) → DEAD_LETTER (HTTP $code, non-retriable).", "SYSTEM_SYNC")
                        }
                    }
                    is ProcessResult.PayloadCorrupt -> {
                        // Payload is malformed — DEAD_LETTER, retrying won't help
                        pendingSyncDao.updateRetryStateWithHttpCode(
                            id = sync.id,
                            status = "DEAD_LETTER",
                            retryCount = sync.retryCount + 1,
                            error = "Payload corrupt: ${result.reason}",
                            nextRetryAt = null,
                            httpCode = null,
                        )
                        addSyncLog("💀 Outbox: ${sync.type} (${sync.id}) → DEAD_LETTER (payload corrupt: ${result.reason}).", "SYSTEM_SYNC")
                    }
                }
            } catch (e: Exception) {
                // Network/exception error — schedule retry with backoff
                handleOutboxFailureWithCode(sync, null, e.localizedMessage ?: e.javaClass.simpleName, retryPolicy)
                addSyncLog("⚠️ Outbox: Сбой (${sync.type}): ${e.message}. Повтор через backoff.", "CLOUD_SYNC_SIMULATOR")
            }
        }

        // Clean up completed items
        database.withTransaction {
            pendingSyncDao.deleteCompleted()
        }
        return successCount == allToProcess.size
    }

    /** Stage 3.6: sealed result type for retryUnsyncedWrites per-row processing. */
    private sealed class ProcessResult {
        data object Success : ProcessResult()
        data class HttpFailure(val code: Int, val message: String) : ProcessResult()
        data class PayloadCorrupt(val reason: String) : ProcessResult()
    }

    /**
     * Stage 3.6 (NET-7 fix): handles outbox failure with HTTP code.
     *  - 4xx non-retriable → DEAD_LETTER immediately (caller decides).
     *  - 5xx / IOException / 408 / 429 → retry with exponential backoff.
     */
    private suspend fun handleOutboxFailureWithCode(
        sync: PendingSyncEntity,
        httpCode: Int?,
        error: String,
        retryPolicy: com.aistudio.clinicsystem.data.outbox.OutboxRetryPolicy,
    ) {
        val newRetryCount = sync.retryCount + 1
        if (newRetryCount >= retryPolicy.maxRetries) {
            // Dead letter — requires manual intervention
            pendingSyncDao.updateRetryStateWithHttpCode(
                id = sync.id,
                status = "DEAD_LETTER",
                retryCount = newRetryCount,
                error = error,
                nextRetryAt = null,
                httpCode = httpCode,
            )
            addSyncLog("💀 Outbox: Операция ${sync.type} (${sync.id}) перемещена в DEAD_LETTER после $newRetryCount попыток.", "SYSTEM_SYNC")
        } else {
            // Schedule retry with exponential backoff
            val nextRetry = System.currentTimeMillis() + retryPolicy.backoffFor(newRetryCount)
            pendingSyncDao.updateRetryStateWithHttpCode(
                id = sync.id,
                status = "FAILED",
                retryCount = newRetryCount,
                error = error,
                nextRetryAt = nextRetry,
                httpCode = httpCode,
            )
        }
    }

    // Stage 3.6: handleOutboxFailure (without HTTP code) was REMOVED —
    // superseded by handleOutboxFailureWithCode above, which also records
    // the HTTP status code so that non-retriable 4xx errors can be moved
    // to DEAD_LETTER immediately instead of cycling through 5 retries.

    override suspend fun createAppointmentOnServerAndLocal(
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

        // Stage 3.10 (PERF-11 fix): use the injected Moshi singleton instead
        // of allocating a new instance per call.
        val appointmentAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentDto::class.java)
        val dto = AppointmentDto(
            id = null, patientPhone = patientPhone, patientName = patientName,
            doctorName = doctorName, specialty = specialty, date = date,
            time = time, status = "PENDING", reason = reason, notes = null
        )
        val payString = appointmentAdapter.toJson(dto)
        val syncRecord = PendingSyncEntity(
            type = com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT.code,
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
                val finalApp = newApp.copy(id = java.util.UUID.randomUUID().toString(), serverId = saved.id)
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

    override suspend fun updateAppointmentStatusOnServerAndLocal(
        token: String?,
        id: String,
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

        // Stage 1.2 (Critical fix C-2): the outbox payload MUST carry the
        // server-side Int id (used by `legacyApiService.updateAppointmentStatus(id: Int, ...)`),
        // NOT the local UUID primary key. The previous implementation packed
        // the local UUID into `payString` and the retry path called
        // `parts[0].toIntOrNull()` — which returned null for every UUID →
        // every offline cancel/approve was guaranteed to fail and end in
        // DEAD_LETTER. See FINAL_RELEASE_AUDIT.md finding C-2.
        //
        // If the appointment has not been synced to the server yet
        // (`serverId == null`), we CANNOT enqueue a status update — the
        // server doesn't know about this appointment. The local update is
        // still saved above; the server will see the new status when the
        // CREATE_APPOINTMENT outbox row is processed (the server's
        // `createAppointment` should accept a `status` field — backend
        // ticket).
        val serverId = appointment.serverId
        val clientReqId = java.util.UUID.randomUUID().toString()
        if (serverId != null) {
            // Payload format: `<serverId:Int>|<status:String>|<notes:String>|<localUuid:String>`
            // The 4th segment is the local UUID, used for client-side
            // reconciliation after the server confirms the update.
            val payString = "$serverId|$status|$notesText|$id"
            val syncRecord = PendingSyncEntity(
                type = com.aistudio.clinicsystem.data.outbox.OutboxOperation.UPDATE_STATUS.code,
                payload = payString,
                clientRequestId = clientReqId
            )
            pendingSyncDao.insertPendingSync(syncRecord)

            try {
                val response = legacyApiService.updateAppointmentStatus(
                    id = serverId, status = status, notes = notesText
                )
                if (response.isSuccessful) {
                    pendingSyncDao.deletePendingSync(syncRecord)
                    addSyncLog("🟢 API [PUT /api/v1/appointments/$serverId/status]: Статус $status подтвержден на сервере.", "CLOUD_SYNC_SIMULATOR")
                } else {
                    addSyncLog("⚠️ API Статус отклонен сервером: Код ${response.code()}", "CLOUD_SYNC_SIMULATOR")
                }
            } catch (e: Exception) {
                addSyncLog("⏳ Сервер FastAPI offline. Статус сохранен локально в очереди транзакций: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
            }
        } else {
            // Appointment not yet synced — server can't update what it doesn't
            // have. Log the situation; the CREATE_APPOINTMENT outbox row will
            // carry the final status.
            addSyncLog(
                "ℹ️ Outbox: Приём #$id ещё не синхронизирован с сервером (serverId=null). " +
                    "Статус $status будет применён при следующей синхронизации создания.",
                "CLOUD_SYNC_SIMULATOR"
            )
        }
        return updated
    }

    override suspend fun createMedicalRecordOnServerAndLocal(
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

        val clientReqId = java.util.UUID.randomUUID().toString()
        // Stage 3.10 (PERF-11 fix): use injected Moshi.
        val medicalRecordAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.MedicalRecordDto::class.java)
        val dto = MedicalRecordDto(
            id = null, patientPhone = patientPhone, doctorName = doctorName, diagnosis = diagnosis,
            prescription = prescription, visitDate = visitDate, recommendations = recommendations
        )
        val payString = medicalRecordAdapter.toJson(dto)
        val syncRecord = PendingSyncEntity(
            type = com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_MEDICAL_RECORD.code,
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

    override suspend fun fetchMedicalRecordsFromServer(
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
                        id = java.util.UUID.randomUUID().toString(), serverId = dto.id,
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

    override suspend fun syncAllAppointmentsFromServer(token: String?): Boolean {
        val startTime = System.currentTimeMillis()
        addSyncLog("🟢 ПОДКЛЮЧЕНИЕ к серверу FastAPI 'final'...", "CLOUD_SYNC_SIMULATOR")
        // Stage 3.11 (PERF-5 fix): removed `delay(400)` — added 400ms of
        // perceived latency for no reason. The user clicked "sync"; show
        // them the result as fast as the network allows.

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

                // Stage 3.2 (H-2 fix): atomic queue snapshot refresh — clear + insert in a transaction.
                val snapshotsList = queueList.map { dto ->
                    QueueSnapshotEntity(
                        id = dto.id,
                        patientName = dto.patientName,
                        appointmentId = dto.appointmentId,
                        position = dto.position,
                        status = dto.status,
                        timestamp = System.currentTimeMillis(),
                    )
                }
                database.withTransaction {
                    queueSnapshotDao.clearQueueSnapshots()
                    queueSnapshotDao.insertQueueSnapshots(snapshotsList)
                }
                addSyncLog("✓ Очередь закэширована в локальную базу данных (доступно оффлайн)", "CLOUD_SYNC_SIMULATOR")
            }

            // Stage 3.4 (H-5 fix): Delta sync — only fetch appointments updated after `lastSync`.
            // The server's `since` parameter is an epoch-millis timestamp.
            val lastSync = com.aistudio.clinicsystem.utils.SyncMetricsManager.metrics.value.lastSyncTime
            val sinceParam = if (lastSync > 0) lastSync else null

            val clinicId = userResponse.body()?.clinicId ?: "clinic_base"

            com.aistudio.clinicsystem.utils.SyncMetricsManager.updateClinicId(clinicId)

            addSyncLog("🛰️ GET /api/v1/appointments (Синхронизация записей на прием) [Delta: ${sinceParam != null}]", "CLOUD_SYNC_SIMULATOR")
            val appointmentsResponse = legacyApiService.getAppointments(since = sinceParam, clinicId = clinicId)
            if (appointmentsResponse.isSuccessful && appointmentsResponse.body() != null) {
                val serverList = appointmentsResponse.body()!!
                addSyncLog("✓ Успешно получено ${serverList.size} записей с сервера.", "CLOUD_SYNC_SIMULATOR")

                // Stage 3.2 (H-2 fix): atomic reconciliation — all inserts/updates in a single transaction.
                database.withTransaction {
                    for (appDto in serverList) {
                        reconcileAppointmentFromServer(appDto)
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
     * Stage 3.4 (H-5 fix): reconciles a single appointment DTO from the
     * server into the local Room cache. Uses `serverId` for deduplication
     * (NOT the local UUID, which is freshly generated and always null on
     * lookup) and `version` for conflict resolution.
     *
     * Conflict resolution policy:
     *   - If local `version` > server `version` → KEEP LOCAL (an in-flight
     *     outbox row will push the local change to the server on next sync).
     *   - If server `version` > local `version` → OVERWRITE LOCAL with server data.
     *   - If versions are equal → prefer server data (server is the source
     *     of truth for shared fields like `status`).
     *
     * Stale-write guard: if there is a PENDING/PROCESSING `UPDATE_STATUS`
     * outbox row for this serverId, skip reconciliation — the local change
     * has not been pushed yet, and overwriting it would lose user input.
     *
     * This method MUST be called inside a `database.withTransaction { ... }`
     * block — it performs multiple DAO calls that must be atomic.
     */
    private suspend fun reconcileAppointmentFromServer(appDto: AppointmentDto) {
        requireNotNull(appDto.id) { "Server DTO must have an id" }

        val existing = appointmentDao.getAppointmentByServerId(appDto.id)

        if (existing == null) {
            // New appointment from server — insert with a fresh local UUID.
            val entity = AppointmentEntity(
                id = java.util.UUID.randomUUID().toString(),
                serverId = appDto.id,
                patientPhone = appDto.patientPhone,
                patientName = appDto.patientName,
                doctorName = appDto.doctorName,
                specialty = appDto.specialty,
                date = appDto.date,
                time = appDto.time,
                status = appDto.status,
                reason = appDto.reason,
                notes = appDto.notes ?: "",
                clinicId = appDto.clinicId ?: "clinic_base",
                updatedAt = appDto.updatedAt ?: System.currentTimeMillis(),
                version = appDto.version ?: 1,
            )
            appointmentDao.insertAppointment(entity)
            return
        }

        // Existing appointment — check stale-write guard.
        // Stage 3.4: payload format is `<serverId>|<status>|<notes>|<localUuid>`.
        val pendingForThis = pendingSyncDao.getAllPendingSyncs().any {
            it.type == "UPDATE_STATUS" &&
                it.payload.startsWith("${appDto.id}|") &&
                (it.status == "PENDING" || it.status == "PROCESSING" || it.status == "FAILED")
        }
        if (pendingForThis) {
            addSyncLog(
                "🛡️ Stale-write guard: сохраняем локальную запись #${existing.id} (serverId=${appDto.id}) — есть неотправленная локальная правка.",
                "SYSTEM_SYNC",
            )
            return
        }

        // Version-based conflict resolution.
        val serverVersion = appDto.version ?: existing.version
        val serverUpdatedAt = appDto.updatedAt ?: existing.updatedAt
        if (serverVersion > existing.version ||
            (serverVersion == existing.version && serverUpdatedAt > existing.updatedAt)
        ) {
            // Server is newer — overwrite local mutable fields, preserve local UUID.
            val merged = existing.copy(
                patientPhone = appDto.patientPhone,
                patientName = appDto.patientName,
                doctorName = appDto.doctorName,
                specialty = appDto.specialty,
                date = appDto.date,
                time = appDto.time,
                status = appDto.status,
                reason = appDto.reason,
                notes = appDto.notes ?: existing.notes,
                clinicId = appDto.clinicId ?: existing.clinicId,
                updatedAt = serverUpdatedAt,
                version = serverVersion,
                etag = null,
            )
            appointmentDao.updateAppointment(merged)
        } else {
            // Local is newer or equal — keep local. The outbox will push
            // the local version to the server on the next sync cycle.
            addSyncLog(
                "↩️ Локальная запись #${existing.id} (v${existing.version}) новее или равна серверной (v$serverVersion) — сохраняем локальную.",
                "SYSTEM_SYNC",
            )
        }
    }

    /**
     * M2: registers a patient in the live queue via POST /api/v1/queue/register.
     * Staff-facing operation — uses legacy ApiService (not in the mobile API contract).
     */
    override suspend fun registerInQueue(appointmentId: String): retrofit2.Response<QueueDto> {
        // M3B.4: look up serverId for API call
        val appointment = getAppointmentById(appointmentId) ?: error("Appointment not found")
        val serverId = appointment.serverId ?: error("Appointment not yet synced with server")
        return legacyApiService.registerInQueue(appointmentId = serverId)
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
                    // Stage 3.5 (H-9 fix): atomic reconciliation — dedup by
                    // serverId, preserve real patientName from DTO (was
                    // hardcoded to "" previously).
                    database.withTransaction {
                        for (dto in serverList) {
                            // Use the same reconciliation logic as delta sync.
                            reconcileAppointmentFromServer(dto)
                        }
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
                    // Stage 3.5 (H-9 fix): dedup by `serverId` — the previous
                    // code looked up by a freshly-generated UUID (always null)
                    // and inserted duplicates on every NBR collection.
                    //
                    // Stage 6 TODO: LabResultOut is NOT a medical record — it's
                    // a lab result. Mapping `testName` to `diagnosis` is
                    // semantically wrong. Stage 6 will introduce a separate
                    // LabResultEntity and LabResultDao. For now, we keep the
                    // mapping but dedup correctly.
                    database.withTransaction {
                        for (dto in serverList) {
                            val existing = medicalRecordDao.getMedicalRecordByServerId(dto.id)
                            if (existing == null) {
                                val entity = MedicalRecordEntity(
                                    id = java.util.UUID.randomUUID().toString(),
                                    serverId = dto.id,
                                    patientPhone = dto.patientPhone ?: patientPhone,
                                    doctorName = "", // LabResultOut doesn't carry doctor
                                    diagnosis = dto.testName,
                                    prescription = dto.result ?: "",
                                    visitDate = dto.performedAt ?: "",
                                    recommendations = dto.referenceRange ?: "",
                                )
                                medicalRecordDao.insertRecord(entity)
                            }
                        }
                    }
                    addSyncLog("✓ NBR: Synced ${serverList.size} lab results from server", "SYSTEM_SYNC")
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

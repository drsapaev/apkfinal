package com.aistudio.clinicsystem.data.repository

import androidx.room.withTransaction
import com.aistudio.clinicsystem.data.api.*
import com.aistudio.clinicsystem.data.db.*
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
@Suppress("NestedBlockDepth", "PrintStackTrace")
class ClinicRepository
    @javax.inject.Inject
    constructor(
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
        private val labResultDao = database.labResultDao() // Stage 6: lab results

        // Expose flows to the ViewModel
        override val allUsers: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
        override val allAppointments: Flow<List<AppointmentEntity>> = appointmentDao.getAllAppointmentsFlow()
        override val allMedicalRecords: Flow<List<MedicalRecordEntity>> = medicalRecordDao.getAllRecordsFlow()
        override val recentLogs: Flow<List<SyncLogEntity>> = syncLogDao.getRecentLogsFlow()
        override val allQueueSnapshots: Flow<List<QueueSnapshotEntity>> = queueSnapshotDao.getAllQueueSnapshotsFlow()
        override val allPendingSyncs: Flow<List<com.aistudio.clinicsystem.data.db.PendingSyncEntity>> =
            pendingSyncDao
                .observeAllPendingSyncs()
        val allDoctors: Flow<List<DoctorEntity>> = database.doctorDao().getAllDoctors()

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
        fun getAppointmentsForPatient(phone: String): Flow<List<AppointmentEntity>> = appointmentDao.getAppointmentsByPatientFlow(phone)

        override suspend fun getAppointmentById(id: String): AppointmentEntity? = appointmentDao.getAppointmentById(id)

        override suspend fun insertAppointment(appointment: AppointmentEntity): AppointmentEntity {
            // Stage 1.1 (Critical fix C-1): Restore the actual DAO call.
            // The previous implementation was a no-op — it only wrote a sync log
            // and returned the input object without persisting. Every offline-first
            // write path routed through this method, so offline writes were
            // silently dropped. See FINAL_RELEASE_AUDIT.md finding C-1.
            appointmentDao.insertAppointment(appointment)
            addSyncLog(
                logMessage = "Created appointment: ${appointment.patientName} (${appointment.date} ${appointment.time})",
                direction = "PATIENT_TO_STAFF",
            )
            return appointment
        }

        override suspend fun updateAppointment(appointment: AppointmentEntity) {
            appointmentDao.updateAppointment(appointment)
            addSyncLog(
                logMessage = "Updated appointment ID #${appointment.id} state to: ${appointment.status}",
                direction = "STAFF_TO_PATIENT",
            )
        }

        override suspend fun deleteAppointment(id: String) {
            appointmentDao.deleteAppointmentById(id)
            addSyncLog("Deleted appointment ID #$id", "SYSTEM_SYNC")
        }

        // Medical Records Operations
        fun getRecordsForPatient(phone: String): Flow<List<MedicalRecordEntity>> = medicalRecordDao.getRecordsByPatientFlow(phone)

        override suspend fun getMedicalRecordById(id: String): MedicalRecordEntity? = medicalRecordDao.getRecordById(id)

        override suspend fun insertMedicalRecord(record: MedicalRecordEntity): MedicalRecordEntity {
            // Stage 1.1 (Critical fix C-1): Restore the actual DAO call.
            // Same no-op bug as insertAppointment above — offline medical-record
            // writes were silently dropped. See FINAL_RELEASE_AUDIT.md finding C-1.
            medicalRecordDao.insertRecord(record)
            addSyncLog(
                logMessage = "New medical record for patient phone ${record.patientPhone}: Diagnosis: ${record.diagnosis}",
                direction = "STAFF_TO_PATIENT",
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
        override suspend fun addSyncLog(
            logMessage: String,
            direction: String,
        ) {
            syncLogDao.insertLog(
                SyncLogEntity(
                    logMessage = logMessage,
                    direction = direction,
                    timestamp = System.currentTimeMillis(),
                ),
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

        // Stage 11 (L-19 fix): prepopulateDatabaseLegacy DELETED.
        // The method contained hardcoded demo PHI (Dr. Rustam Sapaev,
        // +77071234567, patient names, diagnoses, prescriptions) and was
        // a dead code risk — if anyone ever called it, real users would
        // see fake medical data. Also deleted getFutureDateString (only
        // used by the legacy seeder).

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
            val allToProcess =
                database.withTransaction {
                    pendingSyncDao.claimForProcessing(staleBefore)
                }
            if (allToProcess.isEmpty()) return true

            addSyncLog("🔄 Outbox: обработка ${allToProcess.size} отложенных операций...", "CLOUD_SYNC_SIMULATOR")
            val appointmentAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentDto::class.java)
            val retryPolicy =
                com.aistudio.clinicsystem.data.outbox
                    .OutboxRetryPolicy()

            var successCount = 0
            for (sync in allToProcess) {
                // Rows are already marked PROCESSING by claimForProcessing.
                try {
                    // Stage 3.8 (L-2 fix): parse the type string into the enum.
                    // Unknown codes → PayloadCorrupt → DEAD_LETTER (no retry).
                    val operation =
                        com.aistudio.clinicsystem.data.outbox.OutboxOperation
                            .fromCode(sync.type)
                    val result =
                        when (operation) {
                            com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT -> {
                                val dto = appointmentAdapter.fromJson(sync.payload)
                                if (dto != null) {
                                    // M-CONTRACT-FIX: convert the legacy outbox payload
                                    // to the current POST /api/v1/appointments request
                                    // (int patient_id + appointment_date/appointment_time).
                                    val staffCreate =
                                        buildStaffCreateRequest(
                                            patientPhone = dto.patientPhone,
                                            patientName = dto.patientName,
                                            doctorName = dto.doctorName,
                                            date = dto.date,
                                            time = dto.time,
                                            reason = dto.reason,
                                            status = dto.status,
                                        )
                                    if (staffCreate == null) {
                                        // Unresolvable patient can never succeed — 404-style.
                                        ProcessResult.HttpFailure(404, "patient not found on server")
                                    } else {
                                        val response = legacyApiService.createAppointment(staffCreate)
                                        if (response.isSuccessful && response.body() != null) {
                                            val saved = response.body()!!
                                            // Stage 3.2 (H-2 fix): atomic reconciliation —
                                            // delete old + insert new in a single transaction.
                                            val existingWithReqId = appointmentDao.getAppointmentByClientRequestId(sync.clientRequestId)
                                            if (existingWithReqId != null) {
                                                database.withTransaction {
                                                    appointmentDao.deleteAppointmentById(existingWithReqId.id)
                                                    val finalApp =
                                                        existingWithReqId.copy(
                                                            id =
                                                                java.util.UUID
                                                                    .randomUUID()
                                                                    .toString(),
                                                            serverId = saved.id,
                                                            version = existingWithReqId.version + 1,
                                                            updatedAt = System.currentTimeMillis(),
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
                                    }
                                } else {
                                    ProcessResult.PayloadCorrupt("payload is null")
                                }
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
                                        // M-CONTRACT-FIX: status changes go through the
                                        // generic PUT /api/v1/appointments/{id} — the old
                                        // PUT /appointments/{id}/status route is gone.
                                        val response =
                                            legacyApiService.updateAppointment(
                                                id = serverId,
                                                appointment =
                                                    com.aistudio.clinicsystem.data.api.StaffAppointmentUpdateRequest(
                                                        status = toServerStatus(status),
                                                        notes = notes,
                                                    ),
                                            )
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
                                            addSyncLog(
                                                "✓ Outbox: Обновлен статус приема (serverId=$serverId) → $status",
                                                "CLOUD_SYNC_SIMULATOR",
                                            )
                                            ProcessResult.Success
                                        } else {
                                            ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                                        }
                                    } else {
                                        addSyncLog(
                                            "💀 Outbox: UPDATE_STATUS payload corrupt — serverId='${parts[0]}' is not an Int.",
                                            "SYSTEM_SYNC",
                                        )
                                        ProcessResult.PayloadCorrupt("serverId not Int")
                                    }
                                } else {
                                    ProcessResult.PayloadCorrupt("parts.size=${parts.size}")
                                }
                            }
                            com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_MEDICAL_RECORD -> {
                                // M-CONTRACT-FIX: the backend no longer publishes
                                // POST /api/v1/patients/records (removed with the
                                // legacy API). Old outbox rows are dead-lettered
                                // immediately — retrying would 404 forever. New
                                // records are no longer enqueued at all (see
                                // createMedicalRecordOnServerAndLocal).
                                ProcessResult.PayloadCorrupt("medical-record endpoint removed from backend")
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
                            addSyncLog(
                                "💀 Outbox: ${sync.type} (${sync.id}) → DEAD_LETTER (payload corrupt: ${result.reason}).",
                                "SYSTEM_SYNC",
                            )
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

            data class HttpFailure(
                val code: Int,
                val message: String,
            ) : ProcessResult()

            data class PayloadCorrupt(
                val reason: String,
            ) : ProcessResult()
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
                addSyncLog(
                    "💀 Outbox: Операция ${sync.type} (${sync.id}) перемещена в DEAD_LETTER после $newRetryCount попыток.",
                    "SYSTEM_SYNC",
                )
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
            reason: String,
        ): AppointmentEntity {
            val clientReqId =
                java.util.UUID
                    .randomUUID()
                    .toString()

            // 1. clientRequestId dedupe
            val existingWithReqId = appointmentDao.getAppointmentByClientRequestId(clientReqId)
            if (existingWithReqId != null) {
                addSyncLog(
                    "🛡️ Deduplication Guard: Запись с clientRequestId $clientReqId уже существует. Пропускаем дубликат.",
                    "SYSTEM_SYNC",
                )
                return existingWithReqId
            }

            val newApp =
                AppointmentEntity(
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
                    version = 1,
                )
            // Save locally for high offline availability
            val savedApp = insertAppointment(newApp)

            // Stage 3.10 (PERF-11 fix): use the injected Moshi singleton instead
            // of allocating a new instance per call.
            val appointmentAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentDto::class.java)
            val dto =
                AppointmentDto(
                    id = null,
                    patientPhone = patientPhone,
                    patientName = patientName,
                    doctorName = doctorName,
                    specialty = specialty,
                    date = date,
                    time = time,
                    status = "PENDING",
                    reason = reason,
                    notes = null,
                )
            val payString = appointmentAdapter.toJson(dto)
            val syncRecord =
                PendingSyncEntity(
                    type = com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT.code,
                    payload = payString,
                    clientRequestId = clientReqId,
                )
            pendingSyncDao.insertPendingSync(syncRecord)

            try {
                // M-CONTRACT-FIX: the mobile contract endpoint
                // POST /api/v1/mobile/appointments/book now expects
                // {doctor_id, preferred_date, preferred_time?, complaint?,
                //  services?, notes?} (backend MobileBookAppointmentRequest) —
                // the previous {date, time, reason, clinic_id} body was
                // rejected with HTTP 422 on every booking.
                val doctorServerId = resolveDoctorServerId(doctorName)
                if (doctorServerId != null) {
                    // Patient self-booking via mobile contract
                    val bookRequest =
                        com.aistudio.clinicsystem.data.api.AppointmentBookRequest(
                            doctorId = doctorServerId,
                            preferredDate = date,
                            preferredTime = time.ifBlank { null },
                            complaint = reason.ifBlank { null },
                            services = emptyList(),
                            notes = null,
                        )
                    val mobileResponse = mobileApiService.bookAppointment(bookRequest)
                    if (mobileResponse.isSuccessful && mobileResponse.body() != null) {
                        val saved = mobileResponse.body()!!
                        pendingSyncDao.deletePendingSync(syncRecord)
                        deleteAppointment(savedApp.id)
                        // High-1 audit fix: AppointmentUpcomingOut uses
                        // appointment_date (ISO 8601) + clinic_address, with
                        // computed date/time accessors for back-compat with
                        // the AppointmentEntity schema. Date-only backend
                        // bookings serialise as midnight — keep the time the
                        // user actually picked in that case.
                        val resolvedTime =
                            if (saved.time.isBlank() || saved.time == "00:00") {
                                time
                            } else {
                                saved.time
                            }
                        val finalApp =
                            newApp.copy(
                                id =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString(),
                                serverId = saved.id,
                                date = saved.date,
                                time = resolvedTime,
                                doctorName = saved.doctorName,
                                specialty = saved.specialty,
                                clinicId = saved.clinicAddress, // clinic_address → clinicId field (semantic)
                            )
                        insertAppointment(finalApp)
                        addSyncLog(
                            "🟢 API УСПЕХ [POST /api/v1/mobile/appointments/book]: Приём записан на сервере с ID #${saved.id}",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                        return finalApp
                    } else {
                        addSyncLog(
                            "⚠️ Mobile API отклонено сервером: Код ${mobileResponse.code()} (fallback на staff-эндпоинт)",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                        // Fall through to staff attempt below
                    }
                }

                // Staff fallback (registrar/doctor booking for a patient).
                // POST /api/v1/appointments requires an int patient_id —
                // resolve it from the phone first. Legacy PUT
                // /appointments/{id}/status and the old flat create body are
                // gone from the backend (404).
                val staffCreate =
                    buildStaffCreateRequest(
                        patientPhone = patientPhone,
                        patientName = patientName,
                        doctorName = doctorName,
                        date = date,
                        time = time,
                        reason = reason,
                        status = "PENDING",
                    )
                if (staffCreate != null) {
                    val response = legacyApiService.createAppointment(staffCreate)
                    if (response.isSuccessful && response.body() != null) {
                        val saved = response.body()!!
                        pendingSyncDao.deletePendingSync(syncRecord)
                        deleteAppointment(savedApp.id)
                        val finalApp =
                            newApp.copy(
                                id =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString(),
                                serverId = saved.id,
                                status = "PENDING",
                            )
                        insertAppointment(finalApp)
                        addSyncLog(
                            "🟢 API УСПЕХ [POST /api/v1/appointments]: Прием записан на сервере с ID #${saved.id}",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                        return finalApp
                    } else {
                        addSyncLog(
                            "⚠️ API Отклонено сервером: Код ${response.code()} (Работаем оффлайн, запись сохранена)",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                    }
                }
            } catch (e: Exception) {
                addSyncLog(
                    "⏳ Сервер FastAPI offline. Запись сохранена локально и добавлена в очередь отложенной отправки: ${e.localizedMessage}",
                    "CLOUD_SYNC_SIMULATOR",
                )
            }
            return savedApp
        }

        /**
         * M-CONTRACT-FIX: maps the legacy outbox payload (AppointmentDto) to
         * the POST /api/v1/appointments request the backend serves today.
         * Returns null when the patient cannot be resolved on the server —
         * the create schema requires an int `patient_id`.
         */
        private suspend fun buildStaffCreateRequest(
            patientPhone: String,
            patientName: String,
            doctorName: String,
            date: String,
            time: String,
            reason: String,
            status: String,
        ): com.aistudio.clinicsystem.data.api.StaffAppointmentCreateRequest? {
            val patientId = resolvePatientIdByPhone(patientPhone)
            if (patientId == null) {
                addSyncLog(
                    "⚠️ POST /api/v1/appointments: пациент с телефоном $patientPhone не найден на сервере — пациент должен быть зарегистрирован в клинике.",
                    "CLOUD_SYNC_SIMULATOR",
                )
                return null
            }
            val notesParts = listOf(reason, patientName).filter { it.isNotBlank() }
            return com.aistudio.clinicsystem.data.api.StaffAppointmentCreateRequest(
                patientId = patientId,
                doctorId = resolveDoctorServerId(doctorName),
                appointmentDate = date,
                appointmentTime = time.ifBlank { null },
                notes = notesParts.joinToString("\n").ifBlank { null },
                status = toServerStatus(status),
            )
        }

        /** GET /api/v1/patients?phone=… exact-match lookup → patient id. */
        private suspend fun resolvePatientIdByPhone(phone: String): Int? {
            if (phone.isBlank()) return null
            val response = legacyApiService.findPatientsByPhone(phone = phone, limit = 1)
            if (!response.isSuccessful) return null
            return response.body()?.firstOrNull()?.id
        }

        /**
         * Client status vocabulary (PENDING/APPROVED/COMPLETED/CANCELLED) →
         * backend appointment statuses (scheduled/confirmed/completed/cancelled).
         */
        private fun toServerStatus(clientStatus: String): String =
            when (clientStatus.uppercase()) {
                "PENDING", "PLANNED" -> "scheduled"
                "APPROVED", "CONFIRMED" -> "confirmed"
                "CANCELLED" -> "cancelled"
                "COMPLETED" -> "completed"
                else -> clientStatus.lowercase()
            }

        private fun normalizeQueueStatus(status: String): String =
            when (status.lowercase()) {
                "waiting" -> "WAITING"
                "called" -> "CALLED"
                "in_progress", "in-progress" -> "IN_PROGRESS"
                "served", "completed" -> "COMPLETED"
                "cancelled", "canceled" -> "CANCELLED"
                else -> status.uppercase()
            }

        /**
         * High-2 audit fix: extracts a backend doctor serverId from the
         * doctorName field. The doctor directory (P-04) stores doctors with
         * names like "Д-р Сапаев (Стоматолог-терапевт) [#42]" — the bracketed
         * #N is the backend doctor user id. Returns null if no id is found
         * (legacy doctorName without id — staff-side booking).
         *
         * Used to decide whether to call the mobile /appointments/book
         * endpoint (requires doctor_id) or fall back to the legacy
         * /appointments endpoint (staff-side, no doctor_id required).
         */
        private fun extractDoctorServerId(doctorName: String): Int? {
            val regex = Regex("""#(\d+)""")
            val match = regex.find(doctorName) ?: return null
            return match.groupValues[1].toIntOrNull()
        }

        private suspend fun resolveDoctorServerId(doctorName: String): Int? =
            extractDoctorServerId(doctorName)
                ?: database.doctorDao().getDoctorByFullName(doctorName)?.serverId

        override suspend fun updateAppointmentStatusOnServerAndLocal(
            token: String?,
            id: String,
            status: String,
            cancelReason: String,
        ): AppointmentEntity? {
            val appointment = getAppointmentById(id) ?: return null
            val notesText =
                if (status == "CANCELLED") {
                    if (cancelReason.isNotEmpty()) "Отменено: $cancelReason" else "Отклонено."
                } else {
                    "Подтверждено администратором."
                }
            val nextVersion = appointment.version + 1
            val updated =
                appointment.copy(
                    status = status,
                    notes = notesText,
                    updatedAt = System.currentTimeMillis(),
                    version = nextVersion,
                )
            updateAppointment(updated)

            // Stage 1.2 (Critical fix C-2): the outbox payload MUST carry the
            // server-side Int id (used by `legacyApiService.updateAppointment(id: Int, ...)`),
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
            val clientReqId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            if (serverId != null) {
                // Payload format: `<serverId:Int>|<status:String>|<notes:String>|<localUuid:String>`
                // The 4th segment is the local UUID, used for client-side
                // reconciliation after the server confirms the update.
                val payString = "$serverId|$status|$notesText|$id"
                val syncRecord =
                    PendingSyncEntity(
                        type = com.aistudio.clinicsystem.data.outbox.OutboxOperation.UPDATE_STATUS.code,
                        payload = payString,
                        clientRequestId = clientReqId,
                    )
                pendingSyncDao.insertPendingSync(syncRecord)

                try {
                    // M-CONTRACT-FIX: patient-side cancellations go through
                    // POST /api/v1/mobile/appointments/cancel (JWT, patient-
                    // scoped, 2-hour window). Staff-side status changes
                    // (APPROVED, COMPLETED, ...) go through the generic
                    // PUT /api/v1/appointments/{id} — the dedicated
                    // PUT /appointments/{id}/status route was removed from
                    // the backend together with the legacy API.
                    val isPatientCancel = status == "CANCELLED"
                    var mobileAttempted = false
                    if (isPatientCancel) {
                        mobileAttempted = true
                        val cancelRequest =
                            com.aistudio.clinicsystem.data.api.AppointmentCancelRequest(
                                appointmentId = serverId,
                                reason = cancelReason.ifBlank { null },
                            )
                        try {
                            val mobileResponse = mobileApiService.cancelAppointment(cancelRequest)
                            if (mobileResponse.isSuccessful) {
                                pendingSyncDao.deletePendingSync(syncRecord)
                                addSyncLog(
                                    "🟢 API [POST /api/v1/mobile/appointments/cancel]: Приём #$serverId отменён.",
                                    "CLOUD_SYNC_SIMULATOR",
                                )
                                return updated
                            } else {
                                addSyncLog(
                                    "⚠️ Mobile cancel API отклонён: Код ${mobileResponse.code()} (fallback на PUT /appointments/{id})",
                                    "CLOUD_SYNC_SIMULATOR",
                                )
                                // Fall through to staff attempt
                            }
                        } catch (e: Exception) {
                            addSyncLog(
                                "⚠️ Mobile cancel API exception: ${e.message} (fallback на PUT /appointments/{id})",
                                "CLOUD_SYNC_SIMULATOR",
                            )
                            // Fall through to staff attempt
                        }
                    }

                    // Staff fallback (registrar/doctor status changes, or mobile
                    // cancel failed and we retry against the staff endpoint).
                    val response =
                        legacyApiService.updateAppointment(
                            id = serverId,
                            appointment =
                                com.aistudio.clinicsystem.data.api.StaffAppointmentUpdateRequest(
                                    status = toServerStatus(status),
                                    notes = notesText,
                                ),
                        )
                    if (response.isSuccessful) {
                        pendingSyncDao.deletePendingSync(syncRecord)
                        val endpoint =
                            if (mobileAttempted) {
                                "PUT /api/v1/appointments/$serverId (fallback)"
                            } else {
                                "PUT /api/v1/appointments/$serverId"
                            }
                        addSyncLog("🟢 API [$endpoint]: Статус $status подтвержден на сервере.", "CLOUD_SYNC_SIMULATOR")
                    } else {
                        addSyncLog("⚠️ API Статус отклонен сервером: Код ${response.code()}", "CLOUD_SYNC_SIMULATOR")
                    }
                } catch (e: Exception) {
                    addSyncLog(
                        "⏳ Сервер FastAPI offline. Статус сохранен локально в очереди транзакций: ${e.localizedMessage}",
                        "CLOUD_SYNC_SIMULATOR",
                    )
                }
            } else {
                // Appointment not yet synced — server can't update what it doesn't
                // have. Log the situation; the CREATE_APPOINTMENT outbox row will
                // carry the final status.
                addSyncLog(
                    "ℹ️ Outbox: Приём #$id ещё не синхронизирован с сервером (serverId=null). " +
                        "Статус $status будет применён при следующей синхронизации создания.",
                    "CLOUD_SYNC_SIMULATOR",
                )
            }
            return updated
        }

        suspend fun updateAppointmentOnServerAndLocal(
            id: String,
            doctorName: String,
            date: String,
            time: String,
            reason: String,
            status: String,
            notes: String? = null,
        ): AppointmentEntity? {
            val appointment = getAppointmentById(id) ?: return null
            val localUpdate =
                appointment.copy(
                    doctorName = doctorName,
                    date = date,
                    time = time,
                    reason = reason,
                    status = status,
                    notes = notes ?: appointment.notes,
                    updatedAt = System.currentTimeMillis(),
                    version = appointment.version + 1,
                )
            updateAppointment(localUpdate)

            val serverId = appointment.serverId
            if (serverId == null) {
                addSyncLog("ℹ️ Запись изменена локально: серверный ID ещё не получен.", "CLOUD_SYNC_SIMULATOR")
                return localUpdate
            }

            return try {
                val response =
                    legacyApiService.updateAppointment(
                        id = serverId,
                        appointment =
                            StaffAppointmentUpdateRequest(
                                doctorId = resolveDoctorServerId(doctorName),
                                appointmentDate = date,
                                appointmentTime = time.ifBlank { null },
                                notes = notes ?: reason.ifBlank { appointment.notes },
                                status = toServerStatus(status),
                            ),
                    )
                val dto = response.body()
                if (!response.isSuccessful || dto == null) {
                    addSyncLog("⚠️ Сервер не принял изменение записи #$serverId: HTTP ${response.code()}.", "CLOUD_SYNC_SIMULATOR")
                    localUpdate
                } else {
                    val persisted =
                        localUpdate.copy(
                            serverId = dto.id,
                            date = dto.appointmentDate,
                            time = dto.appointmentTime ?: localUpdate.time,
                            status = fromServerStatus(dto.status),
                            notes = dto.notes ?: localUpdate.notes,
                            updatedAt = System.currentTimeMillis(),
                        )
                    updateAppointment(persisted)
                    addSyncLog("🟢 Запись #$serverId изменена на сервере.", "CLOUD_SYNC_SIMULATOR")
                    persisted
                }
            } catch (e: Exception) {
                addSyncLog("⏳ Изменение записи #$serverId сохранено локально: ${e.message}", "CLOUD_SYNC_SIMULATOR")
                localUpdate
            }
        }

        private fun fromServerStatus(status: String): String =
            when (status.lowercase()) {
                "scheduled" -> "PENDING"
                "confirmed" -> "APPROVED"
                "completed" -> "COMPLETED"
                "cancelled", "canceled" -> "CANCELLED"
                else -> status.uppercase()
            }

        override suspend fun createMedicalRecordOnServerAndLocal(
            token: String?,
            patientPhone: String,
            doctorName: String,
            diagnosis: String,
            prescription: String,
            recommendations: String,
        ): MedicalRecordEntity {
            val visitDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val newRecord =
                MedicalRecordEntity(
                    patientPhone = patientPhone,
                    doctorName = doctorName,
                    diagnosis = diagnosis,
                    prescription = prescription,
                    visitDate = visitDate,
                    recommendations = recommendations,
                )
            val savedRecord = insertMedicalRecord(newRecord)

            // M-CONTRACT-FIX: the backend removed POST /api/v1/patients/records
            // together with the legacy API — there is no server route that
            // accepts a free-form medical record from the mobile client anymore.
            // The record is kept in the local Room cache and NOT enqueued to the
            // outbox (the old code enqueued a row that would dead-letter with
            // 404 on the first sync attempt).
            addSyncLog(
                "ℹ️ Медкарта сохранена локально. Создание медкарт через сервер больше не публикуется backend-ом (legacy API удалён).",
                "CLOUD_SYNC_SIMULATOR",
            )
            return savedRecord
        }

        override suspend fun fetchMedicalRecordsFromServer(
            token: String?,
            phone: String,
            onNewRecordAction: (MedicalRecordEntity) -> Unit,
        ): List<MedicalRecordEntity> {
            // High-2 audit fix: prefer the mobile contract endpoint
            // GET /api/v1/mobile/lab/results over the legacy
            // GET /api/v1/patients/records/{phone}. The mobile endpoint:
            //   - authenticates via JWT (no need to pass phone — backend
            //     derives patient_id from current_user)
            //   - returns List<LabResultOut> with typed fields
            //   - enforces patient-scoped access (no cross-patient leak)
            //
            // The legacy endpoint accepted an arbitrary phone number in the
            // URL path — any authenticated user could read any patient's
            // medical records by guessing/enumerating phone numbers. The
            // mobile endpoint is patient-scoped by design.
            //
            // We map LabResultOut → MedicalRecordEntity for backward compat
            // with the existing UI (which expects MedicalRecordEntity). The
            // mapping is semantic: testName→diagnosis, resultValue→
            // prescription, resultDate→visitDate, referenceRange→
            // recommendations, notes→doctorName.
            addSyncLog("🛰️ CONNECTING to API: GET /api/v1/mobile/lab/results", "CLOUD_SYNC_SIMULATOR")
            try {
                val response = mobileApiService.getLabResults()
                if (response.isSuccessful && response.body() != null) {
                    val labResults = response.body()!!
                    addSyncLog("✓ УСПЕШНЫЙ ЗАПРОС: Импортировано ${labResults.size} lab results с сервера final.", "CLOUD_SYNC_SIMULATOR")
                    val results = mutableListOf<MedicalRecordEntity>()
                    for (dto in labResults) {
                        // High-1 audit fix: LabResultOut field mapping
                        val recordEntity =
                            MedicalRecordEntity(
                                id =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString(),
                                serverId = dto.id,
                                patientPhone = phone, // backend doesn't return it; use caller's phone
                                doctorName = dto.notes ?: "",
                                diagnosis = dto.testName,
                                prescription = dto.resultValue,
                                visitDate = dto.resultDate,
                                recommendations = dto.referenceRange,
                            )
                        val existing = medicalRecordDao.getMedicalRecordByServerId(dto.id)
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

                // High-2 audit fix: prefer mobile contract endpoints for
                // patient-side sync. The mobile /mobile/appointments/upcoming
                // endpoint:
                //   - authenticates via JWT
                //   - returns only the current patient's appointments (no
                //     cross-patient leak)
                //   - returns AppointmentUpcomingOut (typed, ISO 8601 datetime)
                //
                // The legacy /appointments endpoint returned ALL appointments
                // across ALL patients — a privacy violation for patient-side
                // use. We keep the legacy endpoint only for staff-side sync
                // (where the user is a doctor/registrar needing visibility
                // into all appointments).
                //
                // Similarly, /mobile/queues/my-position returns the current
                // patient's queue position, replacing the legacy /queue which
                // returned the entire clinic queue.
                val userRole = userResponse.body()?.role
                val isPatient = userRole == "Patient" || userRole.isNullOrBlank()

                if (isPatient) {
                    val patientPhone = userResponse.body()?.phone.orEmpty()
                    // Patient-side: use mobile endpoints (privacy-scoped)
                    addSyncLog("🛰️ GET /api/v1/mobile/appointments/upcoming (patient-scoped)", "CLOUD_SYNC_SIMULATOR")
                    val apptsResponse = mobileApiService.getUpcomingAppointments()
                    if (apptsResponse.isSuccessful && apptsResponse.body() != null) {
                        val serverList = apptsResponse.body()!!
                        addSyncLog("✓ Успешно получено ${serverList.size} предстоящих приёмов.", "CLOUD_SYNC_SIMULATOR")

                        // High-1 audit fix: map AppointmentUpcomingOut → AppointmentEntity
                        // using computed date/time accessors for ISO datetime split.
                        val entities =
                            serverList.map { dto ->
                                AppointmentEntity(
                                    id =
                                        java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                    serverId = dto.id,
                                    patientPhone = patientPhone,
                                    patientName = "",
                                    doctorName = dto.doctorName,
                                    specialty = dto.specialty,
                                    date = dto.date, // computed from appointment_date
                                    time = dto.time, // computed from appointment_date
                                    status = dto.status,
                                    reason = "",
                                    notes = "",
                                    clinicId = dto.clinicAddress,
                                    updatedAt = System.currentTimeMillis(),
                                    version = 1,
                                )
                            }
                        database.withTransaction {
                            for (entity in entities) {
                                val existing = appointmentDao.getAppointmentByServerId(entity.serverId ?: -1)
                                if (existing == null) {
                                    appointmentDao.insertAppointment(entity)
                                } else if (existing.status != entity.status) {
                                    appointmentDao.updateAppointment(
                                        existing.copy(
                                            status = entity.status,
                                            date = entity.date,
                                            time = entity.time,
                                            doctorName = entity.doctorName,
                                            specialty = entity.specialty,
                                            updatedAt = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            }
                        }

                        addSyncLog("✅ СИНХРОНИЗАЦИЯ ПРИЁМОВ УСПЕШНО ЗАВЕРШЕНА!", "CLOUD_SYNC_SIMULATOR")
                    }

                    // Sync patient's queue positions (mobile endpoint).
                    // M-CONTRACT-FIX: the backend returns { "positions": [...] }
                    // with my_number/current_number fields — the previous flat
                    // QueuePositionOut(position=…) never parsed (HTTP 200 but
                    // Moshi type mismatch).
                    addSyncLog("🛰️ GET /api/v1/mobile/queues/my-position (patient-scoped)", "CLOUD_SYNC_SIMULATOR")
                    try {
                        val queueResponse = mobileApiService.getMyQueuePosition()
                        if (queueResponse.isSuccessful && queueResponse.body() != null) {
                            val positions = queueResponse.body()!!.positions
                            if (positions.isEmpty()) {
                                addSyncLog("ℹ️ Очередь: активных позиций нет.", "CLOUD_SYNC_SIMULATOR")
                            }
                            for (pos in positions) {
                                addSyncLog(
                                    "✓ Очередь «${pos.doctorName}»: мой номер #${pos.myNumber}, " +
                                        "текущий #${pos.currentNumber}, передо мной ${pos.patientsBeforeMe}, " +
                                        "ожидание ~${pos.estimatedWaitMinutes} мин (${pos.status})",
                                    "CLOUD_SYNC_SIMULATOR",
                                )
                            }
                        }
                    } catch (e: Exception) {
                        addSyncLog("ℹ️ Очередь: нет активной позиции (${e.message})", "CLOUD_SYNC_SIMULATOR")
                    }

                    val latency = System.currentTimeMillis() - startTime
                    com.aistudio.clinicsystem.utils.SyncMetricsManager
                        .recordSuccess(latency)
                    return true
                } else {
                    // Staff-side: current staff endpoints.
                    // M-CONTRACT-FIX: the legacy GET /api/v1/queue route is gone
                    // ("Legacy API удалён"). The live queue now comes from the
                    // QR-queue surface: GET /queue/available-specialists + per-
                    // specialist GET /queue/status/{specialist_id}.
                    addSyncLog("🛰️ GET /api/v1/queue/available-specialists (staff: активные очереди)", "CLOUD_SYNC_SIMULATOR")
                    try {
                        val specialistsResponse = legacyApiService.getAvailableSpecialists()
                        val specialists =
                            if (specialistsResponse.isSuccessful) {
                                specialistsResponse.body()?.specialists ?: emptyList()
                            } else {
                                addSyncLog("⚠️ Specialists list вернул код ${specialistsResponse.code()}.", "CLOUD_SYNC_SIMULATOR")
                                emptyList()
                            }

                        val snapshotsList = mutableListOf<QueueSnapshotEntity>()
                        for (specialist in specialists) {
                            val statusResponse = legacyApiService.getQueueStatus(specialist.id)
                            if (!statusResponse.isSuccessful) continue
                            val status = statusResponse.body() ?: continue
                            for (entry in status.entries) {
                                snapshotsList.add(
                                    QueueSnapshotEntity(
                                        id = entry.id,
                                        patientName = entry.patientName ?: "",
                                        appointmentId = entry.id, // queue-entry id (no appointment linkage in QR queue)
                                        position = entry.number,
                                        status = normalizeQueueStatus(entry.status),
                                        timestamp = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }
                        if (snapshotsList.isNotEmpty()) {
                            addSyncLog("✓ Активная очередь: ${snapshotsList.size} пациент(ов) у специалистов.", "CLOUD_SYNC_SIMULATOR")
                            database.withTransaction {
                                queueSnapshotDao.clearQueueSnapshots()
                                queueSnapshotDao.insertQueueSnapshots(snapshotsList)
                            }
                            addSyncLog("✓ Очередь закэширована в локальную базу данных (доступно оффлайн)", "CLOUD_SYNC_SIMULATOR")
                        }

                        // Delta sync for staff appointments.
                        // NOTE: the current GET /api/v1/appointments has no `since`
                        // parameter — the delta cursor is informational only.
                        val lastSync = com.aistudio.clinicsystem.utils.SyncMetricsManager.metrics.value.lastSyncTime
                        val sinceParam = if (lastSync > 0) lastSync else null
                        val clinicId = userResponse.body()?.clinicId ?: "clinic_base"
                        com.aistudio.clinicsystem.utils.SyncMetricsManager
                            .updateClinicId(clinicId)

                        addSyncLog(
                            "🛰️ GET /api/v1/appointments (staff: все приёмы клиники) [Delta cursor: $sinceParam]",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                        val appointmentsResponse = legacyApiService.getAppointments(limit = 200)
                        if (appointmentsResponse.isSuccessful && appointmentsResponse.body() != null) {
                            val serverList = appointmentsResponse.body()!!
                            addSyncLog("✓ Успешно получено ${serverList.size} записей с сервера.", "CLOUD_SYNC_SIMULATOR")

                            database.withTransaction {
                                for (appDto in serverList) {
                                    reconcileStaffAppointmentFromServer(appDto)
                                }
                            }

                            addSyncLog("✅ СИНХРОНИЗАЦИЯ С СЕРВЕРОМ 'final' УСПЕШНО ЗАВЕРШЕНА!", "CLOUD_SYNC_SIMULATOR")
                            val latency = System.currentTimeMillis() - startTime
                            com.aistudio.clinicsystem.utils.SyncMetricsManager
                                .recordSuccess(latency)
                            return true
                        } else {
                            addSyncLog("⚠️ Сервер вернул код ${appointmentsResponse.code()}.", "CLOUD_SYNC_SIMULATOR")
                            com.aistudio.clinicsystem.utils.SyncMetricsManager
                                .recordFailure()
                        }
                    } catch (e: Exception) {
                        addSyncLog("⚠️ Staff queue sync failed: ${e.message}", "CLOUD_SYNC_SIMULATOR")
                    }
                }
            } catch (e: Exception) {
                addSyncLog("🔴 Сбой синхронизации с API: ${e.localizedMessage}", "CLOUD_SYNC_SIMULATOR")
                addSyncLog("⏳ Работа в безопасном режиме сохранения в локальный кэш Room SQLite.", "CLOUD_SYNC_SIMULATOR")
                com.aistudio.clinicsystem.utils.SyncMetricsManager
                    .recordFailure()
            }
            return false
        }

        /**
         * M-CONTRACT-FIX: reconciles a staff appointment from GET
         * /api/v1/appointments (backend `Appointment` schema) into the local
         * Room cache. Deduplicates by `serverId`; server data wins for shared
         * fields (the backend has no version column, so local-only edits that
         * still sit in the outbox are protected by the stale-write guard).
         *
         * Stale-write guard: if there is a PENDING/PROCESSING/FAILED
         * `UPDATE_STATUS` outbox row for this serverId, skip reconciliation —
         * the local change has not been pushed yet.
         *
         * This method MUST be called inside a `database.withTransaction { ... }`
         * block — it performs multiple DAO calls that must be atomic.
         */
        private suspend fun reconcileStaffAppointmentFromServer(appDto: com.aistudio.clinicsystem.data.api.StaffAppointmentDto) {
            val existing = appointmentDao.getAppointmentByServerId(appDto.id)

            // Resolve the doctor display name/specialty from the cached doctor
            // directory (the appointments router returns doctor_id, not names).
            val doctor = appDto.doctorId?.let { database.doctorDao().getDoctorByServerId(it) }
            val doctorName =
                doctor?.fullName
                    ?: appDto.doctorId?.let { "Врач #$it" }
                    ?: "Врач"
            val specialty = doctor?.specialty ?: ""
            val updatedAtMs = System.currentTimeMillis()

            if (existing == null) {
                // New appointment from server — insert with a fresh local UUID.
                val entity =
                    AppointmentEntity(
                        id =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        serverId = appDto.id,
                        patientPhone = "", // appointments router doesn't expose the phone
                        patientName = appDto.patientName ?: "",
                        doctorName = doctorName,
                        specialty = specialty,
                        date = appDto.appointmentDate,
                        time = appDto.appointmentTime ?: "",
                        status = appDto.status,
                        reason = "",
                        notes = appDto.notes ?: "",
                        clinicId = "clinic_base",
                        updatedAt = updatedAtMs,
                        version = 1,
                    )
                appointmentDao.insertAppointment(entity)
                return
            }

            // Existing appointment — check stale-write guard.
            // Stage 3.4: payload format is `<serverId>|<status>|<notes>|<localUuid>`.
            val pendingForThis =
                pendingSyncDao.getAllPendingSyncs().any {
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

            // Server is the source of truth for shared fields — overwrite local.
            val merged =
                existing.copy(
                    patientName = appDto.patientName ?: existing.patientName,
                    doctorName = doctorName,
                    specialty = specialty.ifBlank { existing.specialty },
                    date = appDto.appointmentDate,
                    time = appDto.appointmentTime ?: existing.time,
                    status = appDto.status,
                    notes = appDto.notes ?: existing.notes,
                    updatedAt = updatedAtMs,
                    etag = null,
                )
            appointmentDao.updateAppointment(merged)
        }

        /**
         * M-CONTRACT-FIX: upsert of a patient-scoped [MobileAppointmentOut]
         * (GET /mobile/appointments/upcoming) into Room. This replaces the old
         * call that passed the upcoming-appointment DTO into
         * [reconcileStaffAppointmentFromServer] — a type error that could not
         * even compile once the staff DTO switched to the current backend
         * schema.
         */
        private suspend fun reconcileUpcomingAppointment(
            dto: com.aistudio.clinicsystem.data.api.MobileAppointmentOut,
            patientPhone: String,
        ) {
            val existing = appointmentDao.getAppointmentByServerId(dto.id)
            val updatedAtMs = System.currentTimeMillis()
            if (existing == null) {
                appointmentDao.insertAppointment(
                    AppointmentEntity(
                        id =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        serverId = dto.id,
                        patientPhone = patientPhone,
                        patientName = "",
                        doctorName = dto.doctorName,
                        specialty = dto.specialty,
                        date = dto.date,
                        time = dto.time,
                        status = dto.status,
                        reason = "",
                        notes = "",
                        clinicId = dto.clinicAddress,
                        updatedAt = updatedAtMs,
                        version = 1,
                    ),
                )
            } else if (existing.patientPhone != patientPhone ||
                existing.status != dto.status ||
                existing.date != dto.date ||
                existing.doctorName != dto.doctorName
            ) {
                appointmentDao.updateAppointment(
                    existing.copy(
                        patientPhone = patientPhone,
                        status = dto.status,
                        date = dto.date,
                        time = dto.time,
                        doctorName = dto.doctorName,
                        specialty = dto.specialty,
                        updatedAt = updatedAtMs,
                    ),
                )
            }
        }

        suspend fun syncDoctorsFromServer(): Boolean {
            return try {
                val response = mobileApiService.getDoctors()
                if (!response.isSuccessful || response.body() == null) return false
                database.withTransaction {
                    database.doctorDao().clearDoctors()
                    database.doctorDao().insertDoctors(response.body()!!.map { it.toEntity() })
                }
                true
            } catch (e: Exception) {
                addSyncLog("⚠️ Не удалось загрузить справочник врачей: ${e.message}", "SYSTEM_SYNC")
                false
            }
        }

        suspend fun updateQueueStatusOnServerAndLocal(
            snapshotId: Int,
            newStatus: String,
        ): Boolean {
            val response =
                when (newStatus) {
                    "CALLED" -> legacyApiService.callQueueEntry(snapshotId)
                    "IN_PROGRESS" -> legacyApiService.startQueueVisit(snapshotId)
                    "COMPLETED" -> legacyApiService.completeQueueVisit(snapshotId)
                    else -> return false
                }
            if (!response.isSuccessful) {
                addSyncLog("⚠️ Сервер не изменил статус очереди #$snapshotId: HTTP ${response.code()}.", "CLOUD_SYNC_SIMULATOR")
                return false
            }
            val target =
                queueSnapshotDao.getAllQueueSnapshots().firstOrNull { it.id == snapshotId }
                    ?: return false
            queueSnapshotDao.insertQueueSnapshots(listOf(target.copy(status = newStatus)))
            addSyncLog("📢 Статус пациента #$snapshotId изменён на $newStatus на сервере.", "CLOUD_SYNC_SIMULATOR")
            return true
        }

        suspend fun removeQueueEntryOnServerAndLocal(snapshotId: Int): Boolean {
            val response = legacyApiService.cancelQueueEntry(snapshotId)
            if (!response.isSuccessful) {
                addSyncLog("⚠️ Сервер не удалил пациента из очереди #$snapshotId: HTTP ${response.code()}.", "CLOUD_SYNC_SIMULATOR")
                return false
            }
            val snapshots = queueSnapshotDao.getAllQueueSnapshots()
            queueSnapshotDao.clearQueueSnapshots()
            queueSnapshotDao.insertQueueSnapshots(snapshots.filter { it.id != snapshotId })
            addSyncLog("🗑️ Пациент #$snapshotId исключён из очереди на сервере.", "CLOUD_SYNC_SIMULATOR")
            return true
        }

        /**
         * M-CONTRACT-FIX: the backend no longer exposes
         * POST /api/v1/queue/register (registration by appointment id). Queue
         * joining now works exclusively through the QR-token flow
         * (/api/v1/queue/join/start + /join/complete), which the mobile app
         * does not implement; StaffViewModel falls back to the local queue
         * snapshot when this throws.
         */
        override suspend fun registerInQueue(appointmentId: String): Unit =
            throw UnsupportedOperationException(
                "Сервер не поддерживает запись в очередь по ID приёма — используйте QR-регистрацию.",
            )

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
        fun observeAppointmentsWithSync(patientPhone: String): Flow<Resource<List<AppointmentEntity>>> =
            networkBoundResource(
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
                                reconcileUpcomingAppointment(dto, patientPhone)
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
                },
            )

        /**
         * M2/E5.3: Observes medical records with offline-first sync.
         *
         * Same pattern as [observeAppointmentsWithSync] but for medical records.
         */
        fun observeMedicalRecordsWithSync(patientPhone: String): Flow<Resource<List<MedicalRecordEntity>>> =
            networkBoundResource(
                query = {
                    medicalRecordDao.getRecordsByPatientFlow(patientPhone)
                },
                fetch = {
                    mobileApiService.getLabResults()
                },
                saveFetchResult = { response ->
                    if (response.isSuccessful && response.body() != null) {
                        val serverList = response.body()!!
                        // Stage 6: lab results are now stored in lab_results table
                        // via observeLabResultsWithSync. This method still fetches
                        // from /mobile/lab/results for backward compatibility, but
                        // the data is also written to the lab_results table.
                        database.withTransaction {
                            for (dto in serverList) {
                                val existing = medicalRecordDao.getMedicalRecordByServerId(dto.id)
                                if (existing == null) {
                                    // High-1 audit fix: aligned with backend LabResultOut DTO.
                                    // Backend no longer returns patientPhone/doctorName/result/
                                    // performedAt as separate fields — these are now derived
                                    // from the new backend contract (result_value, result_date,
                                    // notes). The medical_records table fields are mapped
                                    // semantically: testName→diagnosis, resultValue→
                                    // prescription, resultDate→visitDate, referenceRange→
                                    // recommendations, notes→doctorName.
                                    val entity =
                                        MedicalRecordEntity(
                                            id =
                                                java.util.UUID
                                                    .randomUUID()
                                                    .toString(),
                                            serverId = dto.id,
                                            patientPhone = patientPhone,
                                            doctorName = dto.notes ?: "",
                                            diagnosis = dto.testName,
                                            prescription = dto.resultValue,
                                            visitDate = dto.resultDate,
                                            recommendations = dto.referenceRange,
                                        )
                                    medicalRecordDao.insertRecord(entity)
                                }
                            }
                        }
                        addSyncLog("✓ NBR: Synced ${serverList.size} records from server", "SYSTEM_SYNC")
                    }
                },
                shouldFetch = { cachedData ->
                    cachedData.isEmpty()
                },
                onFetchFailed = { throwable ->
                    addSyncLog("⚠️ NBR: Medical records fetch failed: ${throwable.message}", "SYSTEM_SYNC")
                },
            )

        /**
         * Stage 6: Observes lab results with offline-first sync.
         *
         * Fetches from GET /api/v1/mobile/lab/results and stores in the
         * lab_results table (NOT medical_records). Uses the same NBR pattern
         * as observeMedicalRecordsWithSync.
         */
        fun observeLabResultsWithSync(patientPhone: String): Flow<Resource<List<LabResultEntity>>> =
            networkBoundResource(
                query = {
                    labResultDao.getResultsByPatientFlow(patientPhone)
                },
                fetch = {
                    mobileApiService.getLabResults()
                },
                saveFetchResult = { response ->
                    if (response.isSuccessful && response.body() != null) {
                        val serverList = response.body()!!
                        database.withTransaction {
                            for (dto in serverList) {
                                val existing = labResultDao.getLabResultByServerId(dto.id)
                                if (existing == null) {
                                    // High-1 audit fix: aligned with backend LabResultOut DTO.
                                    // Backend returns: result_value, reference_range, unit,
                                    // result_date, status, notes. Previous client DTO
                                    // expected patientPhone, result, performedAt, doctorName
                                    // which the backend never returned (all were null).
                                    // Backend does NOT return patientPhone — we use the
                                    // current patient's phone from the enclosing scope.
                                    // Backend "notes" replaces the old "doctorName" field
                                    // (arbitrary notes including doctor attribution).
                                    val entity =
                                        LabResultEntity(
                                            id =
                                                java.util.UUID
                                                    .randomUUID()
                                                    .toString(),
                                            serverId = dto.id,
                                            patientPhone = patientPhone,
                                            testName = dto.testName,
                                            result = dto.resultValue,
                                            unit = dto.unit,
                                            referenceRange = dto.referenceRange,
                                            status = dto.status,
                                            performedAt = dto.resultDate,
                                            doctorName = dto.notes,
                                        )
                                    labResultDao.insertAll(listOf(entity))
                                }
                            }
                        }
                        addSyncLog("✓ NBR: Synced ${serverList.size} lab results", "SYSTEM_SYNC")
                    }
                },
                shouldFetch = { cachedData ->
                    cachedData.isEmpty()
                },
                onFetchFailed = { throwable ->
                    addSyncLog("⚠️ NBR: Lab results fetch failed: ${throwable.message}", "SYSTEM_SYNC")
                },
            )
    }

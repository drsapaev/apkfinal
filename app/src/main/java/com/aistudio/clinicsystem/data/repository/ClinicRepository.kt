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
    // TASK-3: dedicated lab results flow
    val allLabResults: Flow<List<LabResultEntity>> = labResultDao.getAllResultsFlow()
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
                logMessage = "Created appointment: ${appointment.patientName} -> ${appointment.doctorName} (${appointment.date} ${appointment.time})",
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
                            com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_SELF -> {
                                // TASK-1: patient self-booking retry — replay the
                                // ORIGINAL scenario through the mobile contract
                                // only. Never dispatched to the staff endpoint.
                                retrySelfBooking(sync)
                            }
                            com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_STAFF -> {
                                // TASK-1: staff booking retry — replay through
                                // POST /api/v1/appointments only. The booked
                                // patient identity comes from the structured
                                // payload, not from the display name.
                                retryStaffBooking(sync)
                            }
                            com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT -> {
                                val dto = appointmentAdapter.fromJson(sync.payload)
                                if (dto != null) {
                                    // M-CONTRACT-FIX: convert the legacy outbox payload
                                    // to the current POST /api/v1/appointments request
                                    // (int patient_id + appointment_date/appointment_time).
                                    val staffCreate =
                                        buildStaffCreateRequest(
                                            patientId = null, // legacy payload has no structured id — resolve from phone
                                            patientPhone = dto.patientPhone,
                                            patientName = dto.patientName,
                                            doctorId = null,
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
                            com.aistudio.clinicsystem.data.outbox.OutboxOperation.UPDATE_APPOINTMENT -> {
                                // TASK-2: full-edit retry via the staff PUT
                                // with the structured payload. Marks the local
                                // row clean on success / REJECTED on 4xx.
                                retryUpdateAppointment(sync)
                            }
                            com.aistudio.clinicsystem.data.outbox.OutboxOperation.UPDATE_STATUS -> {
                                // Stage 1.2 / 3.2: payload = `<serverId:Int>|<status>|<notes>|<localUuid>`
                                // TASK-1: 5th segment `<actor>` (SELF|STAFF) — the
                                // retry replays the ORIGINAL route. Legacy rows
                                // (4 segments) keep the historical STAFF behavior.
                                val parts = sync.payload.split("|", limit = 5)
                                if (parts.size >= 3) {
                                    val serverId = parts[0].toIntOrNull()
                                    val status = parts[1]
                                    val notes = parts[2]
                                    val localUuid = parts.getOrNull(3)
                                    val actor =
                                        com.aistudio.clinicsystem.data.outbox.OutboxRouting.parseActor(sync.payload)
                                    if (serverId != null) {
                                        if (com.aistudio.clinicsystem.data.outbox.OutboxRouting.statusRoute(actor, status) ==
                                            com.aistudio.clinicsystem.data.outbox.OutboxRouting.StatusRoute.MOBILE_CANCEL
                                        ) {
                                            // Patient cancel retry — mobile contract only.
                                            val cancelRequest =
                                                com.aistudio.clinicsystem.data.api.AppointmentCancelRequest(
                                                    appointmentId = serverId,
                                                    reason = notes.takeIf { it.isNotBlank() && it != "Отклонено." }?.removePrefix("Отменено: "),
                                                )
                                            val mobileResponse =
                                                try {
                                                    mobileApiService.cancelAppointment(cancelRequest)
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            if (mobileResponse != null && mobileResponse.isSuccessful) {
                                                bumpLocalVersion(localUuid)
                                                addSyncLog(
                                                    "✓ Outbox: Приём отменён пациентом (mobile route, serverId=$serverId)",
                                                    "CLOUD_SYNC_SIMULATOR",
                                                )
                                                ProcessResult.Success
                                            } else {
                                                val code = mobileResponse?.code() ?: 0
                                                if (mobileResponse == null) {
                                                    ProcessResult.TransportError(IllegalStateException("mobile cancel transport failure"))
                                                } else {
                                                    ProcessResult.HttpFailure(code, "HTTP $code")
                                                }
                                            }
                                        } else {
                                            // Staff route — generic PUT /api/v1/appointments/{id}.
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
                                                bumpLocalVersion(localUuid)
                                                addSyncLog(
                                                    "✓ Outbox: Обновлен статус приема (serverId=$serverId) → $status",
                                                    "CLOUD_SYNC_SIMULATOR",
                                                )
                                                ProcessResult.Success
                                            } else {
                                                ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                                            }
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
                        is ProcessResult.TransportError -> {
                            // Network/exception error — schedule retry with backoff
                            handleOutboxFailureWithCode(
                                sync,
                                null,
                                result.cause.localizedMessage ?: result.cause.javaClass.simpleName,
                                retryPolicy,
                            )
                            addSyncLog("⚠️ Outbox: Сбой (${sync.type}): ${result.cause.message}. Повтор через backoff.", "CLOUD_SYNC_SIMULATOR")
                        }
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

            /** TASK-1: transport-level failure (no HTTP response) — retry with backoff. */
            data class TransportError(
                val cause: Exception,
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

        /**
         * TASK-1: PATIENT self-booking. The signed-in patient books for
         * themselves through the mobile contract
         * (POST /api/v1/mobile/appointments/book — JWT, patient-scoped).
         *
         * There is deliberately NO fallback to the staff endpoint: a patient
         * has no registrar rights, and after a server rejection retrying the
         * same payload against a different route would either 403 or create
         * a booking the patient never intended. The row stays queued with
         * owner=PATIENT and is retried exclusively against the same route.
         *
         * `patientId`/`doctorId` are structured identifiers captured by the
         * UI from the doctor directory; identity is NOT re-derived from the
         * display name (serverId resolution is a fallback of last resort).
         */
        override suspend fun createAppointmentOnServerAndLocal(
            token: String?,
            patientId: Int?,
            patientPhone: String,
            patientName: String,
            doctorId: Int?,
            doctorName: String,
            specialty: String,
            date: String,
            time: String,
            reason: String,
        ): AppointmentEntity =
            enqueueAndAttemptCreateAppointment(
                owner = com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_SELF,
                patientId = patientId,
                patientPhone = patientPhone,
                patientName = patientName,
                doctorId = doctorId,
                doctorName = doctorName,
                specialty = specialty,
                date = date,
                time = time,
                reason = reason,
            )

        /**
         * TASK-1: STAFF booking for a chosen patient. The registrar/doctor
         * books a SPECIFIC patient through the staff endpoint
         * (POST /api/v1/appointments — requires int patient_id, resolved
         * structurally from the patient registry by phone, not from names).
         *
         * There is deliberately NO fallback to the mobile contract: the
         * mobile endpoint books the JWT caller as the patient, which is
         * exactly the wrong identity when a registrar books for someone else.
         */
        override suspend fun createAppointmentForPatientOnServerAndLocal(
            token: String?,
            patientId: Int?,
            patientPhone: String,
            patientName: String,
            doctorId: Int?,
            doctorName: String,
            specialty: String,
            date: String,
            time: String,
            reason: String,
        ): AppointmentEntity =
            enqueueAndAttemptCreateAppointment(
                owner = com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_STAFF,
                patientId = patientId,
                patientPhone = patientPhone,
                patientName = patientName,
                doctorId = doctorId,
                doctorName = doctorName,
                specialty = specialty,
                date = date,
                time = time,
                reason = reason,
            )

        /**
         * TASK-1: shared offline-first create flow. Writes the local
         * placeholder, enqueues a structured outbox row carrying the owner
         * and the identifiers, then attempts the ORIGINAL route once.
         *   - transport failure  → row stays queued, retried later verbatim;
         *   - server rejection   → row is left for the outbox classifier,
         *     which dead-letters non-retriable 4xx on the next flush. No
         *     second attempt through any other endpoint.
         */
        private suspend fun enqueueAndAttemptCreateAppointment(
            owner: com.aistudio.clinicsystem.data.outbox.OutboxOperation,
            patientId: Int?,
            patientPhone: String,
            patientName: String,
            doctorId: Int?,
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

            // TASK-1: structured outbox payload — owner + identifiers, so the
            // retry replays the original scenario (patient self / staff for
            // patient) with the same route and the same identity.
            val payloadAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentOutboxPayload::class.java)
            val payload =
                com.aistudio.clinicsystem.data.api.AppointmentOutboxPayload(
                    owner =
                        if (owner == com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_SELF) {
                            com.aistudio.clinicsystem.data.outbox.OutboxRouting.OWNER_PATIENT
                        } else {
                            com.aistudio.clinicsystem.data.outbox.OutboxRouting.OWNER_STAFF
                        },
                    patientId = patientId,
                    patientPhone = patientPhone,
                    patientName = patientName,
                    doctorId = doctorId,
                    doctorName = doctorName,
                    specialty = specialty,
                    date = date,
                    time = time,
                    reason = reason,
                    status = "PENDING",
                )
            val syncRecord =
                PendingSyncEntity(
                    type = owner.code,
                    payload = payloadAdapter.toJson(payload),
                    clientRequestId = clientReqId,
                )
            pendingSyncDao.insertPendingSync(syncRecord)

            try {
                val outcome =
                    when (owner) {
                        com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_SELF,
                        -> {
                            // Patient self-booking via the mobile contract.
                            // doctor_id is required by the backend — resolve
                            // structurally, falling back to the cached
                            // directory ONLY when the UI could not provide one.
                            val resolvedDoctorId = doctorId ?: resolveDoctorServerId(doctorName)
                            if (resolvedDoctorId == null) {
                                addSyncLog(
                                    "⚠️ Mobile booking: doctor_id не определён — запись останется локальной до синхронизации.",
                                    "CLOUD_SYNC_SIMULATOR",
                                )
                                null
                            } else {
                                val bookRequest =
                                    com.aistudio.clinicsystem.data.api.AppointmentBookRequest(
                                        doctorId = resolvedDoctorId,
                                        preferredDate = date,
                                        preferredTime = time.ifBlank { null },
                                        complaint = reason.ifBlank { null },
                                        services = emptyList(),
                                        notes = null,
                                    )
                                val mobileResponse = mobileApiService.bookAppointment(bookRequest)
                                if (mobileResponse.isSuccessful && mobileResponse.body() != null) {
                                    val saved = mobileResponse.body()!!
                                    CreateOutcome.Confirmed(
                                        ConfirmedCreate(
                                            serverId = saved.id,
                                            date = saved.date,
                                            time = saved.time,
                                            doctorName = saved.doctorName,
                                            specialty = saved.specialty,
                                            clinicAddress = saved.clinicAddress,
                                        ),
                                    )
                                } else {
                                    // TASK-1: NO staff fallback after a server
                                    // rejection — the row stays queued for the
                                    // outbox classifier.
                                    addSyncLog(
                                        "⚠️ Mobile API отклонено сервером: Код ${mobileResponse.code()} (маршрут не меняется, запись в очереди)",
                                        "CLOUD_SYNC_SIMULATOR",
                                    )
                                    CreateOutcome.Rejected(mobileResponse.code())
                                }
                            }
                        }
                        com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_STAFF -> {
                            // Staff booking for the chosen patient via
                            // POST /api/v1/appointments.
                            val staffCreate =
                                buildStaffCreateRequest(
                                    patientId = patientId,
                                    patientPhone = patientPhone,
                                    patientName = patientName,
                                    doctorId = doctorId,
                                    doctorName = doctorName,
                                    date = date,
                                    time = time,
                                    reason = reason,
                                    status = "PENDING",
                                )
                            if (staffCreate == null) {
                                addSyncLog(
                                    "⚠️ POST /api/v1/appointments: patient_id не разрешён — запись останется локальной до синхронизации.",
                                    "CLOUD_SYNC_SIMULATOR",
                                )
                                null
                            } else {
                                val response = legacyApiService.createAppointment(staffCreate)
                                if (response.isSuccessful && response.body() != null) {
                                    val saved = response.body()!!
                                    CreateOutcome.Confirmed(
                                        ConfirmedCreate(
                                            serverId = saved.id,
                                            date = saved.appointmentDate,
                                            time = saved.appointmentTime ?: "",
                                            doctorName = doctorName,
                                            specialty = specialty,
                                            clinicAddress = null,
                                        ),
                                    )
                                } else {
                                    // TASK-1: NO mobile fallback after a server
                                    // rejection — the mobile route would book
                                    // the STAFF account as the patient.
                                    addSyncLog(
                                        "⚠️ API Отклонено сервером: Код ${response.code()} (маршрут не меняется, запись в очереди)",
                                        "CLOUD_SYNC_SIMULATOR",
                                    )
                                    CreateOutcome.Rejected(response.code())
                                }
                            }
                        }
                        else -> null
                    }

                when (outcome) {
                    is CreateOutcome.Rejected -> {
                        // TASK-2: the server REFUSED the create (4xx). This is
                        // not network absence — retrying on another route is
                        // forbidden and retrying this route is pointless until
                        // the user changes something. Mark the local row
                        // REJECTED and dead-letter the outbox row immediately.
                        val marked = savedApp.copy(syncState = AppointmentEntity.SYNC_STATE_REJECTED)
                        updateAppointment(marked)
                        deadLetterOutboxRow(syncRecord, "HTTP ${outcome.httpCode} (server rejected create)")
                        addSyncLog(
                            "⛔ Сервер отклонил запись: HTTP ${outcome.httpCode}. Изменение помечено как отклонённое, маршрут не менялся.",
                            "SYSTEM_SYNC",
                        )
                        return marked
                    }
                    is CreateOutcome.Confirmed -> {
                        val saved = outcome.normalized
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
                                serverId = saved.serverId,
                                date = if (saved.date.isBlank()) date else saved.date,
                                time = resolvedTime,
                                doctorName = saved.doctorName.ifBlank { doctorName },
                                specialty = saved.specialty.ifBlank { specialty },
                                clinicId = saved.clinicAddress ?: newApp.clinicId,
                            )
                        insertAppointment(finalApp)
                        addSyncLog(
                            "🟢 API УСПЕХ: Приём записан на сервере с ID #${saved.serverId} (${syncRecord.type})",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                        return finalApp
                    }
                    null -> Unit // route not attempted (unresolvable identity) — row stays queued
                }
            } catch (e: Exception) {
                // Transport failure — the queued row will be retried verbatim
                // (same route, same owner) by retryUnsyncedWrites. The local
                // row is explicitly marked QUEUED so the UI can show a draft.
                val queued = savedApp.copy(syncState = AppointmentEntity.SYNC_STATE_QUEUED)
                updateAppointment(queued)
                addSyncLog(
                    "⏳ Сервер недоступен. Запись сохранена локально и добавлена в очередь отложенной отправки: ${e.localizedMessage}",
                    "CLOUD_SYNC_SIMULATOR",
                )
                return queued
            }
            return savedApp
        }

        /**
         * TASK-2: dead-letters an outbox row immediately at the call site —
         * used when the server answered with a non-retriable 4xx during the
         * immediate attempt. Keeps the classification rules of
         * retryUnsyncedWrites consistent for rows that never leave the
         * foreground path.
         */
        private suspend fun deadLetterOutboxRow(
            syncRecord: PendingSyncEntity,
            error: String,
        ) {
            pendingSyncDao.updateRetryStateWithHttpCode(
                id = syncRecord.id,
                status = "DEAD_LETTER",
                retryCount = 1,
                error = error,
                nextRetryAt = null,
                httpCode = null,
            )
        }

        /**
         * TASK-1: normalized result of the immediate create attempt — both
         * backend DTOs (mobile [com.aistudio.clinicsystem.data.api.AppointmentUpcomingOut]
         * and staff [com.aistudio.clinicsystem.data.api.StaffAppointmentDto])
         * are mapped into one shape so the reconciliation code below is
         * route-agnostic.
         */
        private data class ConfirmedCreate(
            val serverId: Int,
            val date: String,
            val time: String,
            val doctorName: String,
            val specialty: String,
            val clinicAddress: String?,
        )

        /** TASK-1: internal result of the immediate create attempt. */
        private sealed class CreateOutcome {
            data class Confirmed(val normalized: ConfirmedCreate) : CreateOutcome()
            data class Rejected(val httpCode: Int) : CreateOutcome()
        }

        /** TASK-1: Moshi adapter for the structured create-appointment payload. */
        private val outboxPayloadAdapter by lazy {
            moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentOutboxPayload::class.java)
        }

        /** TASK-2: outbox type codes that create an appointment. */
        private val CREATE_OPERATION_CODES =
            setOf(
                com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT.code,
                com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_SELF.code,
                com.aistudio.clinicsystem.data.outbox.OutboxOperation.CREATE_APPOINTMENT_STAFF.code,
            )

        /**
         * TASK-1: outbox retry for CREATE_APPOINTMENT_SELF. Route is FIXED to
         * the mobile contract; doctor identity is resolved from the payload's
         * structured doctor_id (fallback: cached directory). A row whose
         * doctor can never be resolved is dead-lettered — retrying it against
         * a different route would break ownership rules.
         */
        private suspend fun retrySelfBooking(
            sync: PendingSyncEntity,
        ): ProcessResult {
            val payload =
                outboxPayloadAdapter.fromJson(sync.payload)
                    ?: return ProcessResult.PayloadCorrupt("payload is null")
            val doctorId = payload.doctorId ?: resolveDoctorServerId(payload.doctorName)
            if (doctorId == null) {
                addSyncLog(
                    "💀 Outbox: CREATE_APPOINTMENT_SELF без doctor_id — маршрутизация в staff-эндпоинт запрещена (владелец — пациент).",
                    "SYSTEM_SYNC",
                )
                return ProcessResult.HttpFailure(404, "doctor_id unresolvable for self-booking")
            }
            return try {
                val bookRequest =
                    com.aistudio.clinicsystem.data.api.AppointmentBookRequest(
                        doctorId = doctorId,
                        preferredDate = payload.date,
                        preferredTime = payload.time.ifBlank { null },
                        complaint = payload.reason.ifBlank { null },
                        services = emptyList(),
                        notes = null,
                    )
                val response = mobileApiService.bookAppointment(bookRequest)
                if (response.isSuccessful && response.body() != null) {
                    val saved = response.body()!!
                    reconcileConfirmedCreate(sync, saved.id, saved.date, saved.time, saved.doctorName, saved.specialty)
                    ProcessResult.Success
                } else {
                    ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                ProcessResult.TransportError(e)
            }
        }

        /**
         * TASK-1: outbox retry for CREATE_APPOINTMENT_STAFF. Route is FIXED to
         * POST /api/v1/appointments; the patient identity comes from the
         * structured patient_id, re-resolved from the phone only as a
         * fallback. Unresolvable patient → dead-letter (a staff create
         * without patient_id can never succeed).
         */
        private suspend fun retryStaffBooking(
            sync: PendingSyncEntity,
        ): ProcessResult {
            val payload =
                outboxPayloadAdapter.fromJson(sync.payload)
                    ?: return ProcessResult.PayloadCorrupt("payload is null")
            val patientId = payload.patientId ?: resolvePatientIdByPhone(payload.patientPhone)
            if (patientId == null) {
                return ProcessResult.HttpFailure(404, "patient not found on server")
            }
            val doctorId = payload.doctorId ?: resolveDoctorServerId(payload.doctorName)
            return try {
                val notesParts = listOf(payload.reason, payload.patientName).filter { it.isNotBlank() }
                val request =
                    com.aistudio.clinicsystem.data.api.StaffAppointmentCreateRequest(
                        patientId = patientId,
                        doctorId = doctorId,
                        appointmentDate = payload.date,
                        appointmentTime = payload.time.ifBlank { null },
                        notes = notesParts.joinToString("\n").ifBlank { null },
                        status = toServerStatus(payload.status),
                    )
                val response = legacyApiService.createAppointment(request)
                if (response.isSuccessful && response.body() != null) {
                    val saved = response.body()!!
                    reconcileConfirmedCreate(
                        sync,
                        saved.id,
                        saved.appointmentDate,
                        saved.appointmentTime ?: payload.time,
                        payload.doctorName,
                        payload.specialty,
                    )
                    ProcessResult.Success
                } else {
                    ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                ProcessResult.TransportError(e)
            }
        }

        /**
         * TASK-1: after an outbox retry confirms the create on the server,
         * swap the local placeholder for the confirmed entity (atomic).
         */
        private suspend fun reconcileConfirmedCreate(
            sync: PendingSyncEntity,
            serverId: Int,
            date: String,
            time: String,
            doctorName: String,
            specialty: String,
        ) {
            val local = appointmentDao.getAppointmentByClientRequestId(sync.clientRequestId)
            if (local != null) {
                database.withTransaction {
                    appointmentDao.deleteAppointmentById(local.id)
                    appointmentDao.insertAppointment(
                        local.copy(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            serverId = serverId,
                            date = date.ifBlank { local.date },
                            time = if (time.isBlank() || time == "00:00") local.time else time,
                            doctorName = doctorName.ifBlank { local.doctorName },
                            specialty = specialty.ifBlank { local.specialty },
                            version = local.version + 1,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            addSyncLog("✓ Outbox: Синхронизирован приём (serverId=$serverId, ${sync.type})", "CLOUD_SYNC_SIMULATOR")
        }

        /** Stage 3.2 helper: bump the local version after a confirmed server write. */
        private suspend fun bumpLocalVersion(localUuid: String?) {
            if (localUuid == null) return
            val local = appointmentDao.getAppointmentById(localUuid) ?: return
            appointmentDao.updateAppointment(
                local.copy(
                    version = local.version + 1,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        /**
         * TASK-2: outbox retry for UPDATE_APPOINTMENT — delivers a deferred
         * full edit through PUT /api/v1/appointments/{id} and reconciles the
         * local row (clean on success; REJECTED on 4xx).
         */
        private suspend fun retryUpdateAppointment(sync: PendingSyncEntity): ProcessResult {
            val adapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentEditOutboxPayload::class.java)
            val payload =
                adapter.fromJson(sync.payload)
                    ?: return ProcessResult.PayloadCorrupt("payload is null")
            return try {
                val response =
                    legacyApiService.updateAppointment(
                        id = payload.serverId,
                        appointment =
                            StaffAppointmentUpdateRequest(
                                doctorId = payload.doctorId,
                                appointmentDate = payload.date,
                                appointmentTime = payload.time.ifBlank { null },
                                notes = payload.notes,
                                status = toServerStatus(payload.status),
                            ),
                    )
                val dto = response.body()
                when {
                    response.isSuccessful && dto != null -> {
                        val local = appointmentDao.getAppointmentById(payload.localId)
                        if (local != null) {
                            appointmentDao.updateAppointment(
                                local.copy(
                                    date = dto.appointmentDate,
                                    time = dto.appointmentTime ?: local.time,
                                    status = fromServerStatus(dto.status),
                                    notes = dto.notes ?: local.notes,
                                    syncState = AppointmentEntity.SYNC_STATE_CLEAN,
                                    version = local.version + 1,
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                        addSyncLog("✓ Outbox: Правка приёма #${payload.serverId} доставлена на сервер.", "CLOUD_SYNC_SIMULATOR")
                        ProcessResult.Success
                    }
                    response.isSuccessful -> ProcessResult.PayloadCorrupt("empty body")
                    else -> {
                        // 4xx → the caller dead-letters; 5xx → retried. Mark
                        // REJECTED only for definitive refusals, matching the
                        // foreground classification.
                        if (!isRetriableHttp(response.code())) {
                            val local = appointmentDao.getAppointmentById(payload.localId)
                            if (local != null) {
                                appointmentDao.updateAppointment(
                                    local.copy(syncState = AppointmentEntity.SYNC_STATE_REJECTED),
                                )
                            }
                        }
                        ProcessResult.HttpFailure(response.code(), "HTTP ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                ProcessResult.TransportError(e)
            }
        }

        /**
         * TASK-2: undo support — re-enqueue the CREATE outbox row for a
         * not-yet-synced appointment that was restored via Undo, so the
         * restored draft is still delivered. [owner] fixes the retry route.
         */
        suspend fun reEnqueueCreateForUnsynced(
            entity: AppointmentEntity,
            owner: String,
        ) {
            if (entity.serverId != null) return
            val requestId = entity.clientRequestId ?: return
            val existing = pendingSyncDao.getAllPendingSyncs().any {
                it.clientRequestId == requestId && it.type in CREATE_OPERATION_CODES &&
                    (it.status == "PENDING" || it.status == "FAILED" || it.status == "PROCESSING")
            }
            if (existing) return
            val payloadAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentOutboxPayload::class.java)
            val payload =
                com.aistudio.clinicsystem.data.api.AppointmentOutboxPayload(
                    owner = owner,
                    patientId = null,
                    patientPhone = entity.patientPhone,
                    patientName = entity.patientName,
                    doctorId = resolveDoctorServerId(entity.doctorName),
                    doctorName = entity.doctorName,
                    specialty = entity.specialty,
                    date = entity.date,
                    time = entity.time,
                    reason = entity.reason,
                    status = entity.status,
                )
            pendingSyncDao.insertPendingSync(
                PendingSyncEntity(
                    type = com.aistudio.clinicsystem.data.outbox.OutboxRouting.ownerOperation(owner).code,
                    payload = payloadAdapter.toJson(payload),
                    clientRequestId = requestId,
                ),
            )
            addSyncLog("↩️ Undo: черновик записи восстановлен и снова в очереди отправки.", "SYSTEM_SYNC")
        }

        /**
         * TASK-2: undo support for a staff-created appointment — removes the
         * local row AND its queued CREATE row, so undoing a create never
         * leaves a phantom row that would later be created on the server.
         */
        suspend fun deleteLocalAppointmentAndOutbox(id: String) {
            val entity = appointmentDao.getAppointmentById(id) ?: return
            database.withTransaction {
                appointmentDao.deleteAppointmentById(id)
                entity.clientRequestId?.let { requestId ->
                    pendingSyncDao.deleteByClientRequestIdAndTypes(requestId, CREATE_OPERATION_CODES.toList())
                }
            }
            addSyncLog("↩️ Undo: локальная запись и её отложенная отправка отменены.", "SYSTEM_SYNC")
        }

        /**
         * M-CONTRACT-FIX: maps the legacy outbox payload (AppointmentDto) to
         * the POST /api/v1/appointments request the backend serves today.
         * Returns null when the patient cannot be resolved on the server —
         * the create schema requires an int `patient_id`.
         */
        private suspend fun buildStaffCreateRequest(
            patientId: Int?,
            patientPhone: String,
            patientName: String,
            doctorId: Int?,
            doctorName: String,
            date: String,
            time: String,
            reason: String,
            status: String,
        ): com.aistudio.clinicsystem.data.api.StaffAppointmentCreateRequest? {
            // TASK-1: use the STRUCTURED patientId when the UI resolved it
            // from the patient registry; re-resolve from the phone only as a
            // fallback for legacy callers. Identity is never guessed from
            // the display name.
            val resolvedPatientId =
                patientId
                    ?: resolvePatientIdByPhone(patientPhone)
            if (resolvedPatientId == null) {
                addSyncLog(
                    "⚠️ POST /api/v1/appointments: пациент с телефоном $patientPhone не найден на сервере — пациент должен быть зарегистрирован в клинике.",
                    "CLOUD_SYNC_SIMULATOR",
                )
                return null
            }
            val notesParts = listOf(reason, patientName).filter { it.isNotBlank() }
            return com.aistudio.clinicsystem.data.api.StaffAppointmentCreateRequest(
                patientId = resolvedPatientId,
                doctorId = doctorId ?: resolveDoctorServerId(doctorName),
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
            com.aistudio.clinicsystem.utils.ServerStatusMapper.toServer(clientStatus)

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
            actorIsPatient: Boolean,
        ): com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome? {
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
                    syncState = AppointmentEntity.SYNC_STATE_QUEUED,
                )

            // TASK-2: cancel of an appointment that has NOT been synced yet.
            // The local row is removed TOGETHER with its queued CREATE row —
            // the draft must never materialize on the server afterwards
            // ("отменённая offline-запись впоследствии не создаётся").
            val serverId = appointment.serverId
            if (serverId == null) {
                if (status == "CANCELLED") {
                    database.withTransaction {
                        appointmentDao.deleteAppointmentById(id)
                        appointment.clientRequestId?.let { requestId ->
                            pendingSyncDao.deleteByClientRequestIdAndTypes(requestId, CREATE_OPERATION_CODES.toList())
                        }
                    }
                    addSyncLog(
                        "🗑️ Offline-запись отменена: локальный черновик и отложенная CREATE-операция удалены (на сервере не создавался).",
                        "SYSTEM_SYNC",
                    )
                    return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.CancelledLocally(appointment)
                }
                // Non-cancel status on an unsynced appointment: amend the
                // pending CREATE payload so the eventual create carries the
                // new status; the local row keeps the QUEUED draft mark.
                updateAppointment(updated)
                amendPendingCreatePayload(updated)
                addSyncLog(
                    "ℹ️ Статус $status сохранён в черновике: приём ещё не синхронизирован; CREATE-операция обновлена.",
                    "CLOUD_SYNC_SIMULATOR",
                )
                return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Queued(
                    updated,
                    "приём ещё не синхронизирован с сервером",
                )
            }

            updateAppointment(updated)

            // Payload format: `<serverId:Int>|<status:String>|<notes:String>|<localUuid:String>|<actor:String>`
            // The 4th segment is the local UUID, used for client-side
            // reconciliation after the server confirms the update.
            // The 5th segment records the ACTOR (TASK-1) so the outbox
            // retry replays the original route: SELF → mobile cancel,
            // STAFF → staff PUT. Legacy rows without the segment are
            // treated as STAFF (the historical behavior).
            val actor =
                if (actorIsPatient) {
                    com.aistudio.clinicsystem.data.outbox.OutboxRouting.ACTOR_SELF
                } else {
                    com.aistudio.clinicsystem.data.outbox.OutboxRouting.ACTOR_STAFF
                }
            val payString = "$serverId|$status|$notesText|$id|$actor"
            val clientReqId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            val syncRecord =
                PendingSyncEntity(
                    type = com.aistudio.clinicsystem.data.outbox.OutboxOperation.UPDATE_STATUS.code,
                    payload = payString,
                    clientRequestId = clientReqId,
                )
            pendingSyncDao.insertPendingSync(syncRecord)

            try {
                // TASK-1: the route is chosen by the ACTOR, not by the
                // outcome of the first attempt. A patient cancels through
                // the mobile contract only; staff change statuses through
                // the generic PUT /api/v1/appointments/{id} only. There is
                // deliberately NO cross-route fallback after a server
                // rejection — a rejected request must not silently become
                // a second, different operation.
                if (com.aistudio.clinicsystem.data.outbox.OutboxRouting.statusRoute(actor, status) ==
                    com.aistudio.clinicsystem.data.outbox.OutboxRouting.StatusRoute.MOBILE_CANCEL
                ) {
                    val cancelRequest =
                        com.aistudio.clinicsystem.data.api.AppointmentCancelRequest(
                            appointmentId = serverId,
                            reason = cancelReason.ifBlank { null },
                        )
                    val mobileResponse = mobileApiService.cancelAppointment(cancelRequest)
                    if (mobileResponse.isSuccessful) {
                        pendingSyncDao.deletePendingSync(syncRecord)
                        val confirmed = updated.copy(syncState = AppointmentEntity.SYNC_STATE_CLEAN)
                        updateAppointment(confirmed)
                        addSyncLog(
                            "🟢 API [POST /api/v1/mobile/appointments/cancel]: Приём #$serverId отменён.",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                        return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Confirmed(confirmed, serverId)
                    } else {
                        val code = mobileResponse.code()
                        if (isRetriableHttp(code)) {
                            addSyncLog(
                                "⚠️ Mobile cancel API: HTTP $code — отмена останется в очереди на повтор.",
                                "CLOUD_SYNC_SIMULATOR",
                            )
                            return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Queued(
                                updated,
                                "HTTP $code — будет повторено автоматически",
                            )
                        }
                        // 4xx: the server refused (e.g. the 2-hour cancel
                        // window). Not network absence — mark REJECTED and
                        // dead-letter the row immediately.
                        val rejected = updated.copy(syncState = AppointmentEntity.SYNC_STATE_REJECTED)
                        updateAppointment(rejected)
                        deadLetterOutboxRow(syncRecord, "HTTP $code (server rejected cancel)")
                        addSyncLog(
                            "⛔ Сервер отклонил отмену приёма #$serverId: HTTP $code.",
                            "CLOUD_SYNC_SIMULATOR",
                        )
                        return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Rejected(
                            rejected,
                            code,
                            "сервер отклонил отмену (HTTP $code)",
                        )
                    }
                } else {
                    // Staff route (registrar/doctor status changes).
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
                        val confirmed = updated.copy(syncState = AppointmentEntity.SYNC_STATE_CLEAN)
                        updateAppointment(confirmed)
                        addSyncLog("🟢 API [PUT /api/v1/appointments/$serverId]: Статус $status подтвержден на сервере.", "CLOUD_SYNC_SIMULATOR")
                        return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Confirmed(confirmed, serverId)
                    } else {
                        val code = response.code()
                        if (isRetriableHttp(code)) {
                            addSyncLog("⏳ Сервер временно не принял статус: HTTP $code — в очереди на повтор.", "CLOUD_SYNC_SIMULATOR")
                            return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Queued(
                                updated,
                                "HTTP $code — будет повторено автоматически",
                            )
                        }
                        val rejected = updated.copy(syncState = AppointmentEntity.SYNC_STATE_REJECTED)
                        updateAppointment(rejected)
                        deadLetterOutboxRow(syncRecord, "HTTP $code (server rejected status change)")
                        addSyncLog("⚠️ API Статус отклонен сервером: Код $code.", "CLOUD_SYNC_SIMULATOR")
                        return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Rejected(
                            rejected,
                            code,
                            "сервер отклонил изменение статуса (HTTP $code)",
                        )
                    }
                }
            } catch (e: Exception) {
                // Transport failure — the queued row will be retried verbatim
                // (same route, same actor) by retryUnsyncedWrites.
                addSyncLog(
                    "⏳ Сервер недоступен. Статус сохранен локально в очереди транзакций: ${e.localizedMessage}",
                    "CLOUD_SYNC_SIMULATOR",
                )
                return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Queued(
                    updated,
                    e.localizedMessage ?: "нет сети",
                )
            }
        }

        /**
         * TASK-2: full edit with guaranteed deferred delivery.
         *
         * Outcome contract:
         *   - server confirmed            → Confirmed (row syncState = clean)
         *   - transport error / 5xx / 408 / 429 → Queued: an
         *     UPDATE_APPOINTMENT outbox row is durably stored and retried
         *     until delivered (row syncState = QUEUED — a visible draft);
         *   - server rejected (4xx)       → Rejected: row marked REJECTED,
         *     NO outbox row (retrying 403/409/422 is pointless), the user
         *     sees the rejection.
         *
         * If the appointment has not been synced yet (serverId == null), the
         * pending CREATE outbox row is amended instead, so the eventual
         * create already carries the edited values — local data and outbox
         * are updated consistently.
         */
        suspend fun updateAppointmentOnServerAndLocal(
            id: String,
            doctorName: String,
            date: String,
            time: String,
            reason: String,
            status: String,
            notes: String? = null,
        ): com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome? {
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
                    syncState = AppointmentEntity.SYNC_STATE_QUEUED,
                )
            updateAppointment(localUpdate)

            val serverId = appointment.serverId
            if (serverId == null) {
                // Not synced yet — amend the pending CREATE row so the
                // eventual create carries the edited values, then keep the
                // local edit as an explicit QUEUED draft.
                amendPendingCreatePayload(localUpdate)
                addSyncLog(
                    "ℹ️ Правка сохранена в черновике: приём ещё не синхронизирован, отложенная CREATE-операция обновлена.",
                    "CLOUD_SYNC_SIMULATOR",
                )
                return com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Queued(
                    localUpdate,
                    "приём ещё не синхронизирован с сервером",
                )
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
                    val code = response.code()
                    if (isRetriableHttp(code)) {
                        // 5xx / 408 / 429 — server-side trouble, retry later.
                        enqueueUpdateAppointmentRow(localUpdate, serverId)
                        addSyncLog("⏳ Сервер временно не принял правку #$serverId: HTTP $code — правка в очереди.", "CLOUD_SYNC_SIMULATOR")
                        com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Queued(
                            localUpdate,
                            "HTTP $code — будет повторено автоматически",
                        )
                    } else {
                        // 4xx — the server REFUSED the edit (403/409/422…).
                        // Not network absence: mark REJECTED, no retry row.
                        val rejected = localUpdate.copy(syncState = AppointmentEntity.SYNC_STATE_REJECTED)
                        updateAppointment(rejected)
                        addSyncLog("⛔ Сервер отклонил правку #$serverId: HTTP $code.", "CLOUD_SYNC_SIMULATOR")
                        com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Rejected(
                            rejected,
                            code,
                            "сервер отклонил изменение (HTTP $code)",
                        )
                    }
                } else {
                    val persisted =
                        localUpdate.copy(
                            serverId = dto.id,
                            date = dto.appointmentDate,
                            time = dto.appointmentTime ?: localUpdate.time,
                            status = fromServerStatus(dto.status),
                            notes = dto.notes ?: localUpdate.notes,
                            updatedAt = System.currentTimeMillis(),
                            syncState = AppointmentEntity.SYNC_STATE_CLEAN,
                        )
                    updateAppointment(persisted)
                    addSyncLog("🟢 Запись #$serverId изменена на сервере.", "CLOUD_SYNC_SIMULATOR")
                    com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Confirmed(persisted, dto.id)
                }
            } catch (e: Exception) {
                // Transport failure — durably queue the edit for delivery
                // after restart (regression scenario: offline edit → restart).
                enqueueUpdateAppointmentRow(localUpdate, serverId)
                addSyncLog("⏳ Изменение записи #$serverId сохранено локально и поставлено в очередь: ${e.message}", "CLOUD_SYNC_SIMULATOR")
                com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome.Queued(
                    localUpdate,
                    e.localizedMessage ?: "нет сети",
                )
            }
        }

        /** TASK-2: HTTP codes that are worth retrying later. */
        private fun isRetriableHttp(code: Int): Boolean =
            com.aistudio.clinicsystem.data.outbox.OutboxRouting.isRetriableHttp(code)

        /** TASK-2: durably queue a full-edit row for a synced appointment. */
        private suspend fun enqueueUpdateAppointmentRow(
            localUpdate: AppointmentEntity,
            serverId: Int,
        ) {
            val payloadAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentEditOutboxPayload::class.java)
            val payload =
                com.aistudio.clinicsystem.data.api.AppointmentEditOutboxPayload(
                    serverId = serverId,
                    localId = localUpdate.id,
                    doctorId = resolveDoctorServerId(localUpdate.doctorName),
                    doctorName = localUpdate.doctorName,
                    date = localUpdate.date,
                    time = localUpdate.time,
                    reason = localUpdate.reason,
                    status = localUpdate.status,
                    notes = localUpdate.notes,
                )
            val editAdapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentEditOutboxPayload::class.java)
            pendingSyncDao.insertPendingSync(
                PendingSyncEntity(
                    type = com.aistudio.clinicsystem.data.outbox.OutboxOperation.UPDATE_APPOINTMENT.code,
                    payload = payloadAdapter.toJson(payload),
                    clientRequestId = localUpdate.clientRequestId
                        ?: java.util.UUID.randomUUID().toString(),
                ),
            )
        }

        /**
         * TASK-2: rewrites the pending CREATE payload (found by
         * clientRequestId) so it carries the edited values — used when the
         * user edits an appointment that has not been synced yet.
         */
        private suspend fun amendPendingCreatePayload(edited: AppointmentEntity) {
            val requestId = edited.clientRequestId ?: return
            val pending =
                pendingSyncDao.getAllPendingSyncs().firstOrNull {
                    it.clientRequestId == requestId &&
                        it.type in CREATE_OPERATION_CODES &&
                        (it.status == "PENDING" || it.status == "FAILED" || it.status == "PROCESSING")
                } ?: return
            val adapter = moshi.adapter(com.aistudio.clinicsystem.data.api.AppointmentOutboxPayload::class.java)
            val current = adapter.fromJson(pending.payload) ?: return
            val amended =
                current.copy(
                    doctorName = edited.doctorName,
                    date = edited.date,
                    time = edited.time,
                    reason = edited.reason,
                    status = edited.status,
                )
            pendingSyncDao.updatePayload(pending.id, adapter.toJson(amended))
        }

        private fun fromServerStatus(status: String): String =
            com.aistudio.clinicsystem.utils.ServerStatusMapper.fromServer(status)

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

        /**
         * TASK-3: lab results are fetched into the DEDICATED lab_results
         * table (LabResultEntity). They are NEVER mapped into
         * medical_records — the previous testName→diagnosis /
         * resultValue→prescription / notes→doctorName mapping displayed a
         * lab test as a diagnosis, a result as a prescription and an
         * arbitrary note as a doctor's name, which is clinically wrong.
         */
        override suspend fun fetchLabResultsFromServer(
            token: String?,
            phone: String,
        ): List<LabResultEntity> {
            addSyncLog("🛰️ GET /api/v1/mobile/lab/results (lab_results table)", "CLOUD_SYNC_SIMULATOR")
            try {
                val response = mobileApiService.getLabResults()
                if (response.isSuccessful && response.body() != null) {
                    val labResults = response.body()!!
                    addSyncLog("✓ Импортировано ${labResults.size} лабораторных результатов.", "CLOUD_SYNC_SIMULATOR")
                    val entities =
                        labResults.map { dto ->
                            LabResultEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                serverId = dto.id,
                                patientPhone = phone,
                                testName = dto.testName,
                                result = dto.resultValue,
                                unit = dto.unit,
                                referenceRange = dto.referenceRange,
                                status = dto.status,
                                performedAt = dto.resultDate,
                                doctorName = dto.notes,
                            )
                        }
                    database.withTransaction {
                        for (entity in entities) {
                            if (labResultDao.getLabResultByServerId(entity.serverId ?: -1) == null) {
                                labResultDao.insertAll(listOf(entity))
                            }
                        }
                    }
                    return entities
                } else {
                    addSyncLog("⚠️ Лабораторные результаты: сервер вернул код ${response.code()}.", "CLOUD_SYNC_SIMULATOR")
                }
            } catch (e: Exception) {
                addSyncLog("⏳ Сервер временно недоступен: (${e.localizedMessage}).", "CLOUD_SYNC_SIMULATOR")
            }
            return emptyList()
        }

        /**
         * TASK-3: staff clinical notes anchored to a real visit through the
         * visit-based EMR v2 (POST /api/v1/emr/{visit_id}).
         *
         * Behavior:
         *   1. The note is ALWAYS stored locally first (a local draft —
         *      serverId == null marks it as a draft in the UI).
         *   2. Only roles allowed by [com.aistudio.clinicsystem.domain.model.EmrAccessPolicy]
         *      attempt the EMR save; a registrar's note stays a local draft.
         *   3. The visit is resolved structurally (GET /visits?patient_id=…);
         *      the current EMR row_version is fetched to satisfy optimistic
         *      locking; is_draft=true — signing NEVER happens automatically.
         *   4. 409 CONFLICT preserves the local draft and surfaces the
         *      conflict — a conflict never destroys work.
         *
         * @param actorRole backend role of the signed-in staff user.
         */
        override suspend fun saveMedicalRecordWithEmr(
            token: String?,
            patientPhone: String,
            doctorName: String,
            diagnosis: String,
            prescription: String,
            recommendations: String,
            actorRole: String?,
        ): com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome {
            // 1. Local draft first — the note is never lost.
            val visitDate =
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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

            // 2. Role gate — Registrar/Lab/Patient notes stay local drafts.
            if (!com.aistudio.clinicsystem.domain.model.EmrAccessPolicy.canAttemptEmr(actorRole)) {
                addSyncLog(
                    "📝 Запись сохранена как локальный черновик: роль '$actorRole' не имеет прав записи EMR v2.",
                    "CLOUD_SYNC_SIMULATOR",
                )
                return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.LocalDraft(
                    savedRecord,
                    "роль '$actorRole' не имеет прав записи EMR",
                )
            }

            // 3. Resolve the patient on the server, then their visit.
            val patientId = resolvePatientIdByPhone(patientPhone)
            if (patientId == null) {
                return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.LocalDraft(
                    savedRecord,
                    "пациент не найден на сервере — визит не определён",
                )
            }

            try {
                val visitsResponse = legacyApiService.getVisitsForPatient(patientId = patientId, limit = 20)
                val visit =
                    if (visitsResponse.isSuccessful) {
                        visitsResponse.body()
                            ?.firstOrNull()
                            ?: return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.LocalDraft(
                                savedRecord,
                                "у пациента нет визита — черновик не привязан к визиту",
                            )
                    } else {
                        return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.LocalDraft(
                            savedRecord,
                            "визит недоступен (HTTP ${visitsResponse.code()})",
                        )
                    }

                // 4. Current EMR + row_version for optimistic locking.
                val existingEmrResponse = legacyApiService.getEmrForVisit(visit.id)
                var rowVersion = 0
                var existingData: MutableMap<String, Any?>? = null
                if (existingEmrResponse.isSuccessful) {
                    val existing = existingEmrResponse.body()
                    if (existing != null) {
                        rowVersion = existing.rowVersion
                        @Suppress("UNCHECKED_CAST")
                        existingData =
                            (existing.data as? Map<String, Any?>)?.let { LinkedHashMap(it) }
                    }
                }

                // Extend the existing clinical data (or start a flat payload) —
                // other clients' nested keys are preserved as-is.
                val data =
                    existingData ?: LinkedHashMap<String, Any?>().also {
                        it["doctor_name"] = doctorName
                    }
                data["diagnosis"] = diagnosis
                data["prescription"] = prescription
                data["recommendations"] = recommendations
                data["doctor_name"] = doctorName
                data["source"] = "android_mobile"

                val saveResponse =
                    legacyApiService.saveEmrForVisit(
                        visitId = visit.id,
                        payload =
                            com.aistudio.clinicsystem.data.api.EmrSaveRequest(
                                data = data,
                                rowVersion = rowVersion,
                                clientSessionId = java.util.UUID.randomUUID().toString(),
                                isDraft = true, // signing is an explicit separate action
                            ),
                    )

                if (saveResponse.isSuccessful && saveResponse.body() != null) {
                    val emr = saveResponse.body()!!
                    val persisted =
                        savedRecord.copy(
                            serverId = emr.id,
                            visitDate = visitDate,
                        )
                    medicalRecordDao.updateRecord(persisted)
                    addSyncLog(
                        "🟢 EMR v2: запись сохранена в визите #${emr.visitId} (EMR #${emr.id}, draft=true, без подписания).",
                        "CLOUD_SYNC_SIMULATOR",
                    )
                    return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.Confirmed(
                        persisted,
                        emr.id,
                        emr.visitId,
                    )
                }

                val code = saveResponse.code()
                if (code == 409) {
                    // CONFLICT — never destroy the draft.
                    addSyncLog(
                        "⚠️ EMR v2: конфликт версии (409) для визита #${visit.id}. Локальный черновик сохранён; обновите данные и повторите.",
                        "SYSTEM_SYNC",
                    )
                    return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.Conflict(
                        savedRecord,
                        null,
                        "конфликт версии EMR (409): на сервере более новая версия",
                    )
                }
                return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.Rejected(
                    savedRecord,
                    code,
                    "сервер отклонил сохранение (HTTP $code)",
                )
            } catch (e: Exception) {
                return com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.LocalDraft(
                    savedRecord,
                    "сервер недоступен: ${e.localizedMessage ?: e.javaClass.simpleName}",
                )
            }
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

                        // TASK-5: full reconciliation for EVERY row — all
                        // changed server fields (date/time/doctor/specialty/
                        // status) are merged; statuses are normalized; a time
                        // transfer without a status change is no longer
                        // dropped (the old loop only updated on status diff).
                        database.withTransaction {
                            for (dto in serverList) {
                                reconcileUpcomingAppointment(dto, patientPhone)
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
                        var anyQueueFetchSucceeded = false
                        for (specialist in specialists) {
                            val statusResponse =
                                try {
                                    legacyApiService.getQueueStatus(specialist.id)
                                } catch (e: Exception) {
                                    addSyncLog("⚠️ Queue status #${specialist.id}: ${e.message}", "CLOUD_SYNC_SIMULATOR")
                                    null
                                }
                            if (statusResponse == null || !statusResponse.isSuccessful) {
                                statusResponse?.let {
                                    addSyncLog("⚠️ Queue status #${specialist.id}: HTTP ${it.code()}.", "CLOUD_SYNC_SIMULATOR")
                                }
                                continue
                            }
                            anyQueueFetchSucceeded = true
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
                        // CODEX-P2-FIX (PR #147): a successful-but-EMPTY result
                        // is a valid state (last patient left). Only transport
                        // failures keep the previous cache; whenever at least one
                        // specialist status request succeeded we REPLACE the
                        // whole cache — including with an empty list — so stale
                        // entries never stay actionable in the staff console.
                        if (anyQueueFetchSucceeded) {
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
                        // TASK-5: paginated fetch — the old single call with
                        // limit=200 silently hid every appointment beyond the
                        // first 200. Pages of 200 are pulled until a short
                        // page arrives (hard cap prevents runaway loops).
                        val serverList = mutableListOf<com.aistudio.clinicsystem.data.api.StaffAppointmentDto>()
                        val pageSize = 200
                        var skip = 0
                        var fetchFailed = false
                        while (true) {
                            val pageResponse = legacyApiService.getAppointments(limit = pageSize, skip = skip)
                            if (!pageResponse.isSuccessful || pageResponse.body() == null) {
                                if (skip == 0) fetchFailed = true
                                break
                            }
                            val page = pageResponse.body()!!
                            serverList.addAll(page)
                            if (page.size < pageSize || serverList.size >= 5000) break
                            skip += pageSize
                        }
                        if (!fetchFailed) {
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
                            addSyncLog("⚠️ Сервер недоступен: не удалось загрузить первую страницу приёмов.", "CLOUD_SYNC_SIMULATOR")
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
                // CODEX-P1-FIX (PR #147): the staff endpoint returns lowercase
                // backend statuses ("scheduled"/"confirmed"/…) while the staff
                // UI only recognizes client statuses ("PENDING"/"APPROVED"/…).
                // Normalize on INSERT — otherwise freshly synced rows render
                // as an unknown state and lose the approve/complete actions.
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
                        status = fromServerStatus(appDto.status),
                        reason = "",
                        notes = appDto.notes ?: "",
                        clinicId = "clinic_base",
                        updatedAt = updatedAtMs,
                        version = 1,
                    )
                appointmentDao.insertAppointment(entity)
                return
            }

            // TASK-5: stale-write guard covers ALL unconfirmed local writes —
            // status changes (UPDATE_STATUS) AND full edits (UPDATE_APPOINTMENT)
            // — so a queued draft is never overwritten by an older server
            // snapshot. Payload prefixes identify the target row.
            val pendingForThis =
                pendingSyncDao.getAllPendingSyncs().any { row ->
                    val live = row.status == "PENDING" || row.status == "PROCESSING" || row.status == "FAILED"
                    live && (
                        (
                            row.type == "UPDATE_STATUS" &&
                                row.payload.startsWith("${appDto.id}|")
                        ) ||
                            (
                                row.type == "UPDATE_APPOINTMENT" &&
                                    row.payload.contains("\"server_id\":${appDto.id}")
                            )
                    )
                }
            if (pendingForThis) {
                addSyncLog(
                    "🛡️ Stale-write guard: сохраняем локальную запись #${existing.id} (serverId=${appDto.id}) — есть неотправленная локальная правка.",
                    "SYSTEM_SYNC",
                )
                return
            }

            // Server is the source of truth for shared fields — overwrite local.
            // CODEX-P1-FIX (PR #147): normalize the server status on UPDATE as
            // well — see the insert comment above.
            val merged =
                existing.copy(
                    patientName = appDto.patientName ?: existing.patientName,
                    doctorName = doctorName,
                    specialty = specialty.ifBlank { existing.specialty },
                    date = appDto.appointmentDate,
                    time = appDto.appointmentTime ?: existing.time,
                    status = fromServerStatus(appDto.status),
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
            // CODEX-P1-FIX (PR #147): Retrofit throws on transport errors
            // before a Response is produced. The ViewModel calls this from an
            // unguarded coroutine, so the exception reached the uncaught-
            // exception handler. Catch and report failure — the cached state
            // stays intact.
            val response =
                try {
                    when (newStatus) {
                        "CALLED" -> legacyApiService.callQueueEntry(snapshotId)
                        "IN_PROGRESS" -> legacyApiService.startQueueVisit(snapshotId)
                        "COMPLETED" -> legacyApiService.completeQueueVisit(snapshotId)
                        else -> return false
                    }
                } catch (e: Exception) {
                    addSyncLog("⚠️ Сеть недоступна: статус очереди #$snapshotId не изменён (${e.message}).", "CLOUD_SYNC_SIMULATOR")
                    return false
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
            // CODEX-P1-FIX (PR #147): same transport-error handling as above —
            // a dropped connection must not crash the staff console.
            val response =
                try {
                    legacyApiService.cancelQueueEntry(snapshotId)
                } catch (e: Exception) {
                    addSyncLog("⚠️ Сеть недоступна: пациент #$snapshotId не удалён из очереди (${e.message}).", "CLOUD_SYNC_SIMULATOR")
                    return false
                }
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
         * TASK-3: clinical documentation flow — LOCAL data only. Lab results
         * are NOT fetched/mapped here anymore (they live in lab_results via
         * [observeLabResultsWithSync] / [fetchLabResultsFromServer]).
         * Server-side clinical documentation arrives through EMR v2 saves
         * ([saveMedicalRecordWithEmr]) — a local row with serverId == null
         * is a LOCAL DRAFT by definition.
         */
        fun observeMedicalRecordsWithSync(patientPhone: String): Flow<Resource<List<MedicalRecordEntity>>> =
            networkBoundResource(
                query = {
                    medicalRecordDao.getRecordsByPatientFlow(patientPhone)
                },
                fetch = {
                    // Nothing to fetch from /mobile/lab/results — that data
                    // belongs to lab_results. Keep an empty typed response so
                    // the NBR contract stays intact.
                    retrofit2.Response.success(emptyList<com.aistudio.clinicsystem.data.api.LabResultOut>())
                },
                saveFetchResult = { response ->
                    // TASK-3: no lab→record mapping. Records are created
                    // locally by staff drafts and by EMR v2 saves only.
                    if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                        addSyncLog("ℹ️ NBR: unexpected lab payload ignored (TASK-3)", "SYSTEM_SYNC")
                    }
                },
                shouldFetch = { _ ->
                    false // local documentation only
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

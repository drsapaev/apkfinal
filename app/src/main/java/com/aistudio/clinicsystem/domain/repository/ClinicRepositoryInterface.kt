package com.aistudio.clinicsystem.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Stage 5.3 (C-9 fix): ClinicRepositoryInterface — the domain-layer
 * contract for clinic operations.
 *
 * The previous version had method signatures that didn't match the real
 * ClinicRepository (used Int IDs, different parameter lists). Stage 5
 * rewrites the interface to match the actual repo methods, but with
 * domain types where possible.
 *
 * ViewModels and UseCases depend on this interface, NOT on the concrete
 * ClinicRepository. This allows easy mocking in tests and future
 * swapping of the data layer implementation.
 *
 * Hilt provides the binding: AppModule binds ClinicRepository (which
 * implements this interface) as a @Singleton.
 */
interface ClinicRepositoryInterface {
    // ── User Operations ──
    suspend fun getUserByPhone(phone: String): com.aistudio.clinicsystem.data.db.UserEntity?

    suspend fun insertUser(user: com.aistudio.clinicsystem.data.db.UserEntity): Long

    suspend fun updateUser(user: com.aistudio.clinicsystem.data.db.UserEntity)

    // ── Appointment Operations ──
    suspend fun getAppointmentById(id: String): com.aistudio.clinicsystem.data.db.AppointmentEntity?

    suspend fun insertAppointment(
        appointment: com.aistudio.clinicsystem.data.db.AppointmentEntity,
    ): com.aistudio.clinicsystem.data.db.AppointmentEntity

    suspend fun updateAppointment(appointment: com.aistudio.clinicsystem.data.db.AppointmentEntity)

    suspend fun deleteAppointment(id: String)

    // ── Appointment Sync Operations ──
    /**
     * TASK-1: patient self-booking. Books the signed-in patient for
     * themselves via the mobile contract; retried exclusively on the
     * mobile route. `patientId`/`doctorId` are structured identifiers.
     */
    suspend fun createAppointmentOnServerAndLocal(
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
    ): com.aistudio.clinicsystem.data.db.AppointmentEntity

    /**
     * TASK-1: staff booking for a chosen patient. Books the SPECIFIC patient
     * via the staff endpoint; retried exclusively on the staff route.
     */
    suspend fun createAppointmentForPatientOnServerAndLocal(
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
    ): com.aistudio.clinicsystem.data.db.AppointmentEntity

    /**
     * TASK-1: `actorIsPatient` selects the route (mobile cancel vs staff PUT)
     * and is recorded in the outbox so retries replay the original scenario.
     * TASK-2: returns a [com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome]
     * so the UI can distinguish a server confirmation from a queued draft
     * and from a server rejection.
     */
    suspend fun updateAppointmentStatusOnServerAndLocal(
        token: String?,
        id: String,
        status: String,
        cancelReason: String = "",
        actorIsPatient: Boolean = false,
    ): com.aistudio.clinicsystem.domain.model.AppointmentWriteOutcome?

    suspend fun retryUnsyncedWrites(token: String?): Boolean

    suspend fun syncAllAppointmentsFromServer(token: String?): Boolean

    // ── Medical Record Operations ──
    suspend fun getMedicalRecordById(id: String): com.aistudio.clinicsystem.data.db.MedicalRecordEntity?

    suspend fun insertMedicalRecord(
        record: com.aistudio.clinicsystem.data.db.MedicalRecordEntity,
    ): com.aistudio.clinicsystem.data.db.MedicalRecordEntity

    suspend fun createMedicalRecordOnServerAndLocal(
        token: String?,
        patientPhone: String,
        doctorName: String,
        diagnosis: String,
        prescription: String,
        recommendations: String,
    ): com.aistudio.clinicsystem.data.db.MedicalRecordEntity

    /**
     * TASK-3: staff clinical note anchored to a real visit via EMR v2.
     * Roles without EMR write rights get a LocalDraft outcome instead of a
     * fake "saved medical record". 409 conflicts never destroy the draft.
     */
    suspend fun saveMedicalRecordWithEmr(
        token: String?,
        patientPhone: String,
        doctorName: String,
        diagnosis: String,
        prescription: String,
        recommendations: String,
        actorRole: String? = null,
    ): com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome

    /**
     * TASK-3: lab results land in the dedicated lab_results table — they are
     * never mapped into medical records (no testName→diagnosis substitution).
     */
    suspend fun fetchLabResultsFromServer(
        token: String?,
        phone: String,
    ): List<com.aistudio.clinicsystem.data.db.LabResultEntity>

    // ── Queue Operations ──
    // M-CONTRACT-FIX: the backend removed POST /api/v1/queue/register —
    // implementations throw UnsupportedOperationException and callers fall
    // back to the local queue snapshot.
    suspend fun registerInQueue(appointmentId: String)

    // ── Outbox Operations ──
    suspend fun dismissPendingSync(sync: com.aistudio.clinicsystem.data.db.PendingSyncEntity)

    suspend fun clearSensitiveDataForPatient(phone: String)

    suspend fun clearLogs()

    suspend fun addSyncLog(
        logMessage: String,
        direction: String,
    )

    // ── Flows (observable data) ──
    val allUsers: Flow<List<com.aistudio.clinicsystem.data.db.UserEntity>>
    val allAppointments: Flow<List<com.aistudio.clinicsystem.data.db.AppointmentEntity>>
    val allMedicalRecords: Flow<List<com.aistudio.clinicsystem.data.db.MedicalRecordEntity>>
    val recentLogs: Flow<List<com.aistudio.clinicsystem.data.db.SyncLogEntity>>
    val allQueueSnapshots: Flow<List<com.aistudio.clinicsystem.data.db.QueueSnapshotEntity>>
    val allPendingSyncs: Flow<List<com.aistudio.clinicsystem.data.db.PendingSyncEntity>>
}

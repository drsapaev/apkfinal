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
     */
    suspend fun updateAppointmentStatusOnServerAndLocal(
        token: String?,
        id: String,
        status: String,
        cancelReason: String = "",
        actorIsPatient: Boolean = false,
    ): com.aistudio.clinicsystem.data.db.AppointmentEntity?

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

    suspend fun fetchMedicalRecordsFromServer(
        token: String?,
        phone: String,
        onNewRecordAction: (com.aistudio.clinicsystem.data.db.MedicalRecordEntity) -> Unit = {},
    ): List<com.aistudio.clinicsystem.data.db.MedicalRecordEntity>

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

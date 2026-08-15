package com.aistudio.clinicsystem.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stage 3.1: Entities use UUID primary keys (already migrated in v5→v6).
 * Stage 3.1 adds:
 *   - `@Index` on FK columns and frequently-queried columns (H-8 fix)
 *   - `version: Int` on mutable entities (H-5 fix — conflict resolution)
 *   - `etag: String?` on mutable entities (optional, for server cache validation)
 *
 * The Migration 6→7 adds the new indices and the `etag` column.
 */

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["phone"], unique = true),
        Index(value = ["role"]),
        Index(value = ["clinicId"]),
    ],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phone: String, // Unique phone number (e.g. +79998887766)
    val fullName: String,
    val role: String, // "PATIENT" or "STAFF"
    val jobTitle: String = "", // "DOCTOR", "REGISTRAR", etc.
    val clinicId: String = "clinic_base",
    val dateOfBirth: String = "1995-05-15",
    val biometricEnabled: Boolean = false,
    val telegramChatId: String? = null,
    val registeredTimestamp: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "appointments",
    indices = [
        Index(value = ["patientPhone"]),
        Index(value = ["serverId"]),
        Index(value = ["clientRequestId"]),
        Index(value = ["status"]),
        Index(value = ["clinicId"]),
        Index(value = ["date", "time"]),
    ],
)
data class AppointmentEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val serverId: Int? = null, // backend-assigned ID, null until synced
    val patientPhone: String,
    val patientName: String,
    val doctorName: String,
    val specialty: String,
    val date: String, // e.g. "2026-06-10"
    val time: String, // e.g. "14:00"
    val status: String, // "PENDING", "APPROVED", "COMPLETED", "CANCELLED"
    val reason: String,
    val clinicId: String = "clinic_base",
    val notes: String = "", // Written by doctor/staff
    val updatedAt: Long = System.currentTimeMillis(),
    val clientRequestId: String? = null,
    val version: Int = 1, // increments on every local mutation; used for conflict resolution
    val etag: String? = null, // server-assigned ETag (optional, for cache validation)
)

@Entity(
    tableName = "queue_snapshots",
    indices = [
        Index(value = ["appointmentId"]),
        Index(value = ["clinicId"]),
        Index(value = ["status"]),
    ],
)
data class QueueSnapshotEntity(
    @PrimaryKey val id: Int,
    val patientName: String,
    val appointmentId: Int,
    val position: Int,
    val clinicId: String = "clinic_base",
    val status: String, // "WAITING", "IN_PROGRESS", "COMPLETED"
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "pending_syncs",
    indices = [
        Index(value = ["status"]),
        Index(value = ["status", "nextRetryAt"]),
        Index(value = ["clientRequestId"]),
        Index(value = ["clinicId"]),
    ],
)
data class PendingSyncEntity(
    @PrimaryKey val id: String = com.aistudio.clinicsystem.data.outbox.generateOutboxId(),
    val type: String, // OutboxOperation.name — see Stage 3.8
    val payload: String, // JSON payload representing the synchronized dto
    val clientRequestId: String,
    val clinicId: String = "clinic_base",
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    // Outbox fields (M3B.3)
    val status: String = "PENDING", // OutboxStatus.name
    val lastError: String? = null,
    val nextRetryAt: Long? = null, // epoch millis, null = retry immediately
    val updatedAt: Long = System.currentTimeMillis(),
    // Stage 3.6: HTTP status code of the last failure — used to distinguish
    // 4xx (DEAD_LETTER) from 5xx (retry). Null if no failure yet.
    val lastHttpCode: Int? = null,
)

@Entity(
    tableName = "medical_records",
    indices = [
        Index(value = ["patientPhone"]),
        Index(value = ["serverId"]),
        Index(value = ["clinicId"]),
        Index(value = ["visitDate"]),
    ],
)
data class MedicalRecordEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val serverId: Int? = null, // backend-assigned ID, null until synced
    val patientPhone: String,
    val doctorName: String,
    val diagnosis: String,
    val prescription: String,
    val visitDate: String, // "2026-06-06"
    val clinicId: String = "clinic_base",
    val recommendations: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    // Stage 3.4: versioning for conflict resolution
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val etag: String? = null,
)

@Entity(tableName = "sync_logs", indices = [Index(value = ["timestamp"])])
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val logMessage: String,
    val clinicId: String = "clinic_base",
    val direction: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * LabResultEntity — stores lab results from GET /api/v1/mobile/lab/results.
 *
 * Previously, lab results were incorrectly mapped into MedicalRecordEntity
 * (testName→diagnosis, result→prescription). This entity stores the fields
 * as-is, preserving their semantic meaning.
 */
@Entity(
    tableName = "lab_results",
    indices = [
        Index(value = ["patientPhone"]),
        Index(value = ["serverId"]),
        Index(value = ["performedAt"]),
    ],
)
data class LabResultEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val serverId: Int? = null,
    val patientPhone: String,
    val testName: String,
    val result: String? = null,
    val unit: String? = null,
    val referenceRange: String? = null,
    val status: String,
    val performedAt: String? = null,
    val doctorName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * P-04: DoctorEntity — справочник врачей, синхронизируемый с backend.
 *
 * Заменяет хардкод списка врачей (3 шт.) в PatientScreen.kt.
 * TTL-кеш: обновление раз в сутки через NetworkBoundResource.
 * UUID primary key, serverId для совместимости с backend (Int).
 */
@Entity(
    tableName = "doctors",
    indices = [
        Index(value = ["serverId"], unique = true),
        Index(value = ["specialty"]),
    ],
)
data class DoctorEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val serverId: Int? = null, // backend-assigned ID, null until synced
    val fullName: String,
    val specialty: String,
    val phone: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val isActive: Boolean = true,
    val clinicId: String = "clinic_base",
    val updatedAt: Long = System.currentTimeMillis(),
)

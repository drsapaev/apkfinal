package com.aistudio.clinicsystem.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
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
    val registeredTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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
    val version: Int = 1
)

@Entity(tableName = "queue_snapshots")
data class QueueSnapshotEntity(
    @PrimaryKey val id: Int,
    val patientName: String,
    val appointmentId: Int,
    val position: Int,
    val clinicId: String = "clinic_base",
    val status: String, // "WAITING", "IN_PROGRESS", "COMPLETED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_syncs")
data class PendingSyncEntity(
    @PrimaryKey val id: String = com.aistudio.clinicsystem.data.outbox.generateOutboxId(),
    val type: String, // "CREATE_APPOINTMENT", "UPDATE_APPOINTMENT_STATUS", "CREATE_MEDICAL_RECORD"
    val payload: String, // JSON payload representing the synchronized dto
    val clientRequestId: String,
    val clinicId: String = "clinic_base",
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    // M3B.3: Outbox fields
    val status: String = "PENDING", // OutboxStatus.name
    val lastError: String? = null,
    val nextRetryAt: Long? = null, // epoch millis, null = retry immediately
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "medical_records")
data class MedicalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientPhone: String,
    val doctorName: String,
    val diagnosis: String,
    val prescription: String,
    val visitDate: String, // "2026-06-06"
    val clinicId: String = "clinic_base",
    val recommendations: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val logMessage: String,
    val clinicId: String = "clinic_base",
    val direction: String, // "PATIENT_TO_STAFF", "STAFF_TO_PATIENT", "CLOUD_SYNC_SIMULATOR"
    val timestamp: Long = System.currentTimeMillis()
)

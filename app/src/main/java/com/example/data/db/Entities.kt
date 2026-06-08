package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phone: String, // Unique phone number (e.g. +79998887766)
    val fullName: String,
    val role: String, // "PATIENT" or "STAFF"
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
    val notes: String = "", // Written by doctor/staff
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
    val recommendations: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val logMessage: String,
    val direction: String, // "PATIENT_TO_STAFF", "STAFF_TO_PATIENT", "CLOUD_SYNC_SIMULATOR"
    val timestamp: Long = System.currentTimeMillis()
)

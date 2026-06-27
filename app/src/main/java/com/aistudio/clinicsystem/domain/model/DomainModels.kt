package com.aistudio.clinicsystem.domain.model

import com.aistudio.clinicsystem.data.model.UserRole

data class User(
    val id: String,
    val phone: String,
    val fullName: String,
    val role: UserRole,
    val dateOfBirth: String? = null,
    val biometricEnabled: Boolean = false,
    val telegramChatId: String? = null,
    val clinicId: String? = null
)

data class Appointment(
    val id: String,
    val patientPhone: String,
    val patientName: String,
    val doctorName: String,
    val specialty: String,
    val date: String,
    val time: String,
    val status: AppointmentStatus,
    val reason: String,
    val notes: String = "",
    val clinicId: String? = null,
    val updatedAt: Long = 0
)

enum class AppointmentStatus {
    PENDING, APPROVED, COMPLETED, CANCELLED;

    companion object {
        fun fromString(value: String?): AppointmentStatus =
            when (value?.uppercase()) {
                "PENDING" -> PENDING
                "APPROVED" -> APPROVED
                "COMPLETED" -> COMPLETED
                "CANCELLED" -> CANCELLED
                else -> PENDING
            }
    }

    val isActive: Boolean get() = this == PENDING || this == APPROVED
    val isFinished: Boolean get() = this == COMPLETED || this == CANCELLED
}

data class MedicalRecord(
    val id: String,
    val patientPhone: String,
    val doctorName: String,
    val diagnosis: String,
    val prescription: String,
    val visitDate: String,
    val recommendations: String = "",
    val timestamp: Long = 0
)

data class QueuePosition(
    val position: Int,
    val queueNumber: Int? = null,
    val estimatedWaitMinutes: Int? = null,
    val status: QueueStatus,
    val queueId: Int? = null
)

enum class QueueStatus {
    WAITING, IN_PROGRESS, COMPLETED, OPEN;

    companion object {
        fun fromString(value: String?): QueueStatus =
            when (value?.uppercase()) {
                "WAITING" -> WAITING
                "IN_PROGRESS" -> IN_PROGRESS
                "COMPLETED" -> COMPLETED
                "OPEN" -> OPEN
                else -> WAITING
            }
    }
}

data class PendingSync(
    val id: String,
    val type: String,
    val payload: String,
    val clientRequestId: String,
    val timestamp: Long,
    val retryCount: Int = 0,
    val status: String = "PENDING",
    val lastError: String? = null,
    val nextRetryAt: Long? = null
)

sealed class LoginResult {
    data class Success(val user: User) : LoginResult()
    data class TwoFactorRequired(val challengeToken: String) : LoginResult()
}

package com.aistudio.clinicsystem.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ============================================
// Authentication Models
// ============================================

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username")
    val username: String,
    @Json(name = "password")
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "access_token")
    val accessToken: String,
    @Json(name = "token_type")
    val tokenType: String,
    @Json(name = "expires_in")
    val expiresIn: Int
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token")
    val refreshToken: String
)

// ============================================
// User Models
// ============================================

@JsonClass(generateAdapter = true)
data class UserResponse(
    @Json(name = "id")
    val id: Int,
    @Json(name = "username")
    val username: String,
    @Json(name = "email")
    val email: String,
    @Json(name = "full_name")
    val fullName: String? = null,
    @Json(name = "role")
    val role: String,
    @Json(name = "is_active")
    val isActive: Boolean = true
)

// ============================================
// Patient Models
// ============================================

@JsonClass(generateAdapter = true)
data class PatientRequest(
    @Json(name = "first_name")
    val firstName: String,
    @Json(name = "last_name")
    val lastName: String,
    @Json(name = "phone")
    val phone: String,
    @Json(name = "birth_date")
    val birthDate: String? = null,
    @Json(name = "gender")
    val gender: String? = null
)

@JsonClass(generateAdapter = true)
data class PatientResponse(
    @Json(name = "id")
    val id: Int,
    @Json(name = "first_name")
    val firstName: String,
    @Json(name = "last_name")
    val lastName: String,
    @Json(name = "phone")
    val phone: String,
    @Json(name = "birth_date")
    val birthDate: String? = null,
    @Json(name = "gender")
    val gender: String? = null,
    @Json(name = "created_at")
    val createdAt: String? = null
) {
    fun getFullName(): String = "$firstName $lastName"
}

// ============================================
// Appointment Models
// ============================================

@JsonClass(generateAdapter = true)
data class AppointmentResponse(
    @Json(name = "id")
    val id: Int,
    @Json(name = "appointment_date")
    val appointmentDate: String,
    @Json(name = "appointment_time")
    val appointmentTime: String,
    @Json(name = "doctor_name")
    val doctorName: String,
    @Json(name = "department")
    val department: String,
    @Json(name = "department_name")
    val departmentName: String,
    @Json(name = "status")
    val status: String,
    @Json(name = "can_cancel")
    val canCancel: Boolean = false,
    @Json(name = "can_reschedule")
    val canReschedule: Boolean = false,
    @Json(name = "hours_until_appointment")
    val hoursUntilAppointment: Double? = null
)

@JsonClass(generateAdapter = true)
data class RescheduleAppointmentRequest(
    @Json(name = "new_date")
    val newDate: String,
    @Json(name = "new_time")
    val newTime: String
)

@JsonClass(generateAdapter = true)
data class AppointmentSlot(
    @Json(name = "date")
    val date: String,
    @Json(name = "time")
    val time: String,
    @Json(name = "available")
    val available: Boolean
)

// ============================================
// Queue Models
// ============================================

@JsonClass(generateAdapter = true)
data class QueueJoinRequest(
    @Json(name = "patient_phone")
    val patientPhone: String,
    @Json(name = "patient_name")
    val patientName: String,
    @Json(name = "department")
    val department: String,
    @Json(name = "priority")
    val priority: String = "normal"
)

@JsonClass(generateAdapter = true)
data class QueueResponse(
    @Json(name = "id")
    val id: Int,
    @Json(name = "patient_name")
    val patientName: String,
    @Json(name = "position")
    val position: Int,
    @Json(name = "department")
    val department: String,
    @Json(name = "status")
    val status: String,
    @Json(name = "created_at")
    val createdAt: String? = null
)

// ============================================
// Department & Service Models
// ============================================

@JsonClass(generateAdapter = true)
data class DepartmentResponse(
    @Json(name = "id")
    val id: Int,
    @Json(name = "name")
    val name: String,
    @Json(name = "description")
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class ServiceResponse(
    @Json(name = "id")
    val id: Int,
    @Json(name = "name")
    val name: String,
    @Json(name = "department_id")
    val departmentId: Int,
    @Json(name = "price")
    val price: Double? = null,
    @Json(name = "description")
    val description: String? = null
)

// ============================================
// Doctor Models
// ============================================

@JsonClass(generateAdapter = true)
data class DoctorResponse(
    @Json(name = "id")
    val id: Int,
    @Json(name = "full_name")
    val fullName: String,
    @Json(name = "specialization")
    val specialization: String,
    @Json(name = "department_id")
    val departmentId: Int,
    @Json(name = "phone")
    val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class DoctorSchedule(
    @Json(name = "doctor_id")
    val doctorId: Int,
    @Json(name = "date")
    val date: String,
    @Json(name = "start_time")
    val startTime: String,
    @Json(name = "end_time")
    val endTime: String,
    @Json(name = "available_slots")
    val availableSlots: Int
)

// ============================================
// Lab Results Models
// ============================================

@JsonClass(generateAdapter = true)
data class LabResult(
    @Json(name = "id")
    val id: Int,
    @Json(name = "test_name")
    val testName: String,
    @Json(name = "result")
    val result: String,
    @Json(name = "unit")
    val unit: String? = null,
    @Json(name = "reference_range")
    val referenceRange: String? = null,
    @Json(name = "test_date")
    val testDate: String,
    @Json(name = "status")
    val status: String
)

// ============================================
// Error/Exception Models
// ============================================

@JsonClass(generateAdapter = true)
data class ErrorResponse(
    @Json(name = "detail")
    val detail: String? = null,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "error")
    val error: String? = null
) {
    fun getErrorMessage(): String = detail ?: message ?: error ?: "Unknown error"
}

// ============================================
// Generic Response Models
// ============================================

@JsonClass(generateAdapter = true)
data class GenericResponse<T>(
    @Json(name = "success")
    val success: Boolean,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "data")
    val data: T? = null
)

@JsonClass(generateAdapter = true)
data class PaginatedResponse<T>(
    @Json(name = "items")
    val items: List<T>,
    @Json(name = "total")
    val total: Int,
    @Json(name = "skip")
    val skip: Int,
    @Json(name = "limit")
    val limit: Int
)

package com.aistudio.clinicsystem.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

/**
 * ApiService defines the Retrofit networking endpoints matching the FastAPI backend in the 'final' repository.
 * It includes authorization requests, profile matching, appointments, medical records, and queue registries.
 */
interface ApiService {

    // --- Authentication & User Cabinet ---

    @POST("api/v1/authentication/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("api/v1/authentication/refresh")
    suspend fun refresh(): Response<AuthResponse>

    @POST("api/v1/authentication/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/v1/users/me")
    suspend fun getProfile(): Response<UserDto>

    @POST("api/v1/users/telegram/link")
    suspend fun linkTelegram(
        @Query("chat_id") chatId: String
    ): Response<Unit>

    @POST("api/v1/users/telegram/unlink")
    suspend fun unlinkTelegram(): Response<Unit>


    // --- Appointments ---

    @GET("api/v1/appointments")
    suspend fun getAppointments(
        @Query("phone") phone: String? = null,
        @Query("since") since: Long? = null,
        @Query("clinic_id") clinicId: String? = null
    ): Response<List<AppointmentDto>>

    @POST("api/v1/appointments")
    suspend fun createAppointment(
        @Body appointment: AppointmentDto
    ): Response<AppointmentDto>

    @PUT("api/v1/appointments/{id}/status")
    suspend fun updateAppointmentStatus(
        @Path("id") id: Int,
        @Query("status") status: String,
        @Query("notes") notes: String? = null
    ): Response<AppointmentDto>


    // --- Medical Records / Reports ---

    @GET("api/v1/patients/records/all")
    suspend fun getAllMedicalRecords(
        @Query("since") since: Long? = null,
        @Query("clinic_id") clinicId: String? = null
    ): Response<List<MedicalRecordDto>>

    @GET("api/v1/patients/records/{phone}")
    suspend fun getMedicalRecordsForPatient(
        @Path("phone") phone: String
    ): Response<List<MedicalRecordDto>>

    @POST("api/v1/patients/records")
    suspend fun createMedicalRecord(
        @Body record: MedicalRecordDto
    ): Response<MedicalRecordDto>


    // --- Queues (Active Waiting Rooms) ---

    @GET("api/v1/queue")
    suspend fun getQueue(): Response<List<QueueDto>>

    @POST("api/v1/queue/register")
    suspend fun registerInQueue(
        @Query("appointment_id") appointmentId: Int
    ): Response<QueueDto>
}

// --- Moshi-Annotated DTOs (Data Transfer Objects) mapping matching JSON payloads ---
//
// M1/E3.1: LoginRequest and AuthResponse were moved to MobileApiService.kt
// (the new LoginRequest supports device_fingerprint + remember_me, and the
// new LoginResponse supports requires_2fa + pending_2fa_token + refresh_token).
// The old AuthService.login() returned AuthResponse which had only access_token.
// Use MobileApiService.login() + LoginResponse for all new auth code.
//
// AuthResponse is kept here ONLY because the legacy ApiService.login() method
// below still references it. Do NOT use it in new code — use LoginResponse.
// The DTOs below (UserDto, AppointmentDto, MedicalRecordDto, QueueDto) are
// still used by ClinicRepository for the legacy sync flow. They will be
// migrated to MobileApiService DTOs in M2.

@Deprecated("Use LoginResponse from MobileApiService.kt instead. Kept only for legacy ApiService.login().")
@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Int? = null
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: Int,
    @Json(name = "phone") val phone: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "role") val role: String, // "PATIENT" or "STAFF"
    @Json(name = "date_of_birth") val dateOfBirth: String?,
    @Json(name = "biometric_enabled") val biometricEnabled: Boolean,
    @Json(name = "telegram_chat_id") val telegramChatId: String?,
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base"
)

@JsonClass(generateAdapter = true)
data class AppointmentDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "patient_phone") val patientPhone: String,
    @Json(name = "patient_name") val patientName: String,
    @Json(name = "doctor_name") val doctorName: String,
    @Json(name = "specialty") val specialty: String,
    @Json(name = "date") val date: String,
    @Json(name = "time") val time: String,
    @Json(name = "status") val status: String, // "PENDING", "APPROVED", "COMPLETED", "CANCELLED"
    @Json(name = "reason") val reason: String,
    @Json(name = "notes") val notes: String?,
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base"
)

@JsonClass(generateAdapter = true)
data class MedicalRecordDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "patient_phone") val patientPhone: String,
    @Json(name = "doctor_name") val doctorName: String,
    @Json(name = "diagnosis") val diagnosis: String,
    @Json(name = "prescription") val prescription: String,
    @Json(name = "visit_date") val visitDate: String,
    @Json(name = "recommendations") val recommendations: String?,
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base"
)

@JsonClass(generateAdapter = true)
data class QueueDto(
    @Json(name = "id") val id: Int,
    @Json(name = "patient_name") val patientName: String,
    @Json(name = "appointment_id") val appointmentId: Int,
    @Json(name = "position") val position: Int,
    @Json(name = "status") val status: String, // "WAITING", "IN_PROGRESS", "COMPLETED"
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base"
)

package com.aistudio.clinicsystem.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MobileApiService — Retrofit interface for the backend `/api/v1/mobile/ endpoint family` contract
 * plus the canonical authentication and 2FA flows.
 *
 * M1/E3.1 (Backend Integration): introduced to replace the generic [ApiService]
 * which used ad-hoc paths (`/api/v1/users/me`, `/api/v1/appointments`, etc.).
 * The backend exposes a dedicated mobile surface under `/api/v1/mobile/ endpoint family`
 * (27 endpoints) and a canonical auth surface under `/api/v1/authentication/ endpoint family`.
 *
 * Source of truth: backend `openapi.json` at
 * https://github.com/drsapaev/final/blob/main/backend/openapi.json
 *
 * ─── Auth (canonical JSON flow, NOT the OAuth2 form flow) ───────────────
 *
 *   POST /api/v1/authentication/login
 *     body: LoginRequest(username, password, device_fingerprint?, remember_me?)
 *     200 → LoginResponse(access_token, refresh_token, token_type, expires_in,
 *                         user, requires_2fa?, pending_2fa_token?)
 *     401 → invalid credentials
 *     200 + requires_2fa=true → caller must POST /api/v1/2fa/verify
 *
 *   POST /api/v1/authentication/refresh
 *     body: RefreshTokenRequest(refresh_token)
 *     200 → RefreshTokenResponse(access_token, refresh_token, token_type, expires_in)
 *     401 → refresh token invalid, caller must re-login
 *
 *   POST /api/v1/authentication/logout
 *     body: LogoutRequest(refresh_token)
 *     200 → server invalidates the session; client clears local tokens
 *
 *   GET  /api/v1/authentication/profile
 *     200 → UserProfileResponse
 *
 * ─── 2FA ───────────────────────────────────────────────────────────────
 *
 *   POST /api/v1/2fa/verify
 *     body: TwoFAVerifyRequest(pending_2fa_token, totp_code, remember_device,
 *                              device_fingerprint)
 *     200 → LoginResponse (with real access_token + refresh_token)
 *     401 → wrong code
 *
 *   POST /api/v1/2fa/recovery/request
 *     body: TwoFARecoveryRequest(pending_2fa_token, method)  // "email" | "sms"
 *     200 → {recovery_token, sent: true}
 *
 *   POST /api/v1/2fa/recovery/verify
 *     body: TwoFARecoveryVerifyRequest(recovery_token, code)
 *     200 → LoginResponse
 *
 * ─── Mobile surface (/api/v1/mobile/ endpoint family) ─────────────────────────────────
 *
 * 27 endpoints covering patient-facing features. Note: the OpenAPI spec for
 * many of these declares empty/untyped response bodies (the backend returns
 * JSON but Pydantic models are not declared in the spec). Where the spec is
 * untyped we use `Response<Unit>` or `Response<Map<String, Any?>>` — the
 * concrete parsing will be tightened in M2 when we add proper DTOs.
 */
interface MobileApiService {

    // ═══════════════════════════════════════════════════════════════════
    // Authentication (canonical)
    // ═══════════════════════════════════════════════════════════════════

    @POST("api/v1/authentication/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/v1/authentication/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<RefreshTokenResponse>

    @POST("api/v1/authentication/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @GET("api/v1/authentication/profile")
    suspend fun getProfile(): Response<UserProfileResponse>

    @GET("api/v1/authentication/status")
    suspend fun getAuthStatus(): Response<AuthStatusResponse>

    // ═══════════════════════════════════════════════════════════════════
    // 2FA
    // ═══════════════════════════════════════════════════════════════════

    @POST("api/v1/2fa/verify")
    suspend fun verify2FA(
        @Body request: TwoFAVerifyRequest
    ): Response<LoginResponse>

    @POST("api/v1/2fa/recovery/request")
    suspend fun request2FARecovery(
        @Body request: TwoFARecoveryRequest
    ): Response<TwoFARecoveryResponse>

    @POST("api/v1/2fa/recovery/verify")
    suspend fun verify2FARecovery(
        @Body request: TwoFARecoveryVerifyRequest
    ): Response<LoginResponse>

    @POST("api/v1/2fa/send-code")
    suspend fun send2FACode(
        @Body request: Send2FACodeRequest
    ): Response<Unit>

    // ═══════════════════════════════════════════════════════════════════
    // Mobile: profile
    // ═══════════════════════════════════════════════════════════════════

    @GET("api/v1/mobile/patients/me")
    suspend fun getMyPatientProfile(): Response<PatientProfileOut>

    @PUT("api/v1/mobile/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): Response<Unit>

    @POST("api/v1/mobile/profile/avatar")
    suspend fun uploadAvatar(@Body body: AvatarUploadRequest): Response<Unit>

    // ═══════════════════════════════════════════════════════════════════
    // Mobile: appointments
    // ═══════════════════════════════════════════════════════════════════

    @GET("api/v1/mobile/appointments/upcoming")
    suspend fun getUpcomingAppointments(): Response<List<MobileAppointmentOut>>

    @GET("api/v1/mobile/appointments/{appointment_id}")
    suspend fun getAppointment(
        @Path("appointment_id") appointmentId: String
    ): Response<MobileAppointmentOut>

    @POST("api/v1/mobile/appointments/book")
    suspend fun bookAppointment(
        @Body request: AppointmentBookRequest
    ): Response<MobileAppointmentOut>

    @POST("api/v1/mobile/appointments/cancel")
    suspend fun cancelAppointment(
        @Body request: AppointmentCancelRequest
    ): Response<Unit>

    @POST("api/v1/mobile/appointments/reschedule")
    suspend fun rescheduleAppointment(
        @Body request: AppointmentRescheduleRequest
    ): Response<MobileAppointmentOut>

    // ═══════════════════════════════════════════════════════════════════
    // Mobile: doctors / services
    // ═══════════════════════════════════════════════════════════════════

    @POST("api/v1/mobile/doctors/search")
    suspend fun searchDoctors(
        @Body request: DoctorSearchRequest
    ): Response<List<DoctorOut>>

    @GET("api/v1/mobile/doctors/{doctor_id}/schedule")
    suspend fun getDoctorSchedule(
        @Path("doctor_id") doctorId: String,
        @Query("date") date: String? = null
    ): Response<DoctorScheduleOut>

    @POST("api/v1/mobile/services/search")
    suspend fun searchServices(
        @Body request: ServiceSearchRequest
    ): Response<List<ServiceOut>>

    @GET("api/v1/mobile/services/categories")
    suspend fun getServiceCategories(): Response<List<ServiceCategoryOut>>

    // ═══════════════════════════════════════════════════════════════════
    // Mobile: queue
    // ═══════════════════════════════════════════════════════════════════

    @GET("api/v1/mobile/queues/status")
    suspend fun getQueueStatus(): Response<QueueStatusOut>

    @GET("api/v1/mobile/queues/my-position")
    suspend fun getMyQueuePosition(): Response<QueuePositionOut>

    // ═══════════════════════════════════════════════════════════════════
    // Mobile: lab results
    // ═══════════════════════════════════════════════════════════════════

    @GET("api/v1/mobile/lab/results")
    suspend fun getLabResults(): Response<List<LabResultOut>>

    // ═══════════════════════════════════════════════════════════════════
    // Mobile: notifications
    // ═══════════════════════════════════════════════════════════════════

    @GET("api/v1/mobile/notifications")
    suspend fun getNotifications(): Response<List<NotificationOut>>

    @POST("api/v1/mobile/notifications/{notification_id}/read")
    suspend fun markNotificationRead(
        @Path("notification_id") notificationId: String
    ): Response<Unit>

    @GET("api/v1/mobile/settings/notifications")
    suspend fun getNotificationSettings(): Response<NotificationSettingsOut>

    @PUT("api/v1/mobile/settings/notifications")
    suspend fun updateNotificationSettings(
        @Body request: NotificationSettingsRequest
    ): Response<Unit>

    // ═══════════════════════════════════════════════════════════════════
    // Mobile: clinic info / feedback / emergency
    // ═══════════════════════════════════════════════════════════════════

    @GET("api/v1/mobile/clinic/info")
    suspend fun getClinicInfo(): Response<ClinicInfoOut>

    @GET("api/v1/mobile/stats")
    suspend fun getQuickStats(): Response<MobileQuickStats>

    @POST("api/v1/mobile/feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): Response<Unit>

    @POST("api/v1/mobile/emergency/contact")
    suspend fun setEmergencyContact(
        @Body request: EmergencyContactRequest
    ): Response<Unit>

    @GET("api/v1/mobile/version")
    suspend fun getApiVersion(): Response<ApiVersionOut>

    @GET("api/v1/mobile/health")
    suspend fun healthCheck(): Response<Unit>

    // P-04: doctor directory endpoints
    @GET("api/v1/mobile/doctors")
    suspend fun getDoctors(
        @Header("If-None-Match") etag: String? = null
    ): Response<List<DoctorDto>>

    @GET("api/v1/mobile/doctors/{id}/slots")
    suspend fun getDoctorTimeSlots(
        @Path("id") doctorId: Int,
        @Query("date") date: String // "2026-06-29"
    ): Response<List<TimeSlotDto>>
}

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Authentication
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
    @Json(name = "device_fingerprint") val deviceFingerprint: String? = null,
    @Json(name = "remember_me") val rememberMe: Boolean? = null
)

/**
 * Login response. When [requires2fa] is true, [pending2faToken] is set and
 * [accessToken]/[refreshToken] are null — the caller must complete the 2FA
 * flow via [MobileApiService.verify2FA] before tokens are issued.
 *
 * Note: the backend's OpenAPI spec marks `user` as `additionalProperties: true`
 * (untyped object). We type it loosely as [Map] here; M2 will tighten this
 * to a proper UserProfile DTO once the backend schema is declared.
 */
@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Int? = null,
    @Json(name = "user") val user: Map<String, Any?>? = null,
    @Json(name = "requires_2fa") val requires2fa: Boolean? = null,
    @Json(name = "pending_2fa_token") val pending2faToken: String? = null
) {
    /** Convenience: is this a "you must complete 2FA" response? */
    val isTwoFactorChallenge: Boolean
        get() = requires2fa == true && !pending2faToken.isNullOrBlank()
}

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Int? = null
)

@JsonClass(generateAdapter = true)
data class LogoutRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class AuthStatusResponse(
    @Json(name = "authenticated") val authenticated: Boolean,
    @Json(name = "user_id") val userId: Int? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "requires_2fa") val requires2fa: Boolean? = null,
    @Json(name = "two_factor_enrolled") val twoFactorEnrolled: Boolean? = null
)

/**
 * User profile returned by GET /api/v1/authentication/profile.
 * Backend schema is `app__schemas__authentication__UserProfileResponse`
 * — fields are inferred from the spec.
 */
@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    @Json(name = "id") val id: Int,
    @Json(name = "username") val username: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "date_of_birth") val dateOfBirth: String? = null,
    @Json(name = "biometric_enabled") val biometricEnabled: Boolean? = null,
    @Json(name = "telegram_chat_id") val telegramChatId: String? = null,
    @Json(name = "clinic_id") val clinicId: String? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "is_superuser") val isSuperuser: Boolean? = null
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — 2FA
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class TwoFAVerifyRequest(
    @Json(name = "pending_2fa_token") val pending2faToken: String,
    @Json(name = "totp_code") val totpCode: String,
    @Json(name = "remember_device") val rememberDevice: Boolean = false,
    @Json(name = "device_fingerprint") val deviceFingerprint: String? = null
)

@JsonClass(generateAdapter = true)
data class TwoFARecoveryRequest(
    @Json(name = "pending_2fa_token") val pending2faToken: String,
    @Json(name = "method") val method: String  // "email" | "sms"
)

@JsonClass(generateAdapter = true)
data class TwoFARecoveryResponse(
    @Json(name = "recovery_token") val recoveryToken: String,
    @Json(name = "sent") val sent: Boolean,
    @Json(name = "method") val method: String? = null
)

@JsonClass(generateAdapter = true)
data class TwoFARecoveryVerifyRequest(
    @Json(name = "recovery_token") val recoveryToken: String,
    @Json(name = "code") val code: String
)

@JsonClass(generateAdapter = true)
data class Send2FACodeRequest(
    @Json(name = "method") val method: String,
    @Json(name = "pending_2fa_token") val pending2faToken: String
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: profile
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class PatientProfileOut(
    @Json(name = "id") val id: Int,
    @Json(name = "phone") val phone: String,
    @Json(name = "fio") val fio: String? = null,           // backend uses `fio` here, not `full_name`
    @Json(name = "birth_year") val birthYear: Int? = null,
    @Json(name = "full_name") val fullName: String? = null,  // some endpoints use this
    @Json(name = "birth_date") val birthDate: String? = null,
    @Json(name = "telegram_chat_id") val telegramChatId: String? = null,
    @Json(name = "biometric_enabled") val biometricEnabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "birth_date") val birthDate: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "telegram_chat_id") val telegramChatId: String? = null,
    @Json(name = "biometric_enabled") val biometricEnabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class AvatarUploadRequest(
    @Json(name = "base64_data") val base64Data: String,
    @Json(name = "content_type") val contentType: String = "image/jpeg"
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: appointments
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class MobileAppointmentOut(
    @Json(name = "id") val id: Int,
    @Json(name = "patient_phone") val patientPhone: String? = null,
    @Json(name = "patient_name") val patientName: String? = null,
    @Json(name = "doctor_id") val doctorId: Int? = null,
    @Json(name = "doctor_name") val doctorName: String? = null,
    @Json(name = "specialty") val specialty: String? = null,
    @Json(name = "date") val date: String,
    @Json(name = "time") val time: String,
    @Json(name = "status") val status: String,
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "clinic_id") val clinicId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AppointmentBookRequest(
    @Json(name = "doctor_id") val doctorId: Int,
    @Json(name = "date") val date: String,
    @Json(name = "time") val time: String,
    @Json(name = "reason") val reason: String? = null,
    @Json(name = "clinic_id") val clinicId: String? = null
)

@JsonClass(generateAdapter = true)
data class AppointmentCancelRequest(
    @Json(name = "appointment_id") val appointmentId: Int,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class AppointmentRescheduleRequest(
    @Json(name = "appointment_id") val appointmentId: Int,
    @Json(name = "new_date") val newDate: String,
    @Json(name = "new_time") val newTime: String
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: doctors / services
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class DoctorSearchRequest(
    @Json(name = "query") val query: String? = null,
    @Json(name = "specialty") val specialty: String? = null,
    @Json(name = "clinic_id") val clinicId: String? = null
)

@JsonClass(generateAdapter = true)
data class DoctorOut(
    @Json(name = "id") val id: Int,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "specialty") val specialty: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "clinic_id") val clinicId: String? = null,
    @Json(name = "photo_url") val photoUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class DoctorScheduleOut(
    @Json(name = "doctor_id") val doctorId: Int,
    @Json(name = "slots") val slots: List<TimeSlot> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TimeSlot(
    @Json(name = "date") val date: String,
    @Json(name = "time") val time: String,
    @Json(name = "available") val available: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ServiceSearchRequest(
    @Json(name = "query") val query: String? = null,
    @Json(name = "category") val category: String? = null
)

@JsonClass(generateAdapter = true)
data class ServiceOut(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "price") val price: Double? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "duration_minutes") val durationMinutes: Int? = null
)

@JsonClass(generateAdapter = true)
data class ServiceCategoryOut(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "icon") val icon: String? = null
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: queue
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class QueueStatusOut(
    @Json(name = "queue_id") val queueId: Int? = null,
    @Json(name = "total_waiting") val totalWaiting: Int = 0,
    @Json(name = "current_number") val currentNumber: Int? = null,
    @Json(name = "average_wait_minutes") val averageWaitMinutes: Int? = null,
    @Json(name = "status") val status: String = "OPEN"
)

@JsonClass(generateAdapter = true)
data class QueuePositionOut(
    @Json(name = "position") val position: Int,
    @Json(name = "queue_number") val queueNumber: Int? = null,
    @Json(name = "estimated_wait_minutes") val estimatedWaitMinutes: Int? = null,
    @Json(name = "status") val status: String = "WAITING",
    @Json(name = "queue_id") val queueId: Int? = null
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: lab results
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class LabResultOut(
    @Json(name = "id") val id: Int,
    @Json(name = "patient_phone") val patientPhone: String? = null,
    @Json(name = "test_name") val testName: String,
    @Json(name = "result") val result: String? = null,
    @Json(name = "unit") val unit: String? = null,
    @Json(name = "reference_range") val referenceRange: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "performed_at") val performedAt: String? = null,
    @Json(name = "doctor_name") val doctorName: String? = null
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: notifications
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class NotificationOut(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "data") val data: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class NotificationSettingsOut(
    @Json(name = "appointment_reminders") val appointmentReminders: Boolean = true,
    @Json(name = "queue_updates") val queueUpdates: Boolean = true,
    @Json(name = "lab_results") val labResults: Boolean = true,
    @Json(name = "marketing") val marketing: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NotificationSettingsRequest(
    @Json(name = "appointment_reminders") val appointmentReminders: Boolean? = null,
    @Json(name = "queue_updates") val queueUpdates: Boolean? = null,
    @Json(name = "lab_results") val labResults: Boolean? = null,
    @Json(name = "marketing") val marketing: Boolean? = null
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: clinic info / feedback / emergency / version
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class ClinicInfoOut(
    @Json(name = "name") val name: String,
    @Json(name = "address") val address: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "working_hours") val workingHours: String? = null,
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lng") val lng: Double? = null
)

@JsonClass(generateAdapter = true)
data class MobileQuickStats(
    @Json(name = "upcoming_appointments") val upcomingAppointments: Int = 0,
    @Json(name = "unread_notifications") val unreadNotifications: Int = 0,
    @Json(name = "queue_position") val queuePosition: Int? = null,
    @Json(name = "last_visit_date") val lastVisitDate: String? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackRequest(
    @Json(name = "rating") val rating: Int,
    @Json(name = "message") val message: String? = null,
    @Json(name = "category") val category: String? = null
)

@JsonClass(generateAdapter = true)
data class EmergencyContactRequest(
    @Json(name = "name") val name: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "relationship") val relationship: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiVersionOut(
    @Json(name = "version") val version: String,
    @Json(name = "api_version") val apiVersion: String? = null,
    @Json(name = "build_date") val buildDate: String? = null
)

// ═══════════════════════════════════════════════════════════════════════
// P-04: DTOs — Doctor directory
// ═══════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class DoctorDto(
    @Json(name = "id") val id: Int,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "specialty") val specialty: String,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_active") val isActive: Boolean = true
) {
    fun toEntity(): com.aistudio.clinicsystem.data.db.DoctorEntity {
        return com.aistudio.clinicsystem.data.db.DoctorEntity(
            serverId = id,
            fullName = fullName,
            specialty = specialty,
            phone = phone ?: "",
            email = email ?: "",
            avatarUrl = avatarUrl,
            isActive = isActive,
            updatedAt = System.currentTimeMillis()
        )
    }
}

@JsonClass(generateAdapter = true)
data class TimeSlotDto(
    @Json(name = "time") val time: String, // "09:00"
    @Json(name = "available") val available: Boolean,
    @Json(name = "appointment_id") val appointmentId: String? = null // if booked
)

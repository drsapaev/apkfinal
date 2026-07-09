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

/**
 * High-1 audit fix: aligned with backend `app/schemas/mobile.py:PatientProfileOut`.
 *
 * Backend returns (Pydantic, all snake_case):
 *   id: int
 *   fio: str                              ← NOT `full_name`
 *   phone: str
 *   birth_year: int | None
 *   address: str | None                   ← NEW (was missing on client)
 *   telegram_id: str | None               ← NOT `telegram_chat_id`
 *   created_at: datetime                  ← NEW (ISO 8601 string)
 *
 * The previous client DTO expected fields the backend never returns
 * (`full_name`, `birth_date`, `biometric_enabled`, `telegram_chat_id`)
 * and missed `address` + `created_at`. Every field except `id`/`fio`/
 * `phone`/`birth_year` was null after JSON parsing.
 *
 * For UI display, use [displayName] (= fio or fallback) and
 * [telegramConnected] (= telegram_id != null).
 */
@JsonClass(generateAdapter = true)
data class PatientProfileOut(
    @Json(name = "id") val id: Int,
    @Json(name = "fio") val fio: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "birth_year") val birthYear: Int? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "telegram_id") val telegramId: String? = null,
    /** ISO 8601 datetime string, e.g. "2026-07-10T14:30:00Z". */
    @Json(name = "created_at") val createdAt: String? = null,
) {
    /** Convenience accessor: display name = fio (always non-null on backend). */
    val displayName: String get() = fio.ifBlank { phone }

    /** Convenience accessor: true if the user has linked a Telegram account. */
    val telegramConnected: Boolean get() = !telegramId.isNullOrBlank()
}

/**
 * High-1 audit fix: profile update request body.
 *
 * Backend `PUT /api/v1/mobile/profile` accepts the same shape as
 * `PatientProfileOut` for the updatable fields. Note that the backend
 * does NOT support `biometric_enabled` — biometric enrollment is a
 * client-only preference stored in EncryptedSharedPreferences.
 */
@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    @Json(name = "fio") val fio: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "birth_year") val birthYear: Int? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "telegram_id") val telegramId: String? = null,
)

@JsonClass(generateAdapter = true)
data class AvatarUploadRequest(
    @Json(name = "base64_data") val base64Data: String,
    @Json(name = "content_type") val contentType: String = "image/jpeg"
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: appointments
// ═══════════════════════════════════════════════════════════════════════

/**
 * High-1 audit fix: aligned with backend `AppointmentUpcomingOut`
 * (`app/schemas/mobile.py:254-264`).
 *
 * Backend returns:
 *   id: int
 *   doctor_name: str
 *   specialty: str
 *   appointment_date: datetime          ← ISO 8601 string, single field
 *   status: str
 *   clinic_address: str                  ← NOT `clinic_id`
 *
 * The previous client DTO (renamed `MobileAppointmentOut`) expected 13
 * fields including separate `date` + `time` strings, `patient_phone`,
 * `patient_name`, `doctor_id`, `reason`, `notes`, `clinic_id`,
 * `created_at`, `updated_at` — NONE of which the backend returns.
 *
 * Use [date] / [time] convenience accessors to split the ISO datetime
 * into the format expected by the existing UI (date string + time
 * string). The split is timezone-aware: backend sends UTC ISO 8601,
 * the client converts to the device's local timezone for display.
 */
@JsonClass(generateAdapter = true)
data class AppointmentUpcomingOut(
    @Json(name = "id") val id: Int,
    @Json(name = "doctor_name") val doctorName: String,
    @Json(name = "specialty") val specialty: String,
    /** ISO 8601 datetime string from backend, e.g. "2026-07-10T14:30:00Z". */
    @Json(name = "appointment_date") val appointmentDate: String,
    @Json(name = "status") val status: String,
    @Json(name = "clinic_address") val clinicAddress: String,
) {
    /**
     * Splits [appointmentDate] (ISO 8601) into the date portion
     * (YYYY-MM-DD) for display. Returns the raw string if parsing fails.
     */
    val date: String
        get() = try {
            // Take the date portion before the 'T' separator.
            appointmentDate.substringBefore('T').ifBlank { appointmentDate }
        } catch (e: Exception) {
            appointmentDate
        }

    /**
     * Splits [appointmentDate] (ISO 8601) into the time portion
     * (HH:MM) for display. Returns empty string if parsing fails.
     */
    val time: String
        get() = try {
            val timePart = appointmentDate.substringAfter('T', "")
                .substringBefore('Z', "")
                .substringBefore('+', "")
                .substringBefore('-', "")
            if (timePart.length >= 5) timePart.substring(0, 5) else timePart
        } catch (e: Exception) {
            ""
        }
}

/**
 * High-1 audit fix: type alias for backward-compat with code that still
 * references the old `MobileAppointmentOut` name. Will be removed in a
 * follow-up PR after all call sites are migrated.
 */
typealias MobileAppointmentOut = AppointmentUpcomingOut

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

/**
 * High-1 audit fix: aligned with backend `LabResultOut`
 * (`app/schemas/mobile.py:280-292`).
 *
 * Backend returns:
 *   id: int
 *   test_name: str
 *   result_value: str                    ← NOT `result`
 *   reference_range: str                 ← required (not nullable)
 *   unit: str                            ← required
 *   result_date: datetime                ← NOT `performed_at`
 *   status: str
 *   notes: str | None                    ← NOT `doctor_name`
 *
 * The previous client DTO expected `result`, `performed_at`,
 * `doctor_name`, `patient_phone` — NONE of which the backend returns.
 * `unit` and `reference_range` were nullable on the client but are
 * required on the backend.
 *
 * For UI display of "Doctor: Dr. X", use [notes] — backend stores
 * arbitrary notes including doctor attribution where applicable.
 */
@JsonClass(generateAdapter = true)
data class LabResultOut(
    @Json(name = "id") val id: Int,
    @Json(name = "test_name") val testName: String,
    @Json(name = "result_value") val resultValue: String,
    @Json(name = "reference_range") val referenceRange: String,
    @Json(name = "unit") val unit: String,
    /** ISO 8601 datetime string, e.g. "2026-07-10T14:30:00Z". */
    @Json(name = "result_date") val resultDate: String,
    @Json(name = "status") val status: String,
    @Json(name = "notes") val notes: String? = null,
)

// ═══════════════════════════════════════════════════════════════════════
// DTOs — Mobile: notifications
// ═══════════════════════════════════════════════════════════════════════

/**
 * High-1 audit fix: aligned with backend `/mobile/notifications` response.
 *
 * Backend `GET /api/v1/mobile/notifications` returns `list[dict]` with
 * these fields (see `mobile_api.py:459-470`):
 *   id: int | None                       ← delivery_id (may be null)
 *   title: str                           ← event_title
 *   message: str                         ← event_message (NOT `body`)
 *   type: str                            ← event_type
 *   data: dict                           ← event_payload_snapshot
 *   sent_at: datetime | None            ← delivery_created_at (NOT `created_at`)
 *   read: bool                           ← delivery_read_at is not None (NOT `is_read`)
 *
 * The previous client DTO expected `body`, `is_read`, `created_at` —
 * NONE of which the backend returns. All three fields were null/false
 * after JSON parsing.
 */
@JsonClass(generateAdapter = true)
data class NotificationOut(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "data") val data: Map<String, Any?>? = null,
    /** ISO 8601 datetime string, nullable. */
    @Json(name = "sent_at") val sentAt: String? = null,
    @Json(name = "read") val read: Boolean = false,
)

/**
 * High-1 audit fix: aligned with backend `MobileNotificationSettings`
 * (`app/schemas/mobile.py:201-212`).
 *
 * Backend returns 7 fields; the previous client DTO had 4 fields
 * (missing `payment_notifications`, `push_enabled`, `email_enabled`,
 * `sms_enabled`) and had `marketing` (which backend does NOT have).
 */
@JsonClass(generateAdapter = true)
data class NotificationSettingsOut(
    @Json(name = "appointment_reminders") val appointmentReminders: Boolean = true,
    @Json(name = "queue_updates") val queueUpdates: Boolean = true,
    @Json(name = "lab_results") val labResults: Boolean = true,
    @Json(name = "payment_notifications") val paymentNotifications: Boolean = true,
    @Json(name = "push_enabled") val pushEnabled: Boolean = true,
    @Json(name = "email_enabled") val emailEnabled: Boolean = false,
    @Json(name = "sms_enabled") val smsEnabled: Boolean = false,
)

/**
 * High-1 audit fix: aligned with backend `MobileNotificationSettings`
 * for `PUT /api/v1/mobile/settings/notifications`. All fields optional
 * — only provided fields are updated.
 */
@JsonClass(generateAdapter = true)
data class NotificationSettingsRequest(
    @Json(name = "appointment_reminders") val appointmentReminders: Boolean? = null,
    @Json(name = "queue_updates") val queueUpdates: Boolean? = null,
    @Json(name = "lab_results") val labResults: Boolean? = null,
    @Json(name = "payment_notifications") val paymentNotifications: Boolean? = null,
    @Json(name = "push_enabled") val pushEnabled: Boolean? = null,
    @Json(name = "email_enabled") val emailEnabled: Boolean? = null,
    @Json(name = "sms_enabled") val smsEnabled: Boolean? = null,
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

/**
 * High-1 audit fix: aligned with backend `MobileQuickStats`
 * (`app/schemas/mobile.py:215-226`).
 *
 * Backend returns 7 fields; the previous client DTO had 4 fields
 * (`upcoming_appointments`, `unread_notifications`, `queue_position`,
 * `last_visit_date`) — NONE of which match the backend except
 * `upcoming_appointments`. `unread_notifications`, `queue_position`,
 * `last_visit_date` were all null/0 after parsing.
 *
 * New fields added: `total_appointments`, `completed_appointments`,
 * `total_spent`, `favorite_doctor`, `pending_payments`.
 */
@JsonClass(generateAdapter = true)
data class MobileQuickStats(
    @Json(name = "total_appointments") val totalAppointments: Int = 0,
    @Json(name = "upcoming_appointments") val upcomingAppointments: Int = 0,
    @Json(name = "completed_appointments") val completedAppointments: Int = 0,
    @Json(name = "total_spent") val totalSpent: Double = 0.0,
    /** ISO 8601 datetime string, nullable. */
    @Json(name = "last_visit") val lastVisit: String? = null,
    @Json(name = "favorite_doctor") val favoriteDoctor: String? = null,
    @Json(name = "pending_payments") val pendingPayments: Int = 0,
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

package com.aistudio.clinicsystem.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * ApiService — STAFF-facing Retrofit surface (registrar/doctor/admin flows).
 *
 * M-CONTRACT-FIX: the previous legacy paths (`/api/v1/users/me`,
 * `/api/v1/appointments/{id}/status`, `/api/v1/patients/records*`,
 * `/api/v1/queue`, `/api/v1/queue/register`, `/api/v1/users/telegram/link и /unlink`)
 * are NOT published by the backend anymore (see `final/backend/app/api/v1/api.py`
 * — "Legacy API удалён") and returned HTTP 404 on every call. This interface
 * now targets the routes the backend actually serves:
 *
 *   GET  /api/v1/appointments                      (appointments.py:list_appointments)
 *   POST /api/v1/appointments                      (appointments.py:create_appointment)
 *   PUT  /api/v1/appointments/{id}                 (appointments.py:update_appointment)
 *   GET  /api/v1/patients?phone=...                (patients.py:list_patients)
 *   GET  /api/v1/queue/available-specialists       (qr_queue/_specialists.py)
 *   GET  /api/v1/queue/status/{specialist_id}      (qr_queue/_queue_ops.py)
 *
 * Patient-facing flows (login, profile, booking, queue position, lab
 * results) live in [MobileApiService]. Authentication and 2FA live there
 * too — this interface intentionally has NO auth endpoints.
 */
interface ApiService {
    // --- Appointments (staff) ---

    @GET("api/v1/appointments/")
    suspend fun getAppointments(
        @Query("limit") limit: Int = 200,
        @Query("skip") skip: Int = 0,
        @Query("patient_id") patientId: Int? = null,
        @Query("doctor_id") doctorId: Int? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): Response<List<StaffAppointmentDto>>

    @POST("api/v1/appointments/")
    suspend fun createAppointment(
        @Body appointment: StaffAppointmentCreateRequest,
    ): Response<StaffAppointmentDto>

    /**
     * Partial update — the backend has no dedicated
     * `PUT /appointments/{id}/status` route; status changes go through
     * the generic update with a `{"status": ...}` body.
     */
    @PUT("api/v1/appointments/{id}")
    suspend fun updateAppointment(
        @Path("id") id: Int,
        @Body appointment: StaffAppointmentUpdateRequest,
    ): Response<StaffAppointmentDto>

    // --- Patient lookup (staff) ---

    /**
     * Exact search by phone (`?phone=...`). Used to resolve the backend
     * `patient_id` required by POST /api/v1/appointments — the create
     * schema takes an int patient_id, not a phone number.
     */
    @GET("api/v1/patients/")
    suspend fun findPatientsByPhone(
        @Query("phone") phone: String,
        @Query("limit") limit: Int = 1,
    ): Response<List<StaffPatientDto>>

    // --- Queues (staff) ---

    /** Public specialists list for the QR queue console. */
    @GET("api/v1/queue/available-specialists")
    suspend fun getAvailableSpecialists(): Response<AvailableSpecialistsResponse>

    /** Per-specialist live queue with patient entries (Admin/Doctor/Registrar only). */
    @GET("api/v1/queue/status/{specialist_id}")
    suspend fun getQueueStatus(
        @Path("specialist_id") specialistId: Int,
    ): Response<QueueStatusResponse>

    @POST("api/v1/doctor/queue/{entry_id}/call")
    suspend fun callQueueEntry(
        @Path("entry_id") entryId: Int,
    ): Response<QueueActionResponse>

    @POST("api/v1/doctor/queue/{entry_id}/start-visit")
    suspend fun startQueueVisit(
        @Path("entry_id") entryId: Int,
    ): Response<QueueActionResponse>

    @POST("api/v1/doctor/queue/{entry_id}/complete")
    suspend fun completeQueueVisit(
        @Path("entry_id") entryId: Int,
    ): Response<QueueActionResponse>

    @POST("api/v1/online-queue/entries/{entry_id}/cancel")
    suspend fun cancelQueueEntry(
        @Path("entry_id") entryId: Int,
    ): Response<QueueActionResponse>

    /**
     * Admin-only system users list (backend `require_roles("Admin")`).
     * Powers the Administration section of the role-aware staff console.
     */
    @GET("api/v1/users")
    suspend fun getSystemUsers(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): Response<StaffUsersPageDto>
}

// ─────────────────────────────────────────────────────────────────────────
// DTOs — system users (backend `app/schemas/user_management.py`)
// ─────────────────────────────────────────────────────────────────────────

/** Response of GET /api/v1/users (backend `UserListResponse`). */
@JsonClass(generateAdapter = true)
data class StaffUsersPageDto(
    @Json(name = "users") val users: List<StaffUserDto> = emptyList(),
    @Json(name = "total") val total: Int = 0,
    @Json(name = "page") val page: Int = 1,
    @Json(name = "per_page") val perPage: Int = 50,
    @Json(name = "total_pages") val totalPages: Int = 0,
)

/** One system user (backend `UserResponse`, profile payloads ignored). */
@JsonClass(generateAdapter = true)
data class StaffUserDto(
    @Json(name = "id") val id: Int,
    @Json(name = "username") val username: String = "",
    @Json(name = "email") val email: String? = null,
    /** Patient | Doctor | Registrar | Lab | Cashier | Admin | … */
    @Json(name = "role") val role: String = "",
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "is_superuser") val isSuperuser: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────
// DTOs — staff appointments (backend `app/schemas/appointment.py`)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Aligned with backend `Appointment` schema (GET/POST/PUT /appointments).
 * The legacy client `AppointmentDto` shape (patient_phone / patient_name /
 * doctor_name / specialty / date / time / reason) is NOT what this router
 * returns — appointments carry `appointment_date` + `appointment_time`,
 * int `patient_id`/`doctor_id`, and an enriched `patient_name`.
 */
@JsonClass(generateAdapter = true)
data class StaffAppointmentDto(
    @Json(name = "id") val id: Int,
    @Json(name = "patient_id") val patientId: Int? = null,
    @Json(name = "doctor_id") val doctorId: Int? = null,
    @Json(name = "department") val department: String? = null,
    /** "YYYY-MM-DD". */
    @Json(name = "appointment_date") val appointmentDate: String,
    /** "HH:MM" or null for date-only bookings. */
    @Json(name = "appointment_time") val appointmentTime: String? = null,
    @Json(name = "notes") val notes: String? = null,
    /** scheduled | confirmed | cancelled | completed */
    @Json(name = "status") val status: String = "scheduled",
    @Json(name = "patient_name") val patientName: String? = null,
    @Json(name = "services") val services: List<String>? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

/**
 * Body for POST /api/v1/appointments — backend `AppointmentCreate`
 * requires `patient_id` (resolve it first via [ApiService.findPatientsByPhone]).
 */
@JsonClass(generateAdapter = true)
data class StaffAppointmentCreateRequest(
    @Json(name = "patient_id") val patientId: Int,
    @Json(name = "doctor_id") val doctorId: Int? = null,
    @Json(name = "department") val department: String? = null,
    /** "YYYY-MM-DD". */
    @Json(name = "appointment_date") val appointmentDate: String,
    /** "HH:MM" or null. */
    @Json(name = "appointment_time") val appointmentTime: String? = null,
    @Json(name = "notes") val notes: String? = null,
    /** scheduled | confirmed | cancelled | completed (backend default: scheduled). */
    @Json(name = "status") val status: String = "scheduled",
    @Json(name = "visit_type") val visitType: String = "paid",
    @Json(name = "payment_type") val paymentType: String = "cash",
    @Json(name = "services") val services: List<String>? = null,
)

/** Body for PUT /api/v1/appointments/{id} — backend `AppointmentUpdate`, all fields optional. */
@JsonClass(generateAdapter = true)
data class StaffAppointmentUpdateRequest(
    @Json(name = "doctor_id") val doctorId: Int? = null,
    @Json(name = "appointment_date") val appointmentDate: String? = null,
    @Json(name = "appointment_time") val appointmentTime: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "status") val status: String? = null,
)

/** Minimal patient row returned by GET /api/v1/patients (backend `Patient` schema). */
@JsonClass(generateAdapter = true)
data class StaffPatientDto(
    @Json(name = "id") val id: Int,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "phone") val phone: String? = null,
) {
    val displayName: String
        get() =
            fullName
                ?: listOfNotNull(lastName, firstName).joinToString(" ").ifBlank { phone ?: "Пациент #$id" }
}

// ─────────────────────────────────────────────────────────────────────────
// DTOs — staff queues (backend `qr_queue` package)
// ─────────────────────────────────────────────────────────────────────────

/** Response of GET /api/v1/queue/available-specialists. */
@JsonClass(generateAdapter = true)
data class AvailableSpecialistsResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "specialists") val specialists: List<QueueSpecialistDto> = emptyList(),
    @Json(name = "total") val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class QueueSpecialistDto(
    @Json(name = "id") val id: Int,
    @Json(name = "specialty") val specialty: String? = null,
    @Json(name = "specialty_display") val specialtyDisplay: String? = null,
    @Json(name = "doctor_name") val doctorName: String? = null,
    @Json(name = "cabinet") val cabinet: String? = null,
)

/** Response of GET /api/v1/queue/status/{specialist_id} (backend `QueueStatusResponse`). */
@JsonClass(generateAdapter = true)
data class QueueStatusResponse(
    @Json(name = "queue_id") val queueId: Int = 0,
    /** "YYYY-MM-DD". */
    @Json(name = "day") val day: String? = null,
    @Json(name = "specialist_name") val specialistName: String? = null,
    @Json(name = "is_open") val isOpen: Boolean = false,
    @Json(name = "total_entries") val totalEntries: Int = 0,
    @Json(name = "waiting_entries") val waitingEntries: Int = 0,
    @Json(name = "entries") val entries: List<QueueEntryDto> = emptyList(),
)

/** One patient entry in a specialist's live queue. */
@JsonClass(generateAdapter = true)
data class QueueEntryDto(
    @Json(name = "id") val id: Int,
    @Json(name = "number") val number: Int,
    @Json(name = "patient_name") val patientName: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "status") val status: String = "waiting",
    /** ISO 8601 datetime strings. */
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "called_at") val calledAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class QueueActionResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "entry_id") val entryId: Int? = null,
    @Json(name = "status") val status: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────
// Legacy payload DTOs
// ─────────────────────────────────────────────────────────────────────────

/**
 * Local cached-user representation (parsed from the canonical
 * /api/v1/authentication/profile response — see MobileApiService.UserProfileResponse).
 * Kept here for backwards compatibility with the auth stack.
 */
@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: Int,
    @Json(name = "phone") val phone: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "role") val role: String, // "PATIENT" or "STAFF"
    @Json(name = "date_of_birth") val dateOfBirth: String?,
    @Json(name = "biometric_enabled") val biometricEnabled: Boolean,
    @Json(name = "telegram_chat_id") val telegramChatId: String?,
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base",
)

/**
 * Queue entry model used by the realtime (WebSocket) event payloads
 * (`utils/WsEventModels.kt`) and the local queue cache reconciliation.
 */
@JsonClass(generateAdapter = true)
data class QueueDto(
    @Json(name = "id") val id: Int,
    @Json(name = "patient_name") val patientName: String,
    @Json(name = "appointment_id") val appointmentId: Int,
    @Json(name = "position") val position: Int,
    @Json(name = "status") val status: String, // "WAITING", "IN_PROGRESS", "COMPLETED"
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base",
)

/**
 * TASK-1: structured outbox payload for appointment creation rows
 * (CREATE_APPOINTMENT_SELF / CREATE_APPOINTMENT_STAFF).
 *
 * Unlike the legacy [AppointmentDto], this carries the STRUCTURED
 * identifiers (patient_id / doctor_id) captured at booking time plus the
 * operation owner, so the outbox retry replays the ORIGINAL scenario:
 *   - owner=PATIENT  → POST /api/v1/mobile/appointments/book  (self-booking)
 *   - owner=STAFF    → POST /api/v1/appointments              (staff books a patient)
 * No route switching after a server rejection; identity is never
 * re-derived from the display name alone (serverId is re-resolved only
 * as a fallback when null, and a failed resolution dead-letters the row).
 */
@JsonClass(generateAdapter = true)
data class AppointmentOutboxPayload(
    /** PATIENT = self-booking by the signed-in patient; STAFF = registrar/doctor booking for a chosen patient. */
    @Json(name = "owner") val owner: String,
    @Json(name = "patient_id") val patientId: Int? = null,
    @Json(name = "patient_phone") val patientPhone: String = "",
    @Json(name = "patient_name") val patientName: String = "",
    @Json(name = "doctor_id") val doctorId: Int? = null,
    @Json(name = "doctor_name") val doctorName: String = "",
    @Json(name = "specialty") val specialty: String = "",
    @Json(name = "date") val date: String = "",
    @Json(name = "time") val time: String = "",
    @Json(name = "reason") val reason: String = "",
    @Json(name = "status") val status: String = "PENDING",
)

/**
 * M-CONTRACT-FIX: [AppointmentDto] and [MedicalRecordDto] are kept ONLY as
 * the outbox payload format (`PendingSyncEntity.payload`, written by
 * ClinicRepository when a write is queued offline). They are parsed back
 * with Moshi when the outbox is flushed and converted to the typed
 * requests above.
 */
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
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base",
    // Stage 3.4 (H-5 fix): version + updatedAt for delta sync and conflict resolution.
    // Both optional — server may not return them on legacy endpoints.
    @Json(name = "version") val version: Int? = null,
    @Json(name = "updated_at") val updatedAt: Long? = null,
    // Stage 3.3: client_request_id for idempotency. Server uses this to
    // dedup concurrent POSTs with the same key (24h window).
    @Json(name = "client_request_id") val clientRequestId: String? = null,
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
    @Json(name = "clinic_id") val clinicId: String? = "clinic_base",
    // Stage 3.4: version + updatedAt
    @Json(name = "version") val version: Int? = null,
    @Json(name = "updated_at") val updatedAt: Long? = null,
    @Json(name = "client_request_id") val clientRequestId: String? = null,
)

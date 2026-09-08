package com.aistudio.clinicsystem.utils

/**
 * CODEX-P1-FIX (PR #147) / Task 5: single source of truth for mapping
 * appointment statuses between the client vocabulary and the backend.
 *
 * Client vocabulary (Room `AppointmentEntity.status`, UI filters):
 *   PENDING | APPROVED | COMPLETED | CANCELLED
 *
 * Backend vocabulary (GET/POST/PUT /api/v1/appointments,
 * /mobile/appointments/upcoming — lowercase):
 *   scheduled | confirmed | completed | cancelled
 *
 * The staff endpoint returns lowercase values (`scheduled`, `confirmed`),
 * while the staff UI only recognizes local values (`PENDING`, `APPROVED`).
 * Every write path that caches a server status MUST go through
 * [fromServer] — otherwise synchronized appointments render as an unknown
 * state and lose the approve/complete actions. Every request path that
 * sends a status to the server MUST go through [toServer].
 */
object ServerStatusMapper {

    /** Client ("PENDING") → backend ("scheduled"). Unknown → lowercase passthrough. */
    fun toServer(clientStatus: String): String =
        when (clientStatus.uppercase()) {
            "PENDING", "PLANNED" -> "scheduled"
            "APPROVED", "CONFIRMED" -> "confirmed"
            "CANCELLED", "CANCELED" -> "cancelled"
            "COMPLETED" -> "completed"
            else -> clientStatus.lowercase()
        }

    /** Backend ("scheduled") → client ("PENDING"). Unknown → uppercase passthrough. */
    fun fromServer(serverStatus: String): String =
        when (serverStatus.lowercase()) {
            "scheduled" -> "PENDING"
            "confirmed" -> "APPROVED"
            "completed" -> "COMPLETED"
            "cancelled", "canceled" -> "CANCELLED"
            else -> serverStatus.uppercase()
        }
}

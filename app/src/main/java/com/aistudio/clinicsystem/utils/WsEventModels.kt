package com.aistudio.clinicsystem.utils

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * P0-2 audit fix: WebSocket event models aligned with backend contract.
 *
 * Backend (`final/backend/app/ws/queue_ws.py` and `websocket_auth.py`)
 * uses the JSON field `type` for event discrimination, NOT `event`.
 * Mobile client was reading `event` → every message had `eventType=null`
 * → `handleSocketMessage` returned early → all realtime updates were
 * silently dropped.
 *
 * Backend message types observed:
 *   - `queue.connected`   sent on successful /ws/queue connection
 *   - `ping`              heartbeat every 30s (client must reply with `pong`)
 *   - `error`             auth/origin errors (with `reason` field)
 *   - `queue_update`      broadcast from `broadcast_queue_update(event_type=...)`
 *   - `patient_called`    queue event variants
 *   - `entry_added`       queue event variants
 *   - `dev.accepted`      DEV mode only (skip in production client)
 *
 * Legacy event types from previous backend version (kept for backward
 * compatibility if the backend still emits them on some paths):
 *   - `APPOINTMENT_STATUS`
 *   - `NEW_MEDICAL_RECORD`
 *   - `QUEUE_UPDATE`
 *
 * The mobile client now handles BOTH naming conventions: the new
 * lowercase `type` field (preferred) and the legacy UPPER_CASE `event`
 * field (fallback).
 */

@JsonClass(generateAdapter = true)
data class BaseWsEvent(
    /** Preferred field — backend uses `type` since 2026-07 audit. */
    val type: String? = null,
    /** Legacy fallback — some backend paths may still emit `event`. */
    val event: String? = null,
) {
    /** Returns the effective event-type: prefers `type`, falls back to `event`. */
    val effectiveType: String?
        get() = type ?: event
}

@JsonClass(generateAdapter = true)
data class AppointmentStatusEvent(
    val type: String? = null,
    val event: String? = null,
    val data: AppointmentStatusData?
)

@JsonClass(generateAdapter = true)
data class AppointmentStatusData(
    val id: Int?,
    val status: String?,
    @param:Json(name = "doctor_name") val doctorName: String?,
    val date: String?,
    val time: String?,
    @param:Json(name = "patient_name") val patientName: String?,
    @param:Json(name = "patient_phone") val patientPhone: String?,
    val specialty: String?,
    val reason: String?
)

@JsonClass(generateAdapter = true)
data class NewMedicalRecordEvent(
    val type: String? = null,
    val event: String? = null,
    val data: NewMedicalRecordData?
)

@JsonClass(generateAdapter = true)
data class NewMedicalRecordData(
    val id: Int?,
    @param:Json(name = "patient_phone") val patientPhone: String?,
    @param:Json(name = "doctor_name") val doctorName: String?,
    val diagnosis: String?,
    val prescription: String?,
    @param:Json(name = "visit_date") val visitDate: String?,
    val recommendations: String?
)

@JsonClass(generateAdapter = true)
data class QueueUpdateEvent(
    val type: String? = null,
    val event: String? = null,
    val data: QueueUpdateData?,
    /** Room identifier sent by backend: "{department}::{date}". */
    val room: String? = null
)

@JsonClass(generateAdapter = true)
data class QueueUpdateData(
    val queue: List<com.aistudio.clinicsystem.data.api.QueueDto>?
)

/**
 * P0-2 audit fix: backend heartbeat `ping` message — every 30s.
 * Client must reply with `{"type":"pong"}` to keep the connection alive.
 *
 * Backend (`queue_ws.py:358`):
 *   await websocket.send_json({"type": "ping", "timestamp": ...})
 */
@JsonClass(generateAdapter = true)
data class WsPingEvent(
    val type: String? = null,
    val timestamp: Double? = null
)

/**
 * P0-2 audit fix: backend sends `queue.connected` on successful
 * subscription. Mobile client uses this as the signal that the
 * WebSocket is fully ready (replaces the legacy `subscribe` handshake
 * that backend never expected).
 *
 * Backend (`queue_ws.py:367`):
 *   await websocket.send_json({"type": "queue.connected", "room": room})
 */
@JsonClass(generateAdapter = true)
data class WsQueueConnectedEvent(
    val type: String? = null,
    val room: String? = null
)

/**
 * P0-2 audit fix: backend sends `error` on auth/origin failures.
 * Mobile client logs the reason and triggers a reconnect only if the
 * error is recoverable (network) — auth errors force session re-login.
 *
 * Backend (`queue_ws.py:323,330,337`):
 *   {"type": "error", "reason": "Authentication required in production"}
 *   {"type": "error", "reason": "origin not allowed"}
 *   {"type": "error", "reason": "auth required"}
 */
@JsonClass(generateAdapter = true)
data class WsErrorEvent(
    val type: String? = null,
    val reason: String? = null
)

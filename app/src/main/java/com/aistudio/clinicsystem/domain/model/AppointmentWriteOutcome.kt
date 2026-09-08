package com.aistudio.clinicsystem.domain.model

import com.aistudio.clinicsystem.data.db.AppointmentEntity

/**
 * TASK-2: the outcome of a patient-visible write against the backend.
 *
 * The UI must never report "saved" when only the local Room cache changed:
 *   - [Confirmed]       — the server acknowledged the change;
 *   - [Queued]          — the change could not be delivered right now, is
 *                         durably queued in the outbox and will be retried;
 *                         the local row is explicitly marked QUEUED;
 *   - [Rejected]        — the server REFUSED the change (HTTP 4xx — 403/409/
 *                         422 are rejections, NOT network absence); the local
 *                         row is marked REJECTED and the user sees the error;
 *   - [CancelledLocally] — an offline (not-yet-synced) appointment was
 *                         cancelled: the local row AND its outbox create row
 *                         were removed, so it will never be created later.
 */
sealed class AppointmentWriteOutcome {
    abstract val entity: AppointmentEntity

    data class Confirmed(
        override val entity: AppointmentEntity,
        val serverId: Int,
    ) : AppointmentWriteOutcome()

    data class Queued(
        override val entity: AppointmentEntity,
        val reason: String,
    ) : AppointmentWriteOutcome()

    data class Rejected(
        override val entity: AppointmentEntity,
        val httpCode: Int?,
        val message: String,
    ) : AppointmentWriteOutcome()

    data class CancelledLocally(
        override val entity: AppointmentEntity,
    ) : AppointmentWriteOutcome()
}

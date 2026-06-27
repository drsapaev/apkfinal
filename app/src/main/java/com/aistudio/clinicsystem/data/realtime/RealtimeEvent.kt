package com.aistudio.clinicsystem.data.realtime

import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.db.QueueSnapshotEntity

/**
 * M3B.2: Sealed class representing all real-time events from WebSocket.
 *
 * ViewModels collect [RealtimeManager.events] Flow and react to these
 * events instead of each ViewModel managing its own WebSocket connection.
 *
 * The RealtimeManager handles:
 *   - WebSocket connection lifecycle (connect/disconnect/reconnect)
 *   - Event parsing (JSON → RealtimeEvent)
 *   - Event distribution (one WebSocket → many subscribers)
 *
 * ViewModels handle:
 *   - Reacting to events (update UI state, show notifications)
 *   - Business logic (reconciliation guards, Room writes)
 */
sealed class RealtimeEvent {
    /** Appointment status changed (e.g. PENDING → APPROVED). */
    data class AppointmentStatusChanged(
        val appointmentId: Int,
        val status: String,
        val doctorName: String,
        val date: String,
        val time: String,
        val patientName: String,
        val patientPhone: String,
        val specialty: String?,
        val reason: String?
    ) : RealtimeEvent()

    /** New medical record created for a patient. */
    data class NewMedicalRecord(
        val id: Int,
        val patientPhone: String,
        val doctorName: String,
        val diagnosis: String,
        val prescription: String,
        val visitDate: String,
        val recommendations: String
    ) : RealtimeEvent()

    /** Queue state updated (full snapshot of current queue). */
    data class QueueUpdated(
        val snapshots: List<QueueSnapshotEntity>
    ) : RealtimeEvent()

    /** WebSocket connection state changed. */
    sealed class ConnectionState : RealtimeEvent() {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        object Reconnecting : ConnectionState()
    }
}

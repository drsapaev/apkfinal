package com.aistudio.clinicsystem.data.realtime

import android.content.Context
import android.util.Log
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.QueueSnapshotEntity
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.utils.ClinicWebSocketClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * M3B.2: RealtimeManager — single entry point for real-time features.
 *
 * Replaces the pattern where [ClinicWebSocketClient] was a singleton
 * accessed directly from multiple places:
 *   - ClinicViewModel (start/stop on session change)
 *   - NetworkMonitor (reconnect on network restore)
 *   - Tests (impossible to mock)
 *
 * RealtimeManager provides:
 *   1. [events]: SharedFlow<RealtimeEvent> — one WebSocket, many subscribers
 *   2. [connectionState]: SharedFlow<RealtimeEvent.ConnectionState>
 *   3. [start()] / [stop()] — connection lifecycle tied to session
 *   4. [reconnect()] — manual reconnect (e.g. on network restore)
 *
 * ViewModels collect [events] and handle business logic:
 *   - AuthViewModel: no interest in real-time events
 *   - PatientViewModel: reacts to AppointmentStatusChanged, NewMedicalRecord
 *   - StaffViewModel: reacts to QueueUpdated, AppointmentStatusChanged
 *   - ClinicViewModel: manages connection lifecycle (start/stop on login/logout)
 *
 * The underlying ClinicWebSocketClient is kept as a private implementation
 * detail. Future versions could replace it with a different transport
 * (Server-Sent Events, MQTT) without changing the RealtimeManager API.
 *
 * Thread-safe: SharedFlow is designed for multi-collector scenarios.
 */
class RealtimeManager(
    private val context: Context,
    private val database: ClinicDatabase,
    @Suppress("unused") private val sessionRepository: SessionRepository
) {
    companion object {
        private const val TAG = "RealtimeManager"
    }

    private val _events = MutableSharedFlow<RealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<RealtimeEvent.ConnectionState>(
        replay = 1,
        extraBufferCapacity = 4
    )
    val connectionState: SharedFlow<RealtimeEvent.ConnectionState> = _connectionState.asSharedFlow()

    private var wsClient: ClinicWebSocketClient? = null

    /**
     * Starts the WebSocket connection. Called when user logs in.
     * If already connected, this is a no-op.
     */
    fun start() {
        if (wsClient != null) {
            Log.d(TAG, "start() called but WebSocket already exists — ignoring")
            return
        }

        Log.i(TAG, "Starting real-time connection")
        wsClient = ClinicWebSocketClient.getInstance(context, database)
        wsClient?.start()

        // Note: the actual event emission happens via the callback bridge
        // below. ClinicWebSocketClient.handleSocketMessage writes to Room
        // and sends notifications directly — in a future refactor, we'll
        // move that logic here and emit RealtimeEvent instead.
    }

    /**
     * Stops the WebSocket connection. Called when user logs out.
     */
    fun stop() {
        Log.i(TAG, "Stopping real-time connection")
        wsClient?.stop()
        wsClient = null
        _connectionState.tryEmit(RealtimeEvent.ConnectionState.Disconnected)
    }

    /**
     * Forces a reconnect. Called by NetworkMonitor when network is restored.
     */
    fun reconnect() {
        Log.i(TAG, "Forcing reconnect")
        wsClient?.start(forceReconnect = true)
    }

    /**
     * Emits an event to all subscribers. Called by the WebSocket event
     * bridge when a message is received.
     *
     * Currently, ClinicWebSocketClient handles events internally (writes
     * to Room, sends notifications). This method is the bridge for
     * future migration where RealtimeManager becomes the event dispatcher
     * and ViewModels handle all side effects.
     */
    fun emitEvent(event: RealtimeEvent) {
        _events.tryEmit(event)
    }
}

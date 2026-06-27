package com.aistudio.clinicsystem.data.realtime

import android.content.Context
import android.util.Log
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.session.SessionState
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.utils.ClinicWebSocketClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 2.3: RealtimeManager — the SINGLE WebSocket owner for the app.
 *
 * Closes audit findings H-3, NET-11, NET-12, NET-13, NET-14, NET-18,
 * NET-19, PERF-12, M-4.
 *
 * Key properties:
 * 1. `@Singleton` — exactly one instance per application. NetworkMonitor,
 *    ClinicViewModel, and tests all see the same instance.
 * 2. Tied to [SessionRepository.sessionState] — connects when
 *    `Authenticated`, disconnects on `Unauthenticated`/`SessionExpired`.
 * 3. Single [CoroutineScope] with [SupervisorJob] — no per-event scope
 *    creation (closes NET-12).
 * 4. Subscribes to access-token changes — when the token is rotated
 *    (post-refresh), closes the current socket and reconnects with the
 *    new token (closes NET-18).
 * 5. The underlying [ClinicWebSocketClient] is a private implementation
 *    detail — ViewModels never touch it directly.
 *
 * Lifecycle:
 *   - [start] / [stop] are called by [com.aistudio.clinicsystem.ClinicSystemApplication]
 *     via ProcessLifecycleOwner (ON_START → start, ON_STOP → stop).
 *   - [reconnectNow] is called by NetworkMonitor.onAvailable (no backoff).
 *   - On session state change → auto start/stop.
 *   - On access-token change → reconnect with new token.
 */
@Singleton
class RealtimeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ClinicDatabase,
    private val sessionRepository: SessionRepository,
) {
    companion object {
        private const val TAG = "RealtimeManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionObserverJob: Job? = null
    private var tokenObserverJob: Job? = null

    private val _events = MutableSharedFlow<RealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<RealtimeEvent.ConnectionState>(
        RealtimeEvent.ConnectionState.Disconnected,
    )
    val connectionState: StateFlow<RealtimeEvent.ConnectionState> = _connectionState.asStateFlow()

    // The underlying client. Singleton via ClinicWebSocketClient.getInstance().
    // Kept private — ViewModels interact only through events / connectionState.
    private var wsClient: ClinicWebSocketClient? = null

    /**
     * Initializes the manager. Called by Application.onCreate.
     *
     * Sets up two long-lived observers:
     * 1. Session state observer — auto-starts/stops the WebSocket based on
     *    auth state.
     * 2. Access-token observer — when the token changes (post-refresh),
     *    reconnects with the new token.
     *
     * Both observers run in [scope] — they survive ViewModel destruction.
     */
    fun initialize() {
        if (sessionObserverJob?.isActive == true) {
            Log.d(TAG, "initialize() called but already initialized — ignoring")
            return
        }

        // Observer 1: session state → start/stop
        sessionObserverJob = scope.launch {
            sessionRepository.sessionState.collectLatest { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        // Token might be the same as before, but ensure the
                        // socket is up. The tokenObserver below handles
                        // mid-session token rotation.
                        ensureStarted()
                    }
                    is SessionState.RequiresTwoFactor,
                    SessionState.Unauthenticated,
                    SessionState.SessionExpired,
                    SessionState.Loading -> {
                        ensureStopped()
                    }
                }
            }
        }

        // Observer 2: access-token rotation → reconnect
        // We observe the SessionState (which carries the token) and react
        // to changes from Authenticated(oldToken) to Authenticated(newToken).
        tokenObserverJob = scope.launch {
            var lastToken: String? = null
            sessionRepository.sessionState.collectLatest { state ->
                if (state is SessionState.Authenticated) {
                    if (lastToken != null && lastToken != state.accessToken && wsClient != null) {
                        Log.i(TAG, "Access token rotated — reconnecting WebSocket with new token")
                        // NET-18 fix: reconnect with new token
                        wsClient?.stop()
                        wsClient = null
                        ensureStarted()
                    }
                    lastToken = state.accessToken
                } else {
                    lastToken = null
                }
            }
        }
    }

    /**
     * Idempotent start. Called on ON_START (ProcessLifecycleOwner) and
     * when session becomes Authenticated.
     */
    fun start() {
        ensureStarted()
    }

    private fun ensureStarted() {
        if (wsClient != null) {
            Log.d(TAG, "ensureStarted() — WebSocket already exists, ignoring")
            return
        }
        // Only start if authenticated — don't open a socket to a server we
        // can't authenticate to.
        val state = sessionRepository.sessionState.value
        if (state !is SessionState.Authenticated) {
            Log.d(TAG, "ensureStarted() — session not Authenticated ($state), skipping")
            return
        }
        Log.i(TAG, "Starting real-time WebSocket connection")
        wsClient = ClinicWebSocketClient.getInstance(context, database)
        wsClient?.start()
        _connectionState.value = RealtimeEvent.ConnectionState.Connected
    }

    /**
     * Idempotent stop. Called on ON_STOP and on session invalidation.
     */
    fun stop() {
        ensureStopped()
    }

    private fun ensureStopped() {
        if (wsClient == null) return
        Log.i(TAG, "Stopping real-time WebSocket connection")
        try {
            wsClient?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WebSocket cleanly", e)
        }
        wsClient = null
        _connectionState.value = RealtimeEvent.ConnectionState.Disconnected
    }

    /**
     * Forces a reconnect — called by NetworkMonitor.onAvailable when
     * network is restored after being lost. No backoff (the user just
     * got network back; they want updates NOW).
     *
     * Closes H-3 / NET-13: NetworkMonitor must call this on the SAME
     * singleton instance — Hilt guarantees that.
     */
    fun reconnectNow() {
        Log.i(TAG, "reconnectNow() — forcing reconnect (network restored)")
        if (wsClient != null) {
            try {
                wsClient?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop WebSocket before reconnect", e)
            }
            wsClient = null
        }
        ensureStarted()
    }

    /**
     * Emits an event to all subscribers. Bridge method for the
     * ClinicWebSocketClient callback path. In a future refactor,
     * ClinicWebSocketClient will call this instead of writing to Room
     * directly — ViewModels will handle all side effects.
     */
    fun emitEvent(event: RealtimeEvent) {
        _events.tryEmit(event)
    }
}

package com.aistudio.clinicsystem.utils

import android.content.Context
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import timber.log.Timber

/**
 * ClinicWebSocketClient connects to the real-time endpoint of the FastAPI 'final' backend.
 * It listens for server side socket events (Queue positions, Appointment Status changes,
 * or newly issued Medical Reports), automatically syncs local Room data structures, and
 * launches native push alerts immediately via NotificationHelper.
 *
 * Stage 3: now @Singleton + @Inject — no more getInstance() singleton.
 * Hilt guarantees a single instance for the entire app lifecycle.
 */
@javax.inject.Singleton
class ClinicWebSocketClient
    @javax.inject.Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
        private val database: ClinicDatabase,
        // P0-2 audit fix: inject SessionRepository instead of constructing a new
        // instance on every start(). Reuses the same Hilt-managed singleton that
        // RealtimeManager uses — no more double-instantiation of SessionManagerImpl.
        private val sessionRepository: com.aistudio.clinicsystem.data.session.SessionRepository,
    ) {
        private val client =
            OkHttpClient
                .Builder()
                .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS) // Heartbeat (Ping/Pong)
                .build()
        private var webSocket: WebSocket? = null
        private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

        @Volatile
        private var isClosedManually = false
        private var baseBackoffTimeMs = 2000L
        private val maxBackoffTimeMs = 60000L
        private var reconnectAttempt = 0
        private var reconnectJob: kotlinx.coroutines.Job? = null

        // TASK-8: room targeting — the backend broadcasts into per-specialist
        // rooms "specialist_{id}::{date}". The client must subscribe to the
        // ALLOWED queue of the selected specialist and date, not a fictitious
        // "general" room.
        @Volatile
        private var targetDepartment: String = "general"

        @Volatile
        private var targetDate: String = ""

        /**
         * TASK-8: partial queue events (no full snapshot in the payload). The
         * client MUST re-read the affected queue through REST instead of
         * interpreting the event as an empty queue. Emits the department
         * name of the room the event belongs to.
         */
        private val _partialQueueEvents =
            kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
        val partialQueueEvents: kotlinx.coroutines.flow.SharedFlow<String> = _partialQueueEvents

        /** TASK-8: emitted after (re)connect so a recovery sync can run. */
        private val _recoveryEvents =
            kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
        val recoveryEvents: kotlinx.coroutines.flow.SharedFlow<String> = _recoveryEvents

        /**
         * TASK-8: subscribe to a specialist queue room. Call with the
         * doctor's backend id and the queue day; reconnects the socket when
         * the target room changes. [date] blank = today.
         */
        @Synchronized
        fun subscribeToQueue(
            department: String,
            date: String,
        ) {
            val changed = department != targetDepartment || date != targetDate
            targetDepartment = department
            targetDate = date
            if (changed && webSocket != null) {
                Timber.i("WS room changed → reconnect: %s::%s", department, date)
                start(forceReconnect = true)
            }
        }

        // P0-2 audit fix: shared Moshi instance — Moshi is thread-safe and
        // constructing a new Builder per message was wasteful (allocation
        // pressure + adapter re-generation).
        private val sharedMoshi: com.squareup.moshi.Moshi by lazy {
            com.squareup.moshi.Moshi
                .Builder()
                .build()
        }

        @Synchronized
        fun start(forceReconnect: Boolean = false) {
            if (!forceReconnect && webSocket != null && !isClosedManually) {
                return
            }
            reconnectJob?.cancel()
            reconnectJob = null

            if (webSocket != null) {
                try {
                    webSocket?.close(1000, "Client reconnecting")
                } catch (e: Exception) {
                    // Ignore close errors
                }
                webSocket = null
            }
            // M3B.1: use SessionRepository as SSOT for token access.
            // P0-2 audit fix: inject SessionRepository via constructor instead
            // of constructing a new instance on every start(). Reuses the same
            // Hilt-managed singleton that RealtimeManager already uses.
            val token = sessionRepository.accessToken
            // E2.7: WEBSOCKET_URL is now baked into BuildConfig per build type.
            val wsBaseUrl = com.aistudio.clinicsystem.BuildConfig.WEBSOCKET_URL

            // P0-2 audit fix: backend /ws/queue REQUIRES `department` and `date`
            // query parameters (see backend/app/ws/queue_ws.py:294-296).
            // Without them the client subscribes to room "::" and receives no
            // real queue updates. We default to "general" department and today's
            // date — staff users with a specific department will get a separate
            // reconnect with their department once StaffViewModel is wired to
            // pass it (High-6 followup).
            val wsUrl = buildWebSocketUrlWithQueryParams(wsBaseUrl)

            val requestBuilder =
                Request
                    .Builder()
                    .url(wsUrl)

            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()

            isClosedManually = false
            webSocket = client.newWebSocket(request, ClinicWSListener())
        }

        /**
         * P0-2 audit fix: appends required `department` and `date` query
         * parameters to the WebSocket URL.
         *
         * Backend `/ws/queue` endpoint (queue_ws.py:294-296) declares:
         *   async def ws_queue(websocket, department: str, date: str, token: str | None = None)
         *
         * Without these query params the backend accepts the connection but
         * subscribes the client to room `"::"` (empty department + empty date),
         * so no real queue broadcasts reach the client.
         *
         * Department defaults to "general" — a catch-all room. Staff users
         * will be migrated to per-department subscriptions in a follow-up
         * (audit finding High-6 — StaffScreen split).
         *
         * Date defaults to today in the user's local timezone (YYYY-MM-DD),
         * matching the backend's queue-date format.
         */
        private fun buildWebSocketUrlWithQueryParams(wsBaseUrl: String): String {
            val today =
                if (targetDate.isBlank()) {
                    java.time.LocalDate
                        .now()
                        .toString()
                } else {
                    targetDate
                }
            val department = targetDepartment

            // Append query params, preserving any existing query string.
            val separator = if (wsBaseUrl.contains('?')) '&' else '?'
            return "$wsBaseUrl${separator}department=$department&date=$today"
        }

        @Synchronized
        fun stop() {
            isClosedManually = true
            reconnectJob?.cancel()
            reconnectJob = null
            scope.coroutineContext.cancelChildren()
            try {
                webSocket?.close(1000, "App closed session")
            } catch (e: Exception) {
                // Ignore close errors
            }
            webSocket = null
        }

        private inner class ClinicWSListener : WebSocketListener() {
            private var loggedError = false

            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                if (webSocket != this@ClinicWebSocketClient.webSocket) {
                    Timber.w("Ignoring onOpen from old or mismatched WebSocket instance.")
                    return
                }
                Timber.i("WebSocket successfully connected to ${response.request.url}")
                loggedError = false
                reconnectAttempt = 0 // Reset exponential backoff on successful connect
                com.aistudio.clinicsystem.utils.SyncMetricsManager
                    .updateWsState("CONNECTED")

                // P0-2 audit fix: removed the `subscribe` handshake.
                //
                // The backend `/ws/queue` endpoint (queue_ws.py:294-296) does NOT
                // expect a subscribe message — it accepts the connection, sends
                // `{"type":"queue.connected","room":"..."}`, and immediately starts
                // streaming queue broadcasts to the room identified by the
                // `department` + `date` query params in the URL.
                //
                // The previous subscribe handshake `{"type":"subscribe","channel":"my_queue"}`
                // was silently ignored by the backend — it consumed the message
                // via `receive_text()` and discarded it (no handler matched the
                // `subscribe` type). Sending it was harmless but wasted bandwidth
                // and gave the impression of a contract that doesn't exist.
                //
                // The real subscription is the `department` + `date` query params
                // on the WebSocket URL — see buildWebSocketUrlWithQueryParams().
                scope.launch {
                    database.syncLogDao().insertLog(
                        com.aistudio.clinicsystem.data.db.SyncLogEntity(
                            logMessage = "🟢 WebSocket подключён к /ws/queue (room: $targetDepartment::$targetDate). Ожидание событий.",
                            direction = "SYSTEM_SYNC",
                        ),
                    )
                    // TASK-8: recovery sync — after every (re)connect the
                    // client MUST re-read the queue through REST, because
                    // events missed during the disconnect are lost.
                    _recoveryEvents.tryEmit("$targetDepartment::$targetDate")
                }
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                if (webSocket != this@ClinicWebSocketClient.webSocket) {
                    Timber.w("Ignoring onMessage from old or mismatched WebSocket instance.")
                    return
                }
                Timber.d("Received message text: $text")
                handleSocketMessage(text)
            }

            override fun onMessage(
                webSocket: WebSocket,
                bytes: ByteString,
            ) {
                if (webSocket != this@ClinicWebSocketClient.webSocket) return
                Timber.d("Received message bytes")
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                if (webSocket != this@ClinicWebSocketClient.webSocket) return
                Timber.w("WebSocket closing: $code / $reason")
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                Timber.w("WebSocket closed: $code / $reason")
                if (webSocket != this@ClinicWebSocketClient.webSocket) {
                    Timber.w("Ignoring onClosed from old or mismatched WebSocket instance.")
                    return
                }
                com.aistudio.clinicsystem.utils.SyncMetricsManager
                    .updateWsState("DISCONNECTED")
                reconnectIfNeeded()
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                Timber.e(t, "WebSocket failure: ${t.message}")
                if (webSocket != this@ClinicWebSocketClient.webSocket) {
                    Timber.w("Ignoring onFailure from old or mismatched WebSocket instance.")
                    return
                }
                com.aistudio.clinicsystem.utils.SyncMetricsManager
                    .updateWsState("DISCONNECTED")
                if (!loggedError) {
                    loggedError = true
                    scope.launch {
                        database.syncLogDao().insertLog(
                            com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                logMessage = "🔴 Сбой подключения вебсокета: Сервер final временно недоступен. Ожидание сессии...",
                                direction = "SYSTEM_SYNC",
                            ),
                        )
                    }
                }
                reconnectIfNeeded()
            }
        }

        @Synchronized
        private fun reconnectIfNeeded() {
            if (!isClosedManually && reconnectJob == null) {
                com.aistudio.clinicsystem.utils.SyncMetricsManager
                    .updateWsState("RECONNECTING")
                reconnectJob =
                    scope.launch {
                        val backoff =
                            (
                                baseBackoffTimeMs *
                                    Math.pow(
                                        2.0,
                                        reconnectAttempt.toDouble(),
                                    )
                            ).toLong().coerceAtMost(maxBackoffTimeMs)
                        val jitter = (Math.random() * 1000).toLong()
                        delay(backoff + jitter)
                        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(20) // NET-10: cap at 20 to prevent overflow
                        Timber.w("Attempting websocket reconnect... retry $reconnectAttempt after ${backoff + jitter}ms")
                        start(forceReconnect = true)
                    }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun handleSocketMessage(json: String) {
            scope.launch {
                try {
                    // P0-2 audit fix: reuse a single Moshi instance instead of
                    // constructing one per message. Moshi is thread-safe.
                    val moshi = sharedMoshi
                    val baseAdapter = moshi.adapter(BaseWsEvent::class.java)
                    val baseEvent = baseAdapter.fromJson(json)

                    // P0-2 audit fix: use effectiveType (prefers `type`, falls back
                    // to legacy `event`) — backend uses `type` field.
                    val eventType = baseEvent?.effectiveType

                    if (eventType.isNullOrEmpty()) {
                        Timber.w("WS message has no `type` or `event` field — dropping: %s", json.take(200))
                        return@launch
                    }

                    Timber.i("Broadcasting backend event: $eventType")

                    when (eventType) {
                        // ─── P0-2 audit fix: backend heartbeat ───────────────────────
                        // Backend sends `{"type":"ping","timestamp":...}` every 30s.
                        // We reply with `{"type":"pong"}` to keep the connection
                        // alive (backend closes the socket if no pong within
                        // CONNECTION_TIMEOUT = 120s — see queue_ws.py:349).
                        "ping" -> {
                            try {
                                webSocket?.send("""{"type":"pong"}""")
                                Timber.v("Replied to backend ping with pong")
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to send pong reply")
                            }
                        }

                        // ─── P0-2 audit fix: subscription confirmed ─────────────────
                        // Backend sends `{"type":"queue.connected","room":"general::2026-07-10"}`
                        // immediately after onOpen. We log it; no further action.
                        "queue.connected" -> {
                            val adapter = moshi.adapter(WsQueueConnectedEvent::class.java)
                            val event = adapter.fromJson(json)
                            Timber.i("WS subscription confirmed for room: ${event?.room}")
                            scope.launch {
                                database.syncLogDao().insertLog(
                                    com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                        logMessage = "✅ WebSocket подписан на room: ${event?.room ?: "unknown"}",
                                        direction = "SYSTEM_SYNC",
                                    ),
                                )
                            }
                        }

                        // ─── P0-2 audit fix: backend error ──────────────────────────
                        // Backend sends `{"type":"error","reason":"..."}` on
                        // auth or origin failures. We log it and stop the socket
                        // (auth errors require re-login, not reconnect).
                        "error" -> {
                            val adapter = moshi.adapter(WsErrorEvent::class.java)
                            val event = adapter.fromJson(json)
                            val reason = event?.reason ?: "unknown"
                            Timber.e("WS backend error: $reason")
                            scope.launch {
                                database.syncLogDao().insertLog(
                                    com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                        logMessage = "🔴 WebSocket ошибка от сервера: $reason",
                                        direction = "SYSTEM_SYNC",
                                    ),
                                )
                            }
                            // Auth errors are not recoverable via reconnect.
                            // Force-stop — RealtimeManager will restart on next
                            // session state change.
                            if (reason.contains("auth", ignoreCase = true) ||
                                reason.contains("Authentication", ignoreCase = true)
                            ) {
                                stop()
                            }
                        }

                        // ─── P0-2 audit fix: new lowercase backend event types ──────
                        // Backend `broadcast_queue_update(event_type=...)` uses
                        // lowercase event types: `queue_update`, `patient_called`,
                        // `entry_added`. We treat them as QUEUE_UPDATE for now —
                        // a follow-up will dispatch them to dedicated handlers
                        // once the backend stabilises its event taxonomy.
                        "queue_update", "patient_called", "entry_added" -> {
                            handleQueueUpdateEvent(moshi, json)
                        }

                        // ─── Legacy event types (kept for backward compat) ────────
                        "APPOINTMENT_STATUS" -> handleAppointmentStatusEvent(moshi, json)
                        "NEW_MEDICAL_RECORD" -> handleNewMedicalRecordEvent(moshi, json)
                        "QUEUE_UPDATE" -> handleQueueUpdateEvent(moshi, json)

                        // ─── Unknown event types ──────────────────────────────────
                        else -> {
                            Timber.w("Unknown WS event type '$eventType' — dropping: %s", json.take(200))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed processing event payload: ${e.message}")
                }
            }
        }

        private fun handleAppointmentStatusEvent(
            moshi: com.squareup.moshi.Moshi,
            json: String,
        ) {
            // BUILD-FIX: the DAO layer is suspend — run the handler on the
            // client IO scope. Malformed payloads are caught so a bad event
            // can never kill the coroutine scope.
            scope.launch {
                try {
                    val adapter = moshi.adapter(AppointmentStatusEvent::class.java)
                    val event = adapter.fromJson(json)
                    val data = event?.data ?: return@launch

                    val serverId = data.id ?: -1
                    if (serverId == -1) return@launch

                    val status = data.status ?: "PENDING"
                    val doctorName = data.doctorName ?: "Доктор"
                    val date = data.date ?: ""
                    val time = data.time ?: ""
                    val patientName = data.patientName ?: "Пациент"
                    val patientPhone = data.patientPhone ?: ""

                    // M3B.4: look up by serverId (WS events use backend Int IDs)
                    val appDao = database.appointmentDao()
                    val existing = appDao.getAppointmentByServerId(serverId)

                    val pendingSyncDao = database.pendingSyncDao()
                    val isPending =
                        pendingSyncDao.getAllPendingSyncs().any {
                            it.type == "UPDATE_STATUS" && it.payload.startsWith("$serverId|")
                        }

                    if (isPending) {
                        database.syncLogDao().insertLog(
                            com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                logMessage =
                                    "🛡️ Реконсиляция: Отклонено WS-обновление для приема #$serverId " +
                                        "— есть локальные отложенные изменения.",
                                direction = "SYSTEM_SYNC",
                            ),
                        )
                    } else {
                        // TASK-5: WS events carry backend statuses — normalize
                        // so realtime, REST and background sync agree on the
                        // client vocabulary (PENDING/APPROVED/…).
                        val normalized = ServerStatusMapper.fromServer(status)
                        if (existing != null) {
                            if (existing.status != normalized) {
                                appDao.updateAppointment(
                                    existing.copy(status = normalized, updatedAt = System.currentTimeMillis()),
                                )
                            }
                        } else {
                            appDao.insertAppointment(
                                AppointmentEntity(
                                    id =
                                        java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                    serverId = serverId,
                                    patientPhone = patientPhone,
                                    patientName = patientName,
                                    doctorName = doctorName,
                                    specialty = data.specialty ?: "Терапевт",
                                    date = date,
                                    time = time,
                                    status = normalized,
                                    reason = data.reason ?: "",
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        }

                        database.syncLogDao().insertLog(
                            com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                logMessage = "⚡ Реалтайм-обновление: Прием #$serverId теперь имеет статус [ $status ]",
                                direction = "SYSTEM_SYNC",
                            ),
                        )

                        NotificationHelper.sendAppointmentStatusNotification(
                            context = context,
                            appointmentId = serverId,
                            doctorName = doctorName,
                            dateTimeString = "$date в $time",
                            newStatus = status,
                            patientName = patientName,
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed processing appointment status event: " + e.message)
                }
            }
        }

        private fun handleNewMedicalRecordEvent(
            moshi: com.squareup.moshi.Moshi,
            json: String,
        ) {
            // BUILD-FIX: the DAO layer is suspend — run the handler on the
            // client IO scope. Malformed payloads are caught so a bad event
            // can never kill the coroutine scope.
            scope.launch {
                try {
                    val adapter = moshi.adapter(NewMedicalRecordEvent::class.java)
                    val event = adapter.fromJson(json)
                    val data = event?.data ?: return@launch

                    val recordServerId = data.id ?: 0
                    val patientPhone = data.patientPhone ?: ""
                    val doctorName = data.doctorName ?: "Врач"
                    val diagnosis = data.diagnosis ?: ""
                    val prescription = data.prescription ?: ""
                    val visitDate = data.visitDate ?: ""
                    val recommendations = data.recommendations ?: ""

                    val recordDao = database.medicalRecordDao()
                    val recordEntity =
                        MedicalRecordEntity(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            serverId = recordServerId,
                            patientPhone = patientPhone,
                            doctorName = doctorName,
                            diagnosis = diagnosis,
                            prescription = prescription,
                            visitDate = visitDate,
                            recommendations = recommendations,
                        )
                    recordDao.insertRecord(recordEntity)

                    database.syncLogDao().insertLog(
                        com.aistudio.clinicsystem.data.db.SyncLogEntity(
                            logMessage = "⚡ Реалтайм-обновление: Добавлена новая медкарта пациента ($patientPhone)",
                            direction = "SYSTEM_SYNC",
                        ),
                    )

                    val patientUser = database.userDao().getUserByPhone(patientPhone)
                    val patientName = patientUser?.fullName ?: "Пациент"

                    NotificationHelper.sendMedicalRecordNotification(
                        context = context,
                        recordId = recordServerId,
                        doctorName = doctorName,
                        diagnosis = diagnosis,
                        patientName = patientName,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed processing new medical record event: " + e.message)
                }
            }
        }

        private fun handleQueueUpdateEvent(
            moshi: com.squareup.moshi.Moshi,
            json: String,
        ) {
            // BUILD-FIX: the DAO layer is suspend — run the handler on the
            // client IO scope. Malformed payloads are caught so a bad event
            // can never kill the coroutine scope.
            scope.launch {
                try {
                    val adapter = moshi.adapter(QueueUpdateEvent::class.java)
                    val event = adapter.fromJson(json)
                    val room = event?.room ?: "$targetDepartment::$targetDate"

                    // TASK-8: a partial event (backend `broadcast_queue_update`
                    // sends data={"action":…,"entry_id":…} with NO queue array)
                    // must NEVER be interpreted as an empty queue — keep the
                    // cache intact and re-read the affected queue via REST.
                    if (event?.data?.queue == null) {
                        Timber.i("Partial queue event (room=%s) — REST re-read scheduled", room)
                        database.syncLogDao().insertLog(
                            com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                logMessage = "⚡ Событие очереди без снимка ($room) — перечитываем очередь через REST.",
                                direction = "SYSTEM_SYNC",
                            ),
                        )
                        _partialQueueEvents.tryEmit(room)
                        return@launch
                    }

                    val activeQueueList = event.data!!.queue
                    Timber.i("Queue snapshot length: ${activeQueueList.size} (room=$room)")

                    // Codex P1 (#150): a full snapshot belongs to the ROOM it
                    // was broadcast to — `specialist_{id}::{date}`. It must
                    // replace ONLY that specialist's cached rows; clearing the
                    // whole table would erase every other doctor's actionable
                    // queue entries.
                    val specialistMatch = Regex("^specialist_(\\d+)::(.+)$").find(room)
                    if (specialistMatch == null) {
                        // Unknown room (e.g. the legacy "general") — the
                        // snapshot cannot be attributed to a queue. Keep the
                        // cache intact and re-read the affected queue via REST.
                        Timber.w("Queue snapshot for non-specialist room %s — REST re-read scheduled", room)
                        database.syncLogDao().insertLog(
                            com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                logMessage = "⚡ Снимок очереди для комнаты $room не привязан к специалисту — перечитываем через REST.",
                                direction = "SYSTEM_SYNC",
                            ),
                        )
                        _partialQueueEvents.tryEmit(room)
                        return@launch
                    }
                    val specialistId = specialistMatch.groupValues[1].toInt()
                    val snapshotDay = specialistMatch.groupValues[2]
                    // Carry the REST-established queue identity (queue_id) so
                    // WS and REST refreshes stay the same logical queue.
                    val previousRows =
                        database
                            .queueSnapshotDao()
                            .getAllQueueSnapshots()
                            .filter { it.specialistId == specialistId }
                    val carriedQueueId = previousRows.firstOrNull()?.queueId
                    val carriedDay = previousRows.firstOrNull()?.day?.takeIf { it.isNotBlank() } ?: snapshotDay

                    // FULL SNAPSHOT for THIS room — replace that specialist's
                    // rows inside the Room cache (identity fields preserved).
                    database.queueSnapshotDao().deleteBySpecialist(specialistId)
                    val snapshotsList =
                        activeQueueList.map { dto ->
                            com.aistudio.clinicsystem.data.db.QueueSnapshotEntity(
                                id = dto.id,
                                patientName = dto.patientName,
                                appointmentId = dto.appointmentId,
                                position = dto.position,
                                status = dto.status,
                                timestamp = System.currentTimeMillis(),
                                queueId = carriedQueueId,
                                specialistId = specialistId,
                                day = carriedDay,
                            )
                        }
                    database.queueSnapshotDao().insertQueueSnapshots(snapshotsList)

                    database.syncLogDao().insertLog(
                        com.aistudio.clinicsystem.data.db.SyncLogEntity(
                            logMessage =
                                "⚡ Реалтайм-обновление очереди: ${activeQueueList.size} пациент(ов) " +
                                    "сейчас ожидает (сохранено в кэш)",
                            direction = "SYSTEM_SYNC",
                        ),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed processing queue update event: " + e.message)
                }
            }
        }
    }

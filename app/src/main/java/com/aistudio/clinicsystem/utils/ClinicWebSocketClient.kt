package com.aistudio.clinicsystem.utils

import android.content.Context
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import timber.log.Timber
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString

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
class ClinicWebSocketClient @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val database: ClinicDatabase
) {
    private val client = OkHttpClient.Builder()
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
        // M3B.1: use SessionRepository as SSOT for token access
        val token = com.aistudio.clinicsystem.data.session.SessionRepository(
            SessionManagerImpl.getInstance(context)
        ).accessToken
        // E2.7: WEBSOCKET_URL is now baked into BuildConfig per build type.
        val wsUrl = com.aistudio.clinicsystem.BuildConfig.WEBSOCKET_URL

        val requestBuilder = Request.Builder()
            .url(wsUrl)

        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        isClosedManually = false
        webSocket = client.newWebSocket(request, ClinicWSListener())
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

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket != this@ClinicWebSocketClient.webSocket) {
                Timber.w("Ignoring onOpen from old or mismatched WebSocket instance.")
                return
            }
            Timber.i("WebSocket successfully connected to ${response.request.url}")
            loggedError = false
            reconnectAttempt = 0 // Reset exponential backoff on successful connect
            com.aistudio.clinicsystem.utils.SyncMetricsManager.updateWsState("CONNECTED")

            // M1/E3.6: send subscribe handshake so the backend starts pushing
            // queue updates for this user. The backend's /ws/queue endpoint
            // expects a JSON message of the form:
            //   {"type": "subscribe", "channel": "my_queue"}
            // to begin streaming updates. Without this handshake, the socket
            // is open but no messages will arrive.
            val subscribeMsg = """{"type":"subscribe","channel":"my_queue"}"""
            try {
                webSocket.send(subscribeMsg)
                Timber.d("Sent subscribe handshake: $subscribeMsg")
            } catch (e: Exception) {
                Timber.w("Failed to send subscribe handshake: ${e.message}")
            }

            scope.launch {
                database.syncLogDao().insertLog(
                    com.aistudio.clinicsystem.data.db.SyncLogEntity(
                        logMessage = "🟢 Вебсокет подключен к /ws/queue, отправлен handshake subscribe",
                        direction = "SYSTEM_SYNC"
                    )
                )
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket != this@ClinicWebSocketClient.webSocket) {
                Timber.w("Ignoring onMessage from old or mismatched WebSocket instance.")
                return
            }
            Timber.d("Received message text: $text")
            handleSocketMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket != this@ClinicWebSocketClient.webSocket) return
            Timber.d("Received message bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket != this@ClinicWebSocketClient.webSocket) return
            Timber.w("WebSocket closing: $code / $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.w("WebSocket closed: $code / $reason")
            if (webSocket != this@ClinicWebSocketClient.webSocket) {
                Timber.w("Ignoring onClosed from old or mismatched WebSocket instance.")
                return
            }
            com.aistudio.clinicsystem.utils.SyncMetricsManager.updateWsState("DISCONNECTED")
            reconnectIfNeeded()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "WebSocket failure: ${t.message}")
            if (webSocket != this@ClinicWebSocketClient.webSocket) {
                Timber.w("Ignoring onFailure from old or mismatched WebSocket instance.")
                return
            }
            com.aistudio.clinicsystem.utils.SyncMetricsManager.updateWsState("DISCONNECTED")
            if (!loggedError) {
                loggedError = true
                scope.launch {
                    database.syncLogDao().insertLog(
                        com.aistudio.clinicsystem.data.db.SyncLogEntity(
                            logMessage = "🔴 Сбой подключения вебсокета: Сервер final временно недоступен. Ожидание сессии...",
                            direction = "SYSTEM_SYNC"
                        )
                    )
                }
            }
            reconnectIfNeeded()
        }
    }

    @Synchronized
    private fun reconnectIfNeeded() {
        if (!isClosedManually && reconnectJob == null) {
            com.aistudio.clinicsystem.utils.SyncMetricsManager.updateWsState("RECONNECTING")
            reconnectJob = scope.launch {
                val backoff = (baseBackoffTimeMs * Math.pow(2.0, reconnectAttempt.toDouble())).toLong().coerceAtMost(maxBackoffTimeMs)
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
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val baseAdapter = moshi.adapter(BaseWsEvent::class.java)
                val baseEvent = baseAdapter.fromJson(json)
                val eventType = baseEvent?.event
                
                if (eventType.isNullOrEmpty()) return@launch

                Timber.i("Broadcasting backend event: $eventType")

                when (eventType) {
                    "APPOINTMENT_STATUS" -> {
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
                        val isPending = pendingSyncDao.getAllPendingSyncs().any { 
                            it.type == "UPDATE_STATUS" && it.payload.startsWith("$serverId|") 
                        }
                        
                        if (isPending) {
                            database.syncLogDao().insertLog(
                                com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                    logMessage = "🛡️ Реконсиляция: Отклонено WS-обновление для приема #$serverId — есть локальные отложенные изменения.",
                                    direction = "SYSTEM_SYNC"
                                )
                            )
                        } else {
                            if (existing != null) {
                                if (existing.status != status) {
                                    appDao.updateAppointment(existing.copy(status = status, updatedAt = System.currentTimeMillis()))
                                }
                            } else {
                                appDao.insertAppointment(
                                    AppointmentEntity(
                                        id = java.util.UUID.randomUUID().toString(),
                                        serverId = serverId,
                                        patientPhone = patientPhone,
                                        patientName = patientName,
                                        doctorName = doctorName,
                                        specialty = data.specialty ?: "Терапевт",
                                        date = date,
                                        time = time,
                                        status = status,
                                        reason = data.reason ?: "",
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }

                            // 2. Add System Log
                            database.syncLogDao().insertLog(
                                com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                    logMessage = "⚡ Реалтайм-обновление: Прием #$serverId теперь имеет статус [ $status ]",
                                    direction = "SYSTEM_SYNC"
                                )
                            )

                            // 3. Dispatch Local Push Alert
                            NotificationHelper.sendAppointmentStatusNotification(
                                context = context,
                                appointmentId = serverId,
                                doctorName = doctorName,
                                dateTimeString = "$date в $time",
                                newStatus = status,
                                patientName = patientName
                            )
                        }
                    }

                    "NEW_MEDICAL_RECORD" -> {
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

                        // 1. Save locally in Room cache
                        val recordDao = database.medicalRecordDao()
                        val recordEntity = MedicalRecordEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            serverId = recordServerId,
                            patientPhone = patientPhone,
                            doctorName = doctorName,
                            diagnosis = diagnosis,
                            prescription = prescription,
                            visitDate = visitDate,
                            recommendations = recommendations
                        )
                        recordDao.insertRecord(recordEntity)

                        // 2. Log in sync log
                        database.syncLogDao().insertLog(
                            com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                logMessage = "⚡ Реалтайм-обновление: Добавлена новая медкарта пациента ($patientPhone)",
                                direction = "SYSTEM_SYNC"
                            )
                        )

                        // 3. Find Patient name for visual notification
                        val patientUser = database.userDao().getUserByPhone(patientPhone)
                        val patientName = patientUser?.fullName ?: "Пациент"

                        // 4. Trigger Push
                        NotificationHelper.sendMedicalRecordNotification(
                            context = context,
                            recordId = recordServerId,
                            doctorName = doctorName,
                            diagnosis = diagnosis,
                            patientName = patientName
                        )
                    }

                    "QUEUE_UPDATE" -> {
                        val adapter = moshi.adapter(QueueUpdateEvent::class.java)
                        val event = adapter.fromJson(json)
                        val activeQueueList = event?.data?.queue ?: emptyList()
                        Timber.i("Queue length: ${activeQueueList.size}")

                        // Clear and store real-time queue snapshots inside the Room cache
                        database.queueSnapshotDao().clearQueueSnapshots()
                        val snapshotsList = activeQueueList.map { dto ->
                            com.aistudio.clinicsystem.data.db.QueueSnapshotEntity(
                                id = dto.id,
                                patientName = dto.patientName,
                                appointmentId = dto.appointmentId,
                                position = dto.position,
                                status = dto.status,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                        database.queueSnapshotDao().insertQueueSnapshots(snapshotsList)

                        // Clear log and log queue positions
                        database.syncLogDao().insertLog(
                            com.aistudio.clinicsystem.data.db.SyncLogEntity(
                                logMessage = "⚡ Реалтайм-обновление очереди: ${activeQueueList.size} пациент(ов) сейчас ожидает (сохранено в кэш)",
                                direction = "SYSTEM_SYNC"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed processing event payload: ${e.message}")
            }
        }
    }
}

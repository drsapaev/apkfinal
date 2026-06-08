package com.example.utils

import android.content.Context
import android.util.Log
import com.example.data.db.ClinicDatabase
import com.example.data.db.AppointmentEntity
import com.example.data.db.MedicalRecordEntity
import com.example.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString

/**
 * ClinicWebSocketClient connects to the real-time endpoint of the FastAPI 'final' backend.
 * It listens for server side socket events (Queue positions, Appointment Status changes,
 * or newly issued Medical Reports), automatically syncs local Room data structures, and
 * launches native push alerts immediately via NotificationHelper.
 */
class ClinicWebSocketClient(
    private val context: Context,
    private val database: ClinicDatabase
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isClosedManually = false

    fun start() {
        val token = TokenManager.getToken(context)
        val wsUrl = try {
            val configUrl = com.example.BuildConfig.WEBSOCKET_URL
            val targetUrl = if (!configUrl.isNullOrBlank() && configUrl != "?") configUrl else "ws://10.0.2.2:18000/ws"
            if (targetUrl.contains("localhost") || targetUrl.contains("127.0.0.1")) {
                targetUrl.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2")
            } else {
                targetUrl
            }
        } catch (e: Exception) {
            "ws://10.0.2.2:18000/ws"
        }

        // Add bearer token to parameters for WebSocket authentication
        val authenticatedUrl = if (!token.isNullOrBlank()) {
            "$wsUrl?token=$token"
        } else {
            wsUrl
        }

        val request = Request.Builder()
            .url(authenticatedUrl)
            .build()

        isClosedManually = false
        webSocket = client.newWebSocket(request, ClinicWSListener())
    }

    fun stop() {
        isClosedManually = true
        webSocket?.close(1000, "App closed session")
        webSocket = null
    }

    private inner class ClinicWSListener : WebSocketListener() {
        private var loggedError = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i("WS_CLIENT", "WebSocket successfully connected.")
            loggedError = false
            scope.launch {
                database.syncLogDao().insertLog(
                    com.example.data.db.SyncLogEntity(
                        logMessage = "🟢 Вебсокет подключен к серверу final API",
                        direction = "SYSTEM_SYNC"
                    )
                )
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d("WS_CLIENT", "Received message text: $text")
            handleSocketMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d("WS_CLIENT", "Received message bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w("WS_CLIENT", "WebSocket closing: $code / $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w("WS_CLIENT", "WebSocket closed: $code / $reason")
            reconnectIfNeeded()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("WS_CLIENT", "WebSocket failure: ${t.message}", t)
            if (!loggedError) {
                loggedError = true
                scope.launch {
                    database.syncLogDao().insertLog(
                        com.example.data.db.SyncLogEntity(
                            logMessage = "🔴 Сбой подключения вебсокета: Сервер final временно недоступен. Ожидание сессии...",
                            direction = "SYSTEM_SYNC"
                        )
                    )
                }
            }
            reconnectIfNeeded()
        }
    }

    private fun reconnectIfNeeded() {
        if (!isClosedManually) {
            scope.launch {
                delay(15000) // retry after 15 seconds instead of 5 to preserve resource constraints
                Log.w("WS_CLIENT", "Attempting websocket reconnect...")
                start()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleSocketMessage(json: String) {
        scope.launch {
            try {
                // Parse the websocket payload natively using org.json
                val parsed = org.json.JSONObject(json)
                val eventType = parsed.optString("event")
                if (eventType.isNullOrEmpty()) return@launch
                val data = parsed.optJSONObject("data") ?: return@launch

                Log.i("WS_CLIENT", "Broadcasting backend event: $eventType")

                when (eventType) {
                    "APPOINTMENT_STATUS" -> {
                        val id = data.optInt("id", -1)
                        if (id == -1) return@launch
                        val status = data.optString("status", "PENDING")
                        val doctorName = data.optString("doctor_name", "Доктор")
                        val date = data.optString("date", "")
                        val time = data.optString("time", "")
                        val patientName = data.optString("patient_name", "Пациент")
                        val patientPhone = data.optString("patient_phone", "")

                        // 1. Sync SQLite Local DB
                        val appDao = database.appointmentDao()
                        val existing = appDao.getAppointmentById(id)
                        if (existing != null) {
                            appDao.updateAppointment(existing.copy(status = status, updatedAt = System.currentTimeMillis()))
                        } else {
                            appDao.insertAppointment(
                                AppointmentEntity(
                                    id = id,
                                    patientPhone = patientPhone,
                                    patientName = patientName,
                                    doctorName = doctorName,
                                    specialty = data.optString("specialty", "Терапевт"),
                                    date = date,
                                    time = time,
                                    status = status,
                                    reason = data.optString("reason", "")
                                )
                            )
                        }

                        // 2. Add System Log
                        database.syncLogDao().insertLog(
                            com.example.data.db.SyncLogEntity(
                                logMessage = "⚡ Реалтайм-обновление: Прием #$id теперь имеет статус [ $status ]",
                                direction = "SYSTEM_SYNC"
                            )
                        )

                        // 3. Dispatch Local Push Alert
                        NotificationHelper.sendAppointmentStatusNotification(
                            context = context,
                            appointmentId = id,
                            doctorName = doctorName,
                            dateTimeString = "$date в $time",
                            newStatus = status,
                            patientName = patientName
                        )
                    }

                    "NEW_MEDICAL_RECORD" -> {
                        val id = data.optInt("id", 0)
                        val patientPhone = data.optString("patient_phone", "")
                        val doctorName = data.optString("doctor_name", "Врач")
                        val diagnosis = data.optString("diagnosis", "")
                        val prescription = data.optString("prescription", "")
                        val visitDate = data.optString("visit_date", "")
                        val recommendations = data.optString("recommendations", "")

                        // 1. Save locally in Room cache
                        val recordDao = database.medicalRecordDao()
                        val recordEntity = MedicalRecordEntity(
                            id = id,
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
                            com.example.data.db.SyncLogEntity(
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
                            recordId = id,
                            doctorName = doctorName,
                            diagnosis = diagnosis,
                            patientName = patientName
                        )
                    }

                    "QUEUE_UPDATE" -> {
                        val activeQueueList = data.optJSONArray("queue") ?: org.json.JSONArray()
                        Log.i("WS_CLIENT", "Queue length: ${activeQueueList.length()}")

                        // Clear log and log queue positions
                        database.syncLogDao().insertLog(
                            com.example.data.db.SyncLogEntity(
                                logMessage = "⚡ Реалтайм-обновление очереди: ${activeQueueList.length()} пациент(ов) сейчас ожидает",
                                direction = "SYSTEM_SYNC"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("WS_CLIENT", "Failed processing event payload: ${e.message}", e)
            }
        }
    }
}

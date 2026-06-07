package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.database.ClinicDatabase
import com.example.data.database.AppointmentEntity
import com.example.data.database.MedicalRecordEntity
import com.example.data.network.ApiClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

    private val moshi = Moshi.Builder()
        .build()

    private val mapAdapter = moshi.adapter<Map<String, Any>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun start() {
        val token = TokenManager.getToken(context)
        val wsUrl = try {
            val configUrl = com.example.BuildConfig.WEBSOCKET_URL
            val targetUrl = if (!configUrl.isNullOrBlank()) configUrl else "ws://10.0.2.2:18000/ws"
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
                    com.example.data.database.SyncLogEntity(
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
                        com.example.data.database.SyncLogEntity(
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
                val parsed = mapAdapter.fromJson(json) ?: return@launch
                val eventType = parsed["event"] as? String ?: return@launch
                val data = parsed["data"] as? Map<String, Any> ?: return@launch

                Log.i("WS_CLIENT", "Broadcasting backend event: $eventType with values: $data")

                when (eventType) {
                    "APPOINTMENT_STATUS" -> {
                        val id = (data["id"] as? Double)?.toInt() ?: return@launch
                        val status = data["status"] as? String ?: "PENDING"
                        val doctorName = data["doctor_name"] as? String ?: "Доктор"
                        val date = data["date"] as? String ?: ""
                        val time = data["time"] as? String ?: ""
                        val patientName = data["patient_name"] as? String ?: "Пациент"
                        val patientPhone = data["patient_phone"] as? String ?: ""

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
                                    specialty = data["specialty"] as? String ?: "Терапевт",
                                    date = date,
                                    time = time,
                                    status = status,
                                    reason = data["reason"] as? String ?: ""
                                )
                            )
                        }

                        // 2. Add System Log
                        database.syncLogDao().insertLog(
                            com.example.data.database.SyncLogEntity(
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
                        val id = (data["id"] as? Double)?.toInt() ?: 0
                        val patientPhone = data["patient_phone"] as? String ?: ""
                        val doctorName = data["doctor_name"] as? String ?: "Врач"
                        val diagnosis = data["diagnosis"] as? String ?: ""
                        val prescription = data["prescription"] as? String ?: ""
                        val visitDate = data["visit_date"] as? String ?: ""
                        val recommendations = data["recommendations"] as? String ?: ""

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
                            com.example.data.database.SyncLogEntity(
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
                        val activeQueueList = data["queue"] as? List<Map<String, Any>> ?: return@launch
                        Log.i("WS_CLIENT", "Queue length: ${activeQueueList.size}")

                        // Clear log and log queue positions
                        database.syncLogDao().insertLog(
                            com.example.data.database.SyncLogEntity(
                                logMessage = "⚡ Реалтайм-обновление очереди: ${activeQueueList.size} пациент(ов) сейчас ожидает",
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

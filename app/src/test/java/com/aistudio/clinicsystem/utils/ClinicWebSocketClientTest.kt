package com.aistudio.clinicsystem.utils

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Method

/**
 * M3A/E6.3: Unit tests for [ClinicWebSocketClient.handleSocketMessage].
 *
 * Tests the real-time event processing pipeline:
 *   1. APPOINTMENT_STATUS → updates Room, respects reconciliation guard
 *   2. NEW_MEDICAL_RECORD → inserts into Room
 *   3. QUEUE_UPDATE → replaces queue snapshots in Room
 *   4. Malformed JSON → no crash
 *   5. Unknown event type → no crash
 *   6. Missing event field → no crash
 *
 * Strategy:
 *   - Use reflection to call the private handleSocketMessage() directly
 *   - Replace the scope with Dispatchers.Unconfined for synchronous execution
 *   - Use in-memory Room under Robolectric
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ClinicWebSocketClientTest {

    private lateinit var context: Context
    private lateinit var database: ClinicDatabase
    private lateinit var wsClient: ClinicWebSocketClient
    private lateinit var handleMethod: Method

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ClinicDatabase::class.java
        ).allowMainThreadQueries().build()

        // P0-2 audit fix: pass a relaxed mockk SessionRepository to the
        // new 3-arg constructor. The previous test used the 2-arg
        // constructor which no longer exists.
        val sessionRepo = io.mockk.mockk<com.aistudio.clinicsystem.data.session.SessionRepository>(relaxed = true)
        io.mockk.every { sessionRepo.accessToken } returns "fake-test-token"

        wsClient = ClinicWebSocketClient(context, database, sessionRepo)

        // Replace scope with Unconfined for synchronous execution
        val scopeField = ClinicWebSocketClient::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        scopeField.set(wsClient, CoroutineScope(Dispatchers.Unconfined + SupervisorJob()))

        handleMethod = ClinicWebSocketClient::class.java.getDeclaredMethod(
            "handleSocketMessage",
            String::class.java
        )
        handleMethod.isAccessible = true
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun handleMessage(json: String) = runBlocking {
        handleMethod.invoke(wsClient, json)
        delay(100)
    }

    // ─── APPOINTMENT_STATUS ──────────────────────────────────────────

    @Test
    fun `APPOINTMENT_STATUS updates existing appointment in Room`() {
        runBlocking {
            database.appointmentDao().insertAppointment(
                com.aistudio.clinicsystem.data.db.AppointmentEntity(
                    id = "42", patientPhone = "+77771112233", patientName = "Patient",
                    doctorName = "Dr. Smith", specialty = "Cardiology",
                    date = "2026-07-01", time = "10:00", status = "PENDING",
                    reason = "Checkup"
                )
            )
        }

        handleMessage("""{"event":"APPOINTMENT_STATUS","data":{"id":42,"status":"APPROVED","doctor_name":"Dr. Smith","date":"2026-07-01","time":"10:00","patient_name":"Patient","patient_phone":"+77771112233"}}""")

        runBlocking {
            val updated = database.appointmentDao().getAppointmentById("42")
            assertNotNull("Appointment should exist", updated)
            assertEquals("APPROVED", updated?.status)
        }
    }

    @Test
    fun `APPOINTMENT_STATUS creates new appointment if not in Room`() {
        handleMessage("""{"event":"APPOINTMENT_STATUS","data":{"id":99,"status":"PENDING","doctor_name":"Dr. New","date":"2026-08-01","time":"14:00","patient_name":"New Patient","patient_phone":"+77001112233","specialty":"Neurology","reason":"New visit"}}""")

        runBlocking {
            val created = database.appointmentDao().getAppointmentById("99")
            assertNotNull("Appointment should be created", created)
            assertEquals("Dr. New", created?.doctorName)
            assertEquals("PENDING", created?.status)
        }
    }

    @Test
    fun `APPOINTMENT_STATUS reconciliation guard blocks update when pending sync exists`() {
        runBlocking {
            database.appointmentDao().insertAppointment(
                com.aistudio.clinicsystem.data.db.AppointmentEntity(
                    id = "10", patientPhone = "+77771112233", patientName = "P",
                    doctorName = "Dr.", specialty = "S", date = "2026-07-01", time = "10:00",
                    status = "PENDING", reason = "R"
                )
            )
            database.pendingSyncDao().insertPendingSync(
                PendingSyncEntity(
                    type = "UPDATE_STATUS",
                    payload = "10|APPROVED|notes",
                    clientRequestId = "req-123"
                )
            )
        }

        handleMessage("""{"event":"APPOINTMENT_STATUS","data":{"id":10,"status":"CANCELLED","doctor_name":"Dr.","date":"2026-07-01","time":"10:00","patient_name":"P","patient_phone":"+77771112233"}}""")

        runBlocking {
            val appointment = database.appointmentDao().getAppointmentById("10")
            assertEquals("Should remain PENDING (reconciliation guard)", "PENDING", appointment?.status)
        }
    }

    @Test
    fun `APPOINTMENT_STATUS with null id does not crash`() {
        handleMessage("""{"event":"APPOINTMENT_STATUS","data":{"status":"APPROVED"}}""")
    }

    // ─── NEW_MEDICAL_RECORD ──────────────────────────────────────────

    @Test
    fun `NEW_MEDICAL_RECORD inserts record into Room`() {
        handleMessage("""{"event":"NEW_MEDICAL_RECORD","data":{"id":55,"patient_phone":"+77771112233","doctor_name":"Dr. House","diagnosis":"Lupus","prescription":"Steroids","visit_date":"2026-06-15","recommendations":"Rest"}}""")

        runBlocking {
            val record = database.medicalRecordDao().getRecordById("55")
            assertNotNull("Medical record should be inserted", record)
            assertEquals("Dr. House", record?.doctorName)
            assertEquals("Lupus", record?.diagnosis)
        }
    }

    @Test
    fun `NEW_MEDICAL_RECORD with null fields uses defaults`() {
        handleMessage("""{"event":"NEW_MEDICAL_RECORD","data":{"id":56,"patient_phone":"+77771112233"}}""")

        runBlocking {
            val record = database.medicalRecordDao().getRecordById("56")
            assertNotNull("Record should be inserted even with null fields", record)
            assertEquals("", record?.diagnosis)
        }
    }

    // ─── QUEUE_UPDATE ────────────────────────────────────────────────

    @Test
    fun `QUEUE_UPDATE replaces queue snapshots in Room`() {
        runBlocking {
            database.queueSnapshotDao().insertQueueSnapshots(
                listOf(
                    com.aistudio.clinicsystem.data.db.QueueSnapshotEntity(
                        id = 1, patientName = "Old", appointmentId = 1,
                        position = 1, status = "WAITING", timestamp = 0
                    )
                )
            )
        }

        handleMessage("""{"event":"QUEUE_UPDATE","data":{"queue":[{"id":1,"patient_name":"Alice","appointment_id":1,"position":1,"status":"WAITING"},{"id":2,"patient_name":"Bob","appointment_id":2,"position":2,"status":"WAITING"},{"id":3,"patient_name":"Charlie","appointment_id":3,"position":3,"status":"IN_PROGRESS"}]}}""")

        runBlocking {
            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            assertEquals(3, snapshots.size)
            assertTrue(snapshots.none { it.patientName == "Old" })
            assertTrue(snapshots.any { it.patientName == "Alice" })
        }
    }

    @Test
    fun `QUEUE_UPDATE with empty queue clears Room`() {
        runBlocking {
            database.queueSnapshotDao().insertQueueSnapshots(
                listOf(
                    com.aistudio.clinicsystem.data.db.QueueSnapshotEntity(
                        id = 1, patientName = "X", appointmentId = 1,
                        position = 1, status = "WAITING", timestamp = 0
                    )
                )
            )
        }

        handleMessage("""{"event":"QUEUE_UPDATE","data":{"queue":[]}}""")

        runBlocking {
            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            assertEquals(0, snapshots.size)
        }
    }

    // ─── Error handling ──────────────────────────────────────────────

    @Test
    fun `malformed JSON does not crash`() {
        handleMessage("this is not json {{{")
    }

    @Test
    fun `unknown event type does not crash`() {
        handleMessage("""{"event":"UNKNOWN_EVENT","data":{}}""")
    }

    @Test
    fun `null event field does not crash`() {
        handleMessage("""{"event":null}""")
    }

    @Test
    fun `missing event field does not crash`() {
        handleMessage("""{"data":{}}""")
    }

    @Test
    fun `empty JSON string does not crash`() {
        handleMessage("")
    }

    // ═══════════════════════════════════════════════════════════════════
    // P0-2 audit fix: new backend contract — `type` field, ping/pong,
    // queue.connected, error, lowercase event types.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `type field is preferred over event field`() {
        // Backend sends `type` (lowercase). Old contract used `event` (UPPER).
        // Verify the new `type:APPOINTMENT_STATUS` is dispatched correctly.
        handleMessage("""{"type":"APPOINTMENT_STATUS","data":{"id":777,"status":"APPROVED","doctor_name":"Dr. Type","date":"2026-07-10","time":"10:00","patient_name":"Type Patient","patient_phone":"+77771112233"}}""")

        runBlocking {
            val appt = database.appointmentDao().getAppointmentByServerId(777)
            assertNotNull("Appointment via type field should be inserted", appt)
            assertEquals("APPROVED", appt!!.status)
        }
    }

    @Test
    fun `ping event does not crash and is handled`() {
        // Backend sends {"type":"ping","timestamp":12345.678} every 30s.
        // The handler must not crash — sending a pong reply requires a live
        // WebSocket, which we don't have in this test (wsClient.webSocket == null).
        // The handler must swallow the NPE / send-failure gracefully.
        handleMessage("""{"type":"ping","timestamp":12345.678}""")
        // No assertion needed — the test passes if no exception propagates.
    }

    @Test
    fun `queue connected event logs subscription confirmation without crashing`() {
        handleMessage("""{"type":"queue.connected","room":"general::2026-07-10"}""")
        // No data assertion — the handler only writes a sync log. Verify
        // no exception was thrown.
    }

    @Test
    fun `error event with auth reason stops the socket`() {
        // Backend sends {"type":"error","reason":"Authentication required in production"}
        // — the handler should call stop() when reason mentions auth.
        // We can't easily verify stop() here without a real WebSocket; we
        // verify the handler doesn't crash and writes a sync log.
        handleMessage("""{"type":"error","reason":"Authentication required in production"}""")

        runBlocking {
            val logs = database.syncLogDao().getAllSyncLogs()
            assertTrue(
                "Should log auth error",
                logs.any { it.logMessage.contains("Authentication") }
            )
        }
    }

    @Test
    fun `error event with non-auth reason does not stop the socket`() {
        handleMessage("""{"type":"error","reason":"origin not allowed"}""")

        runBlocking {
            val logs = database.syncLogDao().getAllSyncLogs()
            assertTrue(
                "Should log origin error",
                logs.any { it.logMessage.contains("origin") }
            )
        }
    }

    @Test
    fun `lowercase queue_update event type is handled like QUEUE_UPDATE`() {
        handleMessage("""{"type":"queue_update","data":{"queue":[{"id":10,"patient_name":"Dave","appointment_id":10,"position":1,"status":"WAITING"}]}}""")

        runBlocking {
            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            assertEquals(1, snapshots.size)
            assertEquals("Dave", snapshots[0].patientName)
        }
    }

    @Test
    fun `lowercase patient_called event type is handled like QUEUE_UPDATE`() {
        handleMessage("""{"type":"patient_called","data":{"queue":[{"id":11,"patient_name":"Eve","appointment_id":11,"position":1,"status":"CALLED"}]}}""")

        runBlocking {
            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            assertEquals(1, snapshots.size)
            assertEquals("Eve", snapshots[0].patientName)
        }
    }

    @Test
    fun `lowercase entry_added event type is handled like QUEUE_UPDATE`() {
        handleMessage("""{"type":"entry_added","data":{"queue":[{"id":12,"patient_name":"Frank","appointment_id":12,"position":1,"status":"WAITING"}]}}""")

        runBlocking {
            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            assertEquals(1, snapshots.size)
            assertEquals("Frank", snapshots[0].patientName)
        }
    }

    @Test
    fun `type and event fields both present - type wins`() {
        // Edge case: backend migration emits both fields. `type` takes priority.
        handleMessage("""{"type":"QUEUE_UPDATE","event":"APPOINTMENT_STATUS","data":{"queue":[{"id":13,"patient_name":"Grace","appointment_id":13,"position":1,"status":"WAITING"}]}}""")

        runBlocking {
            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            assertEquals(
                "type=QUEUE_UPDATE should win, populating queue snapshots",
                1,
                snapshots.size,
            )
        }
    }

    @Test
    fun `unknown type field is logged and dropped`() {
        handleMessage("""{"type":"some_future_event","data":{}}""")
        // No assertion — the test passes if no exception propagates.
    }
}

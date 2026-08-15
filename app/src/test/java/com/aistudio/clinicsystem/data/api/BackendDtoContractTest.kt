package com.aistudio.clinicsystem.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * High-1 audit fix: unit tests for backend DTO JSON parsing.
 *
 * Verifies that the mobile client DTOs correctly parse the JSON returned
 * by the real backend endpoints (see `final/backend/app/schemas/mobile.py`
 * and `final/backend/app/api/v1/endpoints/mobile_api.py`).
 *
 * Before this fix, every DTO had field-name mismatches with the backend —
 * e.g. client expected `event` field but backend sent `type`, client
 * expected `body` but backend sent `message`, etc. All mismatched fields
 * were silently null after parsing.
 *
 * These tests use REAL backend response samples (copied from the backend
 * Pydantic schemas and endpoint implementations) to prevent regressions.
 */
class BackendDtoContractTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder().build()
    }

    // ═══════════════════════════════════════════════════════════════════
    // PatientProfileOut — GET /api/v1/mobile/patients/me
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `PatientProfileOut parses real backend response`() {
        // Sample from backend app/schemas/mobile.py:PatientProfileOut
        val json = """
            {
              "id": 42,
              "fio": "Иванов Иван Иванович",
              "phone": "+77771112233",
              "birth_year": 1990,
              "address": "г. Ташкент, ул. Пример, д. 1",
              "telegram_id": "123456789",
              "created_at": "2026-01-15T10:30:00Z"
            }
        """.trimIndent()

        val adapter = moshi.adapter(PatientProfileOut::class.java)
        val profile = adapter.fromJson(json)!!

        assertEquals(42, profile.id)
        assertEquals("Иванов Иван Иванович", profile.fio)
        assertEquals("+77771112233", profile.phone)
        assertEquals(1990, profile.birthYear)
        assertEquals("г. Ташкент, ул. Пример, д. 1", profile.address)
        assertEquals("123456789", profile.telegramId)
        assertEquals("2026-01-15T10:30:00Z", profile.createdAt)
    }

    @Test
    fun `PatientProfileOut handles null optional fields`() {
        val json = """
            {
              "id": 1,
              "fio": "Test User",
              "phone": "+70000000000",
              "birth_year": null,
              "address": null,
              "telegram_id": null,
              "created_at": "2026-07-10T00:00:00Z"
            }
        """.trimIndent()

        val profile = moshi.adapter(PatientProfileOut::class.java).fromJson(json)!!
        assertNull(profile.birthYear)
        assertNull(profile.address)
        assertNull(profile.telegramId)
        assertFalse(profile.telegramConnected)
    }

    @Test
    fun `PatientProfileOut displayName falls back to phone when fio is blank`() {
        val json = """{"id":1,"fio":"","phone":"+77771112233","created_at":"2026-07-10T00:00:00Z"}"""
        val profile = moshi.adapter(PatientProfileOut::class.java).fromJson(json)!!
        assertEquals("+77771112233", profile.displayName)
    }

    @Test
    fun `PatientProfileOut telegramConnected is true when telegram_id is present`() {
        val json = """{"id":1,"fio":"X","phone":"+7","created_at":"X","telegram_id":"123"}"""
        val profile = moshi.adapter(PatientProfileOut::class.java).fromJson(json)!!
        assertTrue(profile.telegramConnected)
    }

    // ═══════════════════════════════════════════════════════════════════
    // AppointmentUpcomingOut — GET /api/v1/mobile/appointments/upcoming
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `AppointmentUpcomingOut parses real backend response`() {
        // Sample from backend app/schemas/mobile.py:AppointmentUpcomingOut
        val json = """
            {
              "id": 100,
              "doctor_name": "Д-р Сапаев",
              "specialty": "Стоматолог-терапевт",
              "appointment_date": "2026-07-15T14:30:00Z",
              "status": "APPROVED",
              "clinic_address": "г. Ташкент, ул. Амира Темура, 1"
            }
        """.trimIndent()

        val appt = moshi.adapter(AppointmentUpcomingOut::class.java).fromJson(json)!!
        assertEquals(100, appt.id)
        assertEquals("Д-р Сапаев", appt.doctorName)
        assertEquals("Стоматолог-терапевт", appt.specialty)
        assertEquals("2026-07-15T14:30:00Z", appt.appointmentDate)
        assertEquals("APPROVED", appt.status)
        assertEquals("г. Ташкент, ул. Амира Темура, 1", appt.clinicAddress)
    }

    @Test
    fun `AppointmentUpcomingOut date accessor extracts YYYY-MM-DD from ISO datetime`() {
        val json = """{"id":1,"doctor_name":"X","specialty":"Y","appointment_date":"2026-07-15T14:30:00Z","status":"S","clinic_address":"A"}"""
        val appt = moshi.adapter(AppointmentUpcomingOut::class.java).fromJson(json)!!
        assertEquals("2026-07-15", appt.date)
    }

    @Test
    fun `AppointmentUpcomingOut time accessor extracts HH-MM from ISO datetime`() {
        val json = """{"id":1,"doctor_name":"X","specialty":"Y","appointment_date":"2026-07-15T14:30:00Z","status":"S","clinic_address":"A"}"""
        val appt = moshi.adapter(AppointmentUpcomingOut::class.java).fromJson(json)!!
        assertEquals("14:30", appt.time)
    }

    @Test
    fun `AppointmentUpcomingOut time accessor handles fractional seconds`() {
        val json = """{"id":1,"doctor_name":"X","specialty":"Y","appointment_date":"2026-07-15T14:30:45.123Z","status":"S","clinic_address":"A"}"""
        val appt = moshi.adapter(AppointmentUpcomingOut::class.java).fromJson(json)!!
        assertEquals("14:30", appt.time)
    }

    @Test
    fun `AppointmentUpcomingOut parses list of upcoming appointments`() {
        val json = """
            [
              {"id":1,"doctor_name":"Dr. A","specialty":"Cardio","appointment_date":"2026-07-15T10:00:00Z","status":"APPROVED","clinic_address":"Addr A"},
              {"id":2,"doctor_name":"Dr. B","specialty":"Dental","appointment_date":"2026-07-16T11:00:00Z","status":"PENDING","clinic_address":"Addr B"}
            ]
        """.trimIndent()
        val type = Types.newParameterizedType(List::class.java, AppointmentUpcomingOut::class.java)
        val list = moshi.adapter<List<AppointmentUpcomingOut>>(type).fromJson(json)!!
        assertEquals(2, list.size)
        assertEquals("Dr. A", list[0].doctorName)
        assertEquals("PENDING", list[1].status)
    }

    // ═══════════════════════════════════════════════════════════════════
    // LabResultOut — GET /api/v1/mobile/lab/results
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `LabResultOut parses real backend response`() {
        // Sample from backend app/schemas/mobile.py:LabResultOut
        val json = """
            {
              "id": 55,
              "test_name": "Глюкоза крови",
              "result_value": "5.4",
              "reference_range": "3.3 - 6.1",
              "unit": "ммоль/л",
              "result_date": "2026-07-01T08:15:00Z",
              "status": "normal",
              "notes": "Результат в норме"
            }
        """.trimIndent()

        val lab = moshi.adapter(LabResultOut::class.java).fromJson(json)!!
        assertEquals(55, lab.id)
        assertEquals("Глюкоза крови", lab.testName)
        assertEquals("5.4", lab.resultValue)
        assertEquals("3.3 - 6.1", lab.referenceRange)
        assertEquals("ммоль/л", lab.unit)
        assertEquals("2026-07-01T08:15:00Z", lab.resultDate)
        assertEquals("normal", lab.status)
        assertEquals("Результат в норме", lab.notes)
    }

    @Test
    fun `LabResultOut handles null notes`() {
        val json = """
            {
              "id": 1,
              "test_name": "Test",
              "result_value": "X",
              "reference_range": "Y",
              "unit": "U",
              "result_date": "2026-07-01T00:00:00Z",
              "status": "normal",
              "notes": null
            }
        """.trimIndent()
        val lab = moshi.adapter(LabResultOut::class.java).fromJson(json)!!
        assertNull(lab.notes)
    }

    @Test
    fun `LabResultOut parses list of results`() {
        val json = """
            [
              {"id":1,"test_name":"A","result_value":"1","reference_range":"R","unit":"U","result_date":"2026-07-01T00:00:00Z","status":"normal"},
              {"id":2,"test_name":"B","result_value":"2","reference_range":"R","unit":"U","result_date":"2026-07-02T00:00:00Z","status":"abnormal","notes":"High"}
            ]
        """.trimIndent()
        val type = Types.newParameterizedType(List::class.java, LabResultOut::class.java)
        val list = moshi.adapter<List<LabResultOut>>(type).fromJson(json)!!
        assertEquals(2, list.size)
        assertEquals("normal", list[0].status)
        assertEquals("abnormal", list[1].status)
        assertNull(list[0].notes)
        assertEquals("High", list[1].notes)
    }

    // ═══════════════════════════════════════════════════════════════════
    // NotificationOut — GET /api/v1/mobile/notifications
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `NotificationOut parses real backend response`() {
        // Sample from backend mobile_api.py:459-470 — the endpoint
        // returns list[dict] (NOT a Pydantic model), with these fields:
        //   id, title, message, type, data, sent_at, read
        val json = """
            {
              "id": 789,
              "title": "Запись подтверждена",
              "message": "Ваша запись к врачу на 15 июля подтверждена",
              "type": "appointment_status",
              "data": {"appointment_id": 100},
              "sent_at": "2026-07-10T12:00:00Z",
              "read": false
            }
        """.trimIndent()

        val notif = moshi.adapter(NotificationOut::class.java).fromJson(json)!!
        assertEquals(789, notif.id)
        assertEquals("Запись подтверждена", notif.title)
        assertEquals("Ваша запись к врачу на 15 июля подтверждена", notif.message)
        assertEquals("appointment_status", notif.type)
        assertNotNull(notif.data)
        assertEquals(100, notif.data!!["appointment_id"])
        assertEquals("2026-07-10T12:00:00Z", notif.sentAt)
        assertFalse(notif.read)
    }

    @Test
    fun `NotificationOut handles read notification`() {
        val json = """{"id":1,"title":"X","message":"Y","type":"t","sent_at":"2026-07-10T00:00:00Z","read":true}"""
        val notif = moshi.adapter(NotificationOut::class.java).fromJson(json)!!
        assertTrue(notif.read)
    }

    @Test
    fun `NotificationOut handles null id (delivery_id may be null on backend)`() {
        val json = """{"id":null,"title":"X","message":"Y","type":"t","sent_at":null,"read":false}"""
        val notif = moshi.adapter(NotificationOut::class.java).fromJson(json)!!
        assertNull(notif.id)
        assertNull(notif.sentAt)
    }

    // ═══════════════════════════════════════════════════════════════════
    // MobileQuickStats — GET /api/v1/mobile/stats
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `MobileQuickStats parses real backend response`() {
        // Sample from backend app/schemas/mobile.py:MobileQuickStats
        val json = """
            {
              "total_appointments": 15,
              "upcoming_appointments": 2,
              "completed_appointments": 12,
              "total_spent": 1500000.50,
              "last_visit": "2026-06-20T10:00:00Z",
              "favorite_doctor": "Д-р Сапаев",
              "pending_payments": 1
            }
        """.trimIndent()

        val stats = moshi.adapter(MobileQuickStats::class.java).fromJson(json)!!
        assertEquals(15, stats.totalAppointments)
        assertEquals(2, stats.upcomingAppointments)
        assertEquals(12, stats.completedAppointments)
        assertEquals(1500000.50, stats.totalSpent, 0.01)
        assertEquals("2026-06-20T10:00:00Z", stats.lastVisit)
        assertEquals("Д-р Сапаев", stats.favoriteDoctor)
        assertEquals(1, stats.pendingPayments)
    }

    @Test
    fun `MobileQuickStats handles minimal response with defaults`() {
        val json = """{}"""
        val stats = moshi.adapter(MobileQuickStats::class.java).fromJson(json)!!
        assertEquals(0, stats.totalAppointments)
        assertEquals(0, stats.upcomingAppointments)
        assertEquals(0, stats.completedAppointments)
        assertEquals(0.0, stats.totalSpent, 0.01)
        assertNull(stats.lastVisit)
        assertNull(stats.favoriteDoctor)
        assertEquals(0, stats.pendingPayments)
    }

    // ═══════════════════════════════════════════════════════════════════
    // NotificationSettingsOut — GET /api/v1/mobile/settings/notifications
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `NotificationSettingsOut parses real backend response`() {
        // Sample from backend app/schemas/mobile.py:MobileNotificationSettings
        val json = """
            {
              "appointment_reminders": true,
              "queue_updates": true,
              "lab_results": false,
              "payment_notifications": true,
              "push_enabled": true,
              "email_enabled": false,
              "sms_enabled": true
            }
        """.trimIndent()

        val settings = moshi.adapter(NotificationSettingsOut::class.java).fromJson(json)!!
        assertTrue(settings.appointmentReminders)
        assertTrue(settings.queueUpdates)
        assertFalse(settings.labResults)
        assertTrue(settings.paymentNotifications)
        assertTrue(settings.pushEnabled)
        assertFalse(settings.emailEnabled)
        assertTrue(settings.smsEnabled)
    }

    @Test
    fun `NotificationSettingsOut defaults match backend defaults`() {
        // Backend defaults: appointment_reminders=True, queue_updates=True,
        // lab_results=True, payment_notifications=True, push_enabled=True,
        // email_enabled=False, sms_enabled=False
        val json = """{}"""
        val settings = moshi.adapter(NotificationSettingsOut::class.java).fromJson(json)!!
        assertTrue(settings.appointmentReminders)
        assertTrue(settings.queueUpdates)
        assertTrue(settings.labResults)
        assertTrue(settings.paymentNotifications)
        assertTrue(settings.pushEnabled)
        assertFalse(settings.emailEnabled)
        assertFalse(settings.smsEnabled)
    }
}

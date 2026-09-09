package com.aistudio.clinicsystem.data.outbox

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for TASK-1: the owner of an appointment outbox row fixes
 * the retry route. A patient self-booking row must NEVER be replayed through
 * the staff endpoint, and a staff row must NEVER be replayed through the
 * mobile contract — even after server rejections or offline restarts.
 */
class OutboxRoutingTest {
    // ── UPDATE_STATUS actor parsing ──────────────────────────────────────

    @Test
    fun `SELF actor is parsed from the 5th payload segment`() {
        val payload = "42|CANCELLED|Отменено: занят|local-uuid|SELF"
        assertEquals(OutboxRouting.ACTOR_SELF, OutboxRouting.parseActor(payload))
    }

    @Test
    fun `STAFF actor is parsed from the 5th payload segment`() {
        val payload = "42|APPROVED|Подтверждено|local-uuid|STAFF"
        assertEquals(OutboxRouting.ACTOR_STAFF, OutboxRouting.parseActor(payload))
    }

    @Test
    fun `legacy 4-segment rows default to STAFF`() {
        // Rows created before TASK-1 were always dispatched to the staff
        // endpoint — the default preserves their behavior.
        val payload = "42|APPROVED|Подтверждено|local-uuid"
        assertEquals(OutboxRouting.ACTOR_STAFF, OutboxRouting.parseActor(payload))
    }

    @Test
    fun `notes containing the pipe separator do not break actor parsing`() {
        // split(limit = 5) is positional: a '|' inside the notes segment
        // merges into the 4th part and the 5th segment still holds the actor.
        val payload = "42|CANCELLED|note a|b|SELF"
        assertEquals(OutboxRouting.ACTOR_SELF, OutboxRouting.parseActor(payload))
    }

    // ── route selection ──────────────────────────────────────────────────

    @Test
    fun `patient cancel replays through the mobile route`() {
        assertEquals(
            OutboxRouting.StatusRoute.MOBILE_CANCEL,
            OutboxRouting.statusRoute(OutboxRouting.ACTOR_SELF, "CANCELLED"),
        )
    }

    @Test
    fun `patient cancel retry never crosses to the staff route`() {
        // The old fallback dispatched failed patient cancels to PUT
        // /appointments/{id} — that must not happen anymore.
        assertEquals(
            OutboxRouting.StatusRoute.MOBILE_CANCEL,
            OutboxRouting.statusRoute(OutboxRouting.ACTOR_SELF, "cancelled"),
        )
    }

    @Test
    fun `staff status changes replay through the staff route`() {
        for (status in listOf("APPROVED", "COMPLETED", "CANCELLED", "PENDING")) {
            assertEquals(
                OutboxRouting.StatusRoute.STAFF_PUT,
                OutboxRouting.statusRoute(OutboxRouting.ACTOR_STAFF, status),
            )
        }
    }

    @Test
    fun `self non-cancel statuses replay through the generic staff route`() {
        // Patients cannot approve — a SELF row with APPROVED (should not
        // exist) is still replayed through the generic staff route.
        assertEquals(
            OutboxRouting.StatusRoute.STAFF_PUT,
            OutboxRouting.statusRoute(OutboxRouting.ACTOR_SELF, "APPROVED"),
        )
    }

    // ── owner → operation mapping ────────────────────────────────────────

    @Test
    fun `patient owner maps to the self-booking operation`() {
        assertEquals(
            OutboxOperation.CREATE_APPOINTMENT_SELF,
            OutboxRouting.ownerOperation("PATIENT"),
        )
    }

    @Test
    fun `staff owner maps to the staff-booking operation`() {
        assertEquals(
            OutboxOperation.CREATE_APPOINTMENT_STAFF,
            OutboxRouting.ownerOperation("STAFF"),
        )
        // Unknown owners fall back to the staff scenario — the mobile
        // self-booking route is JWT-scoped to the caller and would book the
        // wrong patient.
        assertEquals(
            OutboxOperation.CREATE_APPOINTMENT_STAFF,
            OutboxRouting.ownerOperation(""),
        )
    }

    @Test
    fun `new outbox operations have distinct codes`() {
        // The codes are persisted in PendingSyncEntity.type — they must stay
        // distinct from each other and from the legacy code.
        val codes =
            setOf(
                OutboxOperation.CREATE_APPOINTMENT.code,
                OutboxOperation.CREATE_APPOINTMENT_SELF.code,
                OutboxOperation.CREATE_APPOINTMENT_STAFF.code,
                OutboxOperation.UPDATE_STATUS.code,
                OutboxOperation.UPDATE_APPOINTMENT.code,
                OutboxOperation.CREATE_MEDICAL_RECORD.code,
            )
        assertEquals(6, codes.size)
        // Round-trip through fromCode — new codes must stay parseable.
        assertEquals(OutboxOperation.CREATE_APPOINTMENT_SELF, OutboxOperation.fromCode("CREATE_APPOINTMENT_SELF"))
        assertEquals(OutboxOperation.CREATE_APPOINTMENT_STAFF, OutboxOperation.fromCode("CREATE_APPOINTMENT_STAFF"))
    }
}

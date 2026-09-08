package com.aistudio.clinicsystem.data.outbox

/**
 * TASK-1: routing rules for appointment outbox rows.
 *
 * The owner of an operation decides the route — NEVER the outcome of a
 * previous attempt:
 *   - PATIENT rows (CREATE_APPOINTMENT_SELF, UPDATE_STATUS actor=SELF)
 *     are retried exclusively against the mobile contract
 *     (POST /api/v1/mobile/appointments/book|cancel);
 *   - STAFF rows (CREATE_APPOINTMENT_STAFF, UPDATE_STATUS actor=STAFF)
 *     are retried exclusively against the staff endpoints
 *     (POST|PUT /api/v1/appointments).
 *
 * Extracted as a pure object so the routing invariants can be regression-
 * tested on the JVM without Android/Robolectric.
 */
object OutboxRouting {

    /** Recorded actor of an UPDATE_STATUS row. */
    const val ACTOR_SELF = "SELF"
    const val ACTOR_STAFF = "STAFF"

    /**
     * UPDATE_STATUS payload format (Stage 1.2 / TASK-1):
     * `<serverId:Int>|<status:String>|<notes:String>|<localUuid:String>|<actor:String>`
     *
     * Rows written before TASK-1 have no 5th segment — they were always
     * dispatched to the staff endpoint, so the default is [ACTOR_STAFF].
     */
    fun parseActor(payload: String): String {
        val parts = payload.split("|", limit = 5)
        return when (parts.getOrNull(4)?.uppercase()) {
            ACTOR_SELF -> ACTOR_SELF
            else -> ACTOR_STAFF
        }
    }

    /** Owner recorded in a [com.aistudio.clinicsystem.data.api.AppointmentOutboxPayload]. */
    const val OWNER_PATIENT = "PATIENT"
    const val OWNER_STAFF = "STAFF"

    /** Maps a recorded owner to the outbox operation the row must replay. */
    fun ownerOperation(owner: String): OutboxOperation =
        when (owner.uppercase()) {
            OWNER_PATIENT -> OutboxOperation.CREATE_APPOINTMENT_SELF
            else -> OutboxOperation.CREATE_APPOINTMENT_STAFF
        }

    /** The fixed route an UPDATE_STATUS row must be replayed on. */
    enum class StatusRoute {
        /** Patient cancel via POST /api/v1/mobile/appointments/cancel. */
        MOBILE_CANCEL,

        /** Staff status change via PUT /api/v1/appointments/{id}. */
        STAFF_PUT,
    }

    /**
     * Route selection for an UPDATE_STATUS retry: actor=SELF with a CANCELLED
     * status replays the patient mobile-cancel; everything else replays the
     * staff PUT (the only route for staff status changes, including approve /
     * complete / cancel by registrar or doctor).
     */
    fun statusRoute(actor: String, status: String): StatusRoute =
        if (actor == ACTOR_SELF && status.uppercase() == "CANCELLED") {
            StatusRoute.MOBILE_CANCEL
        } else {
            StatusRoute.STAFF_PUT
        }

    /**
     * TASK-2: HTTP classification shared by the foreground write paths and
     * the outbox flush. 403/409/422 are SERVER REJECTIONS, not network
     * absence — they must surface to the user and must NOT be retried on a
     * different route or mistaken for an offline state. Retriable = 5xx,
     * 401 (token refresh), 408, 429.
     */
    fun isRetriableHttp(code: Int): Boolean = code in 500..599 || code == 401 || code == 408 || code == 429
}

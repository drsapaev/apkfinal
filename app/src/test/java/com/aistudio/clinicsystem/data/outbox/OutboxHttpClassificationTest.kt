package com.aistudio.clinicsystem.data.outbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for TASK-2: HTTP classification for write outcomes.
 * 403/409/422 must be treated as server REJECTIONS (surfaced to the user,
 * never retried, never mistaken for "no network"); 5xx/401/408/429 are
 * retriable through the outbox.
 */
class OutboxHttpClassificationTest {

    @Test
    fun `403 is a rejection, not network absence`() {
        assertFalse(OutboxRouting.isRetriableHttp(403))
    }

    @Test
    fun `409 conflict is a rejection`() {
        assertFalse(OutboxRouting.isRetriableHttp(409))
    }

    @Test
    fun `422 validation error is a rejection`() {
        assertFalse(OutboxRouting.isRetriableHttp(422))
    }

    @Test
    fun `400 bad request is a rejection`() {
        assertFalse(OutboxRouting.isRetriableHttp(400))
    }

    @Test
    fun `404 not found is a rejection`() {
        assertFalse(OutboxRouting.isRetriableHttp(404))
    }

    @Test
    fun `server errors are retriable`() {
        assertTrue(OutboxRouting.isRetriableHttp(500))
        assertTrue(OutboxRouting.isRetriableHttp(502))
        assertTrue(OutboxRouting.isRetriableHttp(503))
    }

    @Test
    fun `timeout and throttling are retriable`() {
        assertTrue(OutboxRouting.isRetriableHttp(408))
        assertTrue(OutboxRouting.isRetriableHttp(429))
    }

    @Test
    fun `401 triggers token-refresh retry`() {
        assertTrue(OutboxRouting.isRetriableHttp(401))
    }
}

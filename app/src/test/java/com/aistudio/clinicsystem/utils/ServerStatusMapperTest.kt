package com.aistudio.clinicsystem.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the CODEX-P1-FIX (PR #147): server appointment
 * statuses must be normalized to the client vocabulary before caching,
 * and client statuses must be converted to the backend vocabulary before
 * any write request.
 *
 * Backend (`final/backend`, appointments router) answers with lowercase
 * values ("scheduled"/"confirmed"/…); the staff UI filters and action
 * buttons only recognize "PENDING"/"APPROVED"/….
 */
class ServerStatusMapperTest {
    // ── fromServer: what gets cached after a sync ────────────────────────

    @Test
    fun `staff lowercase scheduled maps to PENDING`() {
        assertEquals("PENDING", ServerStatusMapper.fromServer("scheduled"))
    }

    @Test
    fun `staff lowercase confirmed maps to APPROVED`() {
        assertEquals("APPROVED", ServerStatusMapper.fromServer("confirmed"))
    }

    @Test
    fun `staff lowercase completed maps to COMPLETED`() {
        assertEquals("COMPLETED", ServerStatusMapper.fromServer("completed"))
    }

    @Test
    fun `staff lowercase cancelled maps to CANCELLED`() {
        assertEquals("CANCELLED", ServerStatusMapper.fromServer("cancelled"))
        // British spelling used by some backend code paths
        assertEquals("CANCELLED", ServerStatusMapper.fromServer("canceled"))
    }

    @Test
    fun `client uppercase statuses pass through unchanged`() {
        // The mobile endpoint may already return client-style values
        assertEquals("PENDING", ServerStatusMapper.fromServer("PENDING"))
        assertEquals("APPROVED", ServerStatusMapper.fromServer("APPROVED"))
    }

    @Test
    fun `unknown server status does not crash and stays visible`() {
        assertEquals("ON_HOLD", ServerStatusMapper.fromServer("on_hold"))
    }

    // ── toServer: what gets sent to the backend ─────────────────────────

    @Test
    fun `client PENDING maps to scheduled`() {
        assertEquals("scheduled", ServerStatusMapper.toServer("PENDING"))
    }

    @Test
    fun `client APPROVED maps to confirmed`() {
        assertEquals("confirmed", ServerStatusMapper.toServer("APPROVED"))
    }

    @Test
    fun `client CANCELLED maps to cancelled`() {
        assertEquals("cancelled", ServerStatusMapper.toServer("CANCELLED"))
    }

    @Test
    fun `client COMPLETED maps to completed`() {
        assertEquals("completed", ServerStatusMapper.toServer("COMPLETED"))
    }

    // ── round-trip ───────────────────────────────────────────────────────

    @Test
    fun `round trip client to server and back is identity`() {
        for (status in listOf("PENDING", "APPROVED", "COMPLETED", "CANCELLED")) {
            assertEquals(status, ServerStatusMapper.fromServer(ServerStatusMapper.toServer(status)))
        }
    }
}

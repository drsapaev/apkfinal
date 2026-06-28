package com.aistudio.clinicsystem.data.outbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 10c (TEST-7 fix): OutboxRetryPolicyTest.
 *
 * Closes audit finding TEST-7: "No tests for OutboxRetryPolicy."
 *
 * Tests the exponential backoff formula with jitter:
 *  - backoffFor(0) ≈ 2000ms (±20% jitter)
 *  - backoffFor(1) ≈ 4000ms
 *  - backoffFor(2) ≈ 8000ms
 *  - backoffFor(10) = maxBackoffMs (capped at 300000ms)
 *  - Minimum is 1000ms regardless of inputs
 *  - Jitter is within ±20% of the base delay
 */
class OutboxRetryPolicyTest {

    private val policy = OutboxRetryPolicy(
        maxRetries = 5,
        initialBackoffMs = 2_000,
        maxBackoffMs = 300_000,
        backoffMultiplier = 2.0,
    )

    @Test
    fun `backoffFor 0 returns approximately 2000ms with jitter`() {
        val delay = policy.backoffFor(0)
        assertTrue("Delay should be >= 1600, was $delay", delay >= 1600)
        assertTrue("Delay should be <= 2400, was $delay", delay <= 2400)
    }

    @Test
    fun `backoffFor 1 returns approximately 4000ms with jitter`() {
        val delay = policy.backoffFor(1)
        assertTrue("Delay should be >= 3200, was $delay", delay >= 3200)
        assertTrue("Delay should be <= 4800, was $delay", delay <= 4800)
    }

    @Test
    fun `backoffFor 2 returns approximately 8000ms with jitter`() {
        val delay = policy.backoffFor(2)
        assertTrue("Delay should be >= 6400, was $delay", delay >= 6400)
        assertTrue("Delay should be <= 9600, was $delay", delay <= 9600)
    }

    @Test
    fun `backoffFor 10 is capped at maxBackoffMs with jitter`() {
        val delay = policy.backoffFor(10)
        assertTrue("Delay should be >= 240000, was $delay", delay >= 240000)
        assertTrue("Delay should be <= 360000, was $delay", delay <= 360000)
    }

    @Test
    fun `backoffFor never returns less than 1000ms`() {
        for (i in 0..20) {
            val delay = policy.backoffFor(i)
            assertTrue("Delay for retry $i should be >= 1000, was $delay", delay >= 1000)
        }
    }

    @Test
    fun `backoffFor increases monotonically on average`() {
        val samples = 100
        val avg0 = (1..samples).map { policy.backoffFor(0) }.average()
        val avg1 = (1..samples).map { policy.backoffFor(1) }.average()
        val avg2 = (1..samples).map { policy.backoffFor(2) }.average()
        assertTrue("avg0=$avg0 < avg1=$avg1", avg0 < avg1)
        assertTrue("avg1=$avg1 < avg2=$avg2", avg1 < avg2)
    }

    @Test
    fun `default policy has correct defaults`() {
        val default = OutboxRetryPolicy()
        assertEquals(5, default.maxRetries)
        assertEquals(2_000L, default.initialBackoffMs)
        assertEquals(300_000L, default.maxBackoffMs)
    }

    @Test
    fun `OutboxStatus enum has 5 values`() {
        assertEquals(5, OutboxStatus.entries.size)
    }

    @Test
    fun `OutboxOperation enum has 3 values`() {
        assertEquals(3, OutboxOperation.entries.size)
    }

    @Test
    fun `OutboxOperation fromCode round-trip works`() {
        for (op in OutboxOperation.entries) {
            assertEquals(op, OutboxOperation.fromCode(op.code))
        }
    }

    @Test
    fun `OutboxOperation fromCode returns null for unknown`() {
        assertEquals(null, OutboxOperation.fromCode("UNKNOWN"))
    }

    @Test
    fun `generateOutboxId returns unique UUID strings`() {
        val id1 = generateOutboxId()
        val id2 = generateOutboxId()
        assertTrue("IDs should be different", id1 != id2)
        assertTrue("ID should be a UUID", id1.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }
}

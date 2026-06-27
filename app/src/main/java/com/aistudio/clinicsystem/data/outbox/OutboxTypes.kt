package com.aistudio.clinicsystem.data.outbox

import java.util.UUID

/**
 * M3B.3: Outbox status for pending sync operations.
 *
 * State machine:
 *   PENDING → PROCESSING → COMPLETED (success)
 *                      ↘ FAILED → (retry) → PENDING
 *                              ↘ DEAD_LETTER (max retries exceeded)
 */
enum class OutboxStatus {
    /** Queued, waiting for next sync cycle. */
    PENDING,

    /** Being processed right now (SyncWorker is sending it to the server). */
    PROCESSING,

    /** Server confirmed success — can be deleted. */
    COMPLETED,

    /** Server rejected or network error — will retry with backoff. */
    FAILED,

    /** Max retries exceeded — requires manual intervention. */
    DEAD_LETTER
}

/**
 * M3B.3: Type of outbox operation.
 * Determines how the payload is deserialized and which API endpoint is called.
 */
enum class OutboxOperation {
    CREATE_APPOINTMENT,
    UPDATE_APPOINTMENT_STATUS,
    CREATE_MEDICAL_RECORD,
    CANCEL_APPOINTMENT,
    RESCHEDULE_APPOINTMENT
}

/**
 * M3B.3: Configuration for the outbox retry policy.
 */
data class OutboxRetryPolicy(
    val maxRetries: Int = 5,
    val initialBackoffMs: Long = 2_000,      // 2 seconds
    val maxBackoffMs: Long = 300_000,        // 5 minutes
    val backoffMultiplier: Double = 2.0      // exponential: 2s, 4s, 8s, 16s, 32s
) {
    /**
     * Calculates the next retry delay for a given attempt number (0-based).
     * Uses exponential backoff with jitter.
     */
    fun backoffFor(retryCount: Int): Long {
        val baseDelay = (initialBackoffMs * Math.pow(backoffMultiplier, retryCount.toDouble())).toLong()
        val cappedDelay = minOf(baseDelay, maxBackoffMs)
        // Add ±20% jitter to avoid thundering herd
        val jitter = (cappedDelay * 0.2 * (Math.random() * 2 - 1)).toLong()
        return maxOf(1000, cappedDelay + jitter) // minimum 1 second
    }
}

/**
 * M3B.3: Generates a new UUID for outbox entries.
 * Used as primary key — collision-free, sortable by creation time.
 */
fun generateOutboxId(): String = UUID.randomUUID().toString()

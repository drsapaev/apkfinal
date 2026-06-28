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
 * M3B.3 / Stage 3.8 (L-2 fix): Type of outbox operation.
 * Determines how the payload is deserialized and which API endpoint is called.
 *
 * Stage 3.8 reactivates this enum — previously it was dead code, with
 * production using string literals like `"CREATE_APPOINTMENT"` and
 * `"UPDATE_STATUS"`. The string literals had drifted from the enum
 * (`UPDATE_APPOINTMENT_STATUS` vs the actual `"UPDATE_STATUS"`).
 *
 * Now [PendingSyncEntity.type] is still a String (for Room storage), but
 * all writes go through [OutboxOperation.code] and all reads go through
 * [OutboxOperation.fromCode]. This makes the type system enforce consistency.
 *
 * The `.code` values match the strings that were already in production —
 * no migration needed.
 */
enum class OutboxOperation(val code: String) {
    CREATE_APPOINTMENT("CREATE_APPOINTMENT"),
    UPDATE_STATUS("UPDATE_STATUS"),
    CREATE_MEDICAL_RECORD("CREATE_MEDICAL_RECORD"),
    ;

    companion object {
        /**
         * Parses a stored type string back into the enum. Returns null for
         * unknown strings (e.g. legacy `CANCEL_APPOINTMENT` rows from an
         * old build) — the caller should treat null as "payload corrupt"
         * and move the row to DEAD_LETTER.
         */
        fun fromCode(code: String): OutboxOperation? =
            entries.firstOrNull { it.code == code }
    }
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

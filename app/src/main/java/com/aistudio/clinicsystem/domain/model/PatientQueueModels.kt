package com.aistudio.clinicsystem.domain.model

/**
 * TASK-6: one live queue position of the signed-in patient
 * (GET /api/v1/mobile/queues/my-position → {"positions":[…]}).
 */
data class PatientQueuePosition(
    val queueId: Int,
    val doctorName: String,
    val specialty: String,
    val myNumber: Int,
    val currentNumber: Int,
    val patientsBeforeMe: Int,
    val estimatedWaitMinutes: Int,
    val status: String,
)

/**
 * TASK-6: UI state of the patient's queues. A successful EMPTY response is a
 * valid state (no active positions) and CLEARS old positions; a network
 * error keeps the last known data and flags it stale so the user sees the
 * difference between "нет позиции" and "не удалось обновить".
 */
data class PatientQueueUiState(
    val positions: List<PatientQueuePosition> = emptyList(),
    val lastUpdated: Long = 0,
    /** True when the latest fetch FAILED and [positions] is stale data. */
    val isStale: Boolean = false,
    val isLoading: Boolean = false,
)

/** TASK-6: result of a queue-position fetch — empty vs error are different. */
sealed class PatientQueueFetchResult {
    data class Success(val positions: List<PatientQueuePosition>) : PatientQueueFetchResult()

    /** Server answered successfully: no active queue positions right now. */
    data object Empty : PatientQueueFetchResult()

    /** Transport or HTTP failure — stale data must be kept and flagged. */
    data class Error(val cause: Exception) : PatientQueueFetchResult()
}

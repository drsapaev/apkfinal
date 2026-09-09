package com.aistudio.clinicsystem.domain.model

/**
 * TASK-7: outcome of a staff queue registration attempt. Registration is
 * created by the SERVER only — there is no local "live ticket" fallback, so
 * a failure must be shown to the registrar instead of a phantom entry.
 */
sealed class QueueRegistrationOutcome {
    /** Server created the ticket(s); [numbers] are the issued queue numbers. */
    data class Registered(
        val numbers: List<Int>,
    ) : QueueRegistrationOutcome()

    /** Server refused / network down / missing data — nothing was created. */
    data class Failed(
        val reason: String,
    ) : QueueRegistrationOutcome()
}

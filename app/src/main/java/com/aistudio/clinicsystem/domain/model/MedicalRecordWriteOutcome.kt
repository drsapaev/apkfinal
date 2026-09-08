package com.aistudio.clinicsystem.domain.model

import com.aistudio.clinicsystem.data.db.MedicalRecordEntity

/**
 * TASK-3: outcome of a staff clinical-note save, distinguishing a real
 * visit-anchored EMR v2 save from a local-only draft.
 *
 * A record is NEVER presented as a "saved medical record" unless the
 * server accepted it into EMR v2 (visible in the corresponding visit of
 * the web client). Signing is never performed automatically.
 */
sealed class MedicalRecordWriteOutcome {
    abstract val entity: MedicalRecordEntity

    /** EMR v2 accepted the note (is_draft=true; signing is separate). */
    data class Confirmed(
        override val entity: MedicalRecordEntity,
        val emrId: Int,
        val visitId: Int,
    ) : MedicalRecordWriteOutcome()

    /** Stored locally only — EMR integration unavailable for this actor/visit. */
    data class LocalDraft(
        override val entity: MedicalRecordEntity,
        val reason: String,
    ) : MedicalRecordWriteOutcome()

    /**
     * 409 CONFLICT — a newer version exists on the server. The local draft
     * is PRESERVED (never destroyed by a conflict); the user must reload
     * and re-apply the change.
     */
    data class Conflict(
        override val entity: MedicalRecordEntity,
        val serverRowVersion: Int?,
        val message: String,
    ) : MedicalRecordWriteOutcome()

    /** Server refused (403/422/…); the local draft is preserved and marked. */
    data class Rejected(
        override val entity: MedicalRecordEntity,
        val httpCode: Int,
        val message: String,
    ) : MedicalRecordWriteOutcome()
}

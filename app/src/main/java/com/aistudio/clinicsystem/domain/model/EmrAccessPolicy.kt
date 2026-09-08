package com.aistudio.clinicsystem.domain.model

/**
 * TASK-3: pure policy for EMR v2 access decisions on the client.
 *
 * Mirrors the backend (`final/backend/app/api/v1/endpoints/emr_v2.py`):
 *   - EMR_V2_WRITE_ROLES = Admin + Doctor family — may save EMR;
 *   - Registrar/Lab/Cashier/Patient — NOT allowed to write EMR; their
 *     clinical notes stay LOCAL DRAFTS instead of pretending to be saved
 *     medical records.
 * Extracted as a pure object so the decision is regression-tested without
 * Android/Robolectric.
 */
object EmrAccessPolicy {

    private val DOCTOR_FAMILY_ROLES =
        setOf(
            "DOCTOR",
            "DOCTOR_FAMILY",
            "CARDIO",
            "CARDIOLOGY",
            "CARDIOLOGIST",
            "DERMA",
            "DERMATOLOGIST",
            "DENTIST",
            "DENTIST_THERAPIST",
            "DENTIST_SURGEON",
            "DENTIST_ORTHOPEDIST",
            "DENTIST_ORTHODONTIST",
        )

    /** Roles that may attempt POST /api/v1/emr/{visit_id}. */
    fun canAttemptEmr(role: String?): Boolean {
        val normalized = role?.trim()?.uppercase() ?: return false
        return normalized == "ADMIN" || normalized == "SUPERADMIN" || normalized in DOCTOR_FAMILY_ROLES
    }

    /** True when the record is a local-only draft (no server-side EMR behind it). */
    fun isLocalDraft(record: com.aistudio.clinicsystem.data.db.MedicalRecordEntity): Boolean = record.serverId == null
}

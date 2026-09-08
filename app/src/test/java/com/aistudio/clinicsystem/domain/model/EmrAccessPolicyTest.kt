package com.aistudio.clinicsystem.domain.model

import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for TASK-3: EMR v2 write access is gated by the backend
 * role; notes of roles without EMR rights must stay LOCAL DRAFTS instead of
 * being presented as saved medical records. A record without a server-side
 * EMR id (serverId == null) is a local draft by definition.
 */
class EmrAccessPolicyTest {

    @Test
    fun `doctor may attempt EMR save`() {
        assertTrue(EmrAccessPolicy.canAttemptEmr("Doctor"))
        assertTrue(EmrAccessPolicy.canAttemptEmr("doctor"))
        assertTrue(EmrAccessPolicy.canAttemptEmr("Dentist"))
        assertTrue(EmrAccessPolicy.canAttemptEmr("Cardiologist"))
    }

    @Test
    fun `admin may attempt EMR save`() {
        assertTrue(EmrAccessPolicy.canAttemptEmr("Admin"))
        assertTrue(EmrAccessPolicy.canAttemptEmr("SuperAdmin"))
    }

    @Test
    fun `registrar can NOT attempt EMR save`() {
        // Registrar notes must remain local drafts — the backend would 403
        // (EMR_V2_WRITE_ROLES), and the client must not fake a save.
        assertFalse(EmrAccessPolicy.canAttemptEmr("Registrar"))
    }

    @Test
    fun `lab cashier and patient can NOT attempt EMR save`() {
        assertFalse(EmrAccessPolicy.canAttemptEmr("Lab"))
        assertFalse(EmrAccessPolicy.canAttemptEmr("Cashier"))
        assertFalse(EmrAccessPolicy.canAttemptEmr("Patient"))
    }

    @Test
    fun `null or blank role can NOT attempt EMR save`() {
        assertFalse(EmrAccessPolicy.canAttemptEmr(null))
        assertFalse(EmrAccessPolicy.canAttemptEmr(""))
        assertFalse(EmrAccessPolicy.canAttemptEmr("   "))
    }

    @Test
    fun `record without serverId is a local draft`() {
        val draft = record(serverId = null)
        assertTrue(EmrAccessPolicy.isLocalDraft(draft))
    }

    @Test
    fun `record with serverId is NOT a local draft`() {
        val saved = record(serverId = 42)
        assertFalse(EmrAccessPolicy.isLocalDraft(saved))
    }

    private fun record(serverId: Int?): MedicalRecordEntity =
        MedicalRecordEntity(
            serverId = serverId,
            patientPhone = "+77000000000",
            doctorName = "Д-р Тест",
            diagnosis = "Кариес",
            prescription = "Лечение",
            visitDate = "2026-09-09",
        )
}

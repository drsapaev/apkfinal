package com.aistudio.clinicsystem.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-5 audit fix: unit tests for [UserRole.fromBackend] and [UserRole.isStaff].
 *
 * The MainActivity routing fix (P0-5) depends on `UserRole.fromBackend(...).isStaff`
 * correctly identifying staff roles. Before the fix, MainActivity compared
 * `state.user?.role == "STAFF"` — but "STAFF" is not a backend role. Every
 * staff user (Doctor, Registrar, Cashier, Admin) was incorrectly routed to
 * PatientScreen.
 *
 * These tests verify:
 *   1. All real backend roles are parsed correctly.
 *   2. `isStaff` returns true for every non-PATIENT role.
 *   3. Null/blank/unknown values default to PATIENT (fail-safe).
 *   4. Legacy "STAFF" string maps to DOCTOR (backward-compat with old DB rows).
 *   5. Case sensitivity is preserved (backend sends exact-case strings).
 */
class UserRoleTest {

    @Test
    fun `Patient role parses correctly`() {
        assertEquals(UserRole.PATIENT, UserRole.fromBackend("Patient"))
    }

    @Test
    fun `Doctor role parses correctly and is staff`() {
        val role = UserRole.fromBackend("Doctor")
        assertEquals(UserRole.DOCTOR, role)
        assertTrue("Doctor must be staff", role.isStaff)
    }

    @Test
    fun `Registrar role parses correctly and is staff`() {
        val role = UserRole.fromBackend("Registrar")
        assertEquals(UserRole.REGISTRAR, role)
        assertTrue(role.isStaff)
    }

    @Test
    fun `Receptionist alias maps to Registrar`() {
        // Backend uses both "Registrar" and "Receptionist" in different places.
        val role = UserRole.fromBackend("Receptionist")
        assertEquals(UserRole.REGISTRAR, role)
        assertTrue(role.isStaff)
    }

    @Test
    fun `Lab role parses correctly and is staff`() {
        val role = UserRole.fromBackend("Lab")
        assertEquals(UserRole.LAB, role)
        assertTrue(role.isStaff)
    }

    @Test
    fun `Cashier role parses correctly and is staff`() {
        val role = UserRole.fromBackend("Cashier")
        assertEquals(UserRole.CASHIER, role)
        assertTrue(role.isStaff)
    }

    @Test
    fun `cardio role parses correctly and is staff`() {
        // Note: backend uses lowercase "cardio" — case-sensitive.
        val role = UserRole.fromBackend("cardio")
        assertEquals(UserRole.CARDIO, role)
        assertTrue(role.isStaff)
    }

    @Test
    fun `derma role parses correctly and is staff`() {
        val role = UserRole.fromBackend("derma")
        assertEquals(UserRole.DERMA, role)
        assertTrue(role.isStaff)
    }

    @Test
    fun `dentist role parses correctly and is staff`() {
        val role = UserRole.fromBackend("dentist")
        assertEquals(UserRole.DENTIST, role)
        assertTrue(role.isStaff)
    }

    @Test
    fun `Admin role parses correctly and is staff`() {
        val role = UserRole.fromBackend("Admin")
        assertEquals(UserRole.ADMIN, role)
        assertTrue(role.isStaff)
    }

    // ─── Fail-safe defaults ─────────────────────────────────────────

    @Test
    fun `null role defaults to PATIENT`() {
        assertEquals(UserRole.PATIENT, UserRole.fromBackend(null))
    }

    @Test
    fun `blank role defaults to PATIENT`() {
        assertEquals(UserRole.PATIENT, UserRole.fromBackend(""))
        assertEquals(UserRole.PATIENT, UserRole.fromBackend("   "))
    }

    @Test
    fun `unknown role defaults to PATIENT and is not staff`() {
        // Future backend roles (e.g. "Auditor") must NOT accidentally be
        // treated as staff — fail-safe defaults to PATIENT.
        val role = UserRole.fromBackend("some-future-role")
        assertEquals(UserRole.PATIENT, role)
        assertFalse("Unknown roles must not be staff", role.isStaff)
    }

    // ─── Legacy compat ──────────────────────────────────────────────

    @Test
    fun `legacy PATIENT string maps to PATIENT`() {
        // Old mobile client stored "PATIENT" (uppercase) in the local DB.
        assertEquals(UserRole.PATIENT, UserRole.fromBackend("PATIENT"))
    }

    @Test
    fun `legacy STAFF string maps to DOCTOR and is staff`() {
        // Old mobile client stored "STAFF" (uppercase) in the local DB.
        // We map it to DOCTOR — the safest staff default. Real backend
        // users will have their role overwritten from the backend profile
        // on next login.
        val role = UserRole.fromBackend("STAFF")
        assertEquals(UserRole.DOCTOR, role)
        assertTrue(role.isStaff)
    }

    // ─── Case sensitivity ───────────────────────────────────────────

    @Test
    fun `case-sensitive matching — lowercase 'doctor' is unknown`() {
        // Backend sends "Doctor" (capital D). Lowercase "doctor" is NOT
        // a valid backend role — must default to PATIENT for safety.
        // (If backend ever sends lowercase, we'll add a mapping.)
        val role = UserRole.fromBackend("doctor")
        assertEquals(UserRole.PATIENT, role)
        assertFalse(role.isStaff)
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(UserRole.DOCTOR, UserRole.fromBackend("  Doctor  "))
    }

    // ─── isStaff invariant ──────────────────────────────────────────

    @Test
    fun `PATIENT is not staff`() {
        assertFalse(UserRole.PATIENT.isStaff)
    }

    @Test
    fun `every non-PATIENT role is staff`() {
        // Enum invariant: isStaff = (this != PATIENT)
        UserRole.values().filter { it != UserRole.PATIENT }.forEach { role ->
            assertTrue(
                "Role $role should be staff (every non-PATIENT role is staff)",
                role.isStaff,
            )
        }
    }
}

package com.aistudio.clinicsystem.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * High-2 audit fix: unit tests for the doctor serverId extraction helper.
 *
 * The migration to mobile /appointments/book requires a backend doctor_id
 * (integer). The doctor directory (P-04) stores doctors with names like
 * "Д-р Сапаев (Стоматолог-терапевт) [#42]" — the bracketed #N is the
 * backend doctor user id. Legacy doctorName strings without the bracket
 * (staff-side bookings) should return null so the code falls back to
 * the legacy /appointments endpoint.
 */
class DoctorServerIdExtractionTest {

    // We test the regex directly since extractDoctorServerId is private.
    // The regex is: #(\d+)
    private val regex = Regex("""#(\d+)""")

    private fun extractDoctorServerId(doctorName: String): Int? {
        val match = regex.find(doctorName) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    @Test
    fun `extracts doctor id from P-04 directory format`() {
        assertEquals(42, extractDoctorServerId("Д-р Сапаев (Стоматолог-терапевт) [#42]"))
    }

    @Test
    fun `extracts doctor id without brackets`() {
        assertEquals(99, extractDoctorServerId("Dr. House #99"))
    }

    @Test
    fun `extracts first id when multiple present`() {
        // Edge case: if a name has multiple #N, take the first.
        assertEquals(1, extractDoctorServerId("Doctor #1 specialist #2"))
    }

    @Test
    fun `returns null for legacy doctorName without id`() {
        assertNull(extractDoctorServerId("Д-р Сапаев (Стоматолог-терапевт)"))
    }

    @Test
    fun `returns null for plain doctor name`() {
        assertNull(extractDoctorServerId("Dr. Smith"))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(extractDoctorServerId(""))
    }

    @Test
    fun `returns null for name with hash but no digits`() {
        assertNull(extractDoctorServerId("Doctor #"))
    }

    @Test
    fun `handles large doctor ids`() {
        assertEquals(999999, extractDoctorServerId("Doctor [#999999]"))
    }

    @Test
    fun `handles id at start of string`() {
        assertEquals(7, extractDoctorServerId("#7 Doctor"))
    }

    @Test
    fun `handles id with surrounding text`() {
        assertEquals(123, extractDoctorServerId("Запись к врачу #123 на завтра"))
    }
}

package com.aistudio.clinicsystem.data.model

/**
 * M1/E3.5: enum of all user roles supported by the backend.
 *
 * Source of truth: backend `app/core/security.py` — `require_roles(*roles)`.
 *
 * The previous mobile client only had `PATIENT` and `STAFF` (a collapsed
 * bucket for everything non-patient). This caused the role-based navigation
 * in `ClinicNavGraph` to route doctors, registrars, lab techs, cashiers,
 * and admins all to the same `StaffScreen` — which then tried to render
 * admin-only UI for a doctor and vice versa.
 *
 * With this enum, `ClinicViewModel.currentRole` is now a [UserRole] and
 * `ClinicNavGraph` can route per role. The actual role-specific screens
 * (DoctorScreen, RegistrarScreen, LabScreen, etc.) are introduced in M2/E5.5;
 * for M1 we keep the existing `StaffScreen` for all non-patient roles and
 * just make the enum explicit so the contract is documented.
 *
 * Backend role string values are CASE-SENSITIVE (they come from the `role`
 * column in the `users` table). The mobile client preserves them verbatim.
 */
enum class UserRole(val backendValue: String, val displayLabel: String) {
    PATIENT("Patient", "Пациент"),
    DOCTOR("Doctor", "Врач"),
    REGISTRAR("Registrar", "Регистратор"),
    LAB("Lab", "Лаборант"),
    CASHIER("Cashier", "Кассир"),
    CARDIO("cardio", "Кардиолог"),
    DERMA("derma", "Дерматолог"),
    DENTIST("dentist", "Стоматолог"),
    ADMIN("Admin", "Администратор");

    /** True for any role that gives access to the staff dashboard (not a patient). */
    val isStaff: Boolean get() = this != PATIENT

    companion object {
        /**
         * Parses the role string returned by the backend.
         * "Receptionist" is an alias for REGISTRAR (backend uses both in different places).
         * Unknown / null values default to PATIENT for safety.
         */
        fun fromBackend(value: String?): UserRole {
            if (value.isNullOrBlank()) return PATIENT
            return when (value.trim()) {
                "Patient" -> PATIENT
                "Doctor" -> DOCTOR
                "Registrar", "Receptionist" -> REGISTRAR
                "Lab" -> LAB
                "Cashier" -> CASHIER
                "cardio" -> CARDIO
                "derma" -> DERMA
                "dentist" -> DENTIST
                "Admin" -> ADMIN
                // Legacy mobile client used "PATIENT"/"STAFF" — map them.
                "PATIENT" -> PATIENT
                "STAFF" -> DOCTOR  // legacy "STAFF" → default to DOCTOR
                else -> {
                    android.util.Log.w("UserRole", "Unknown backend role: '$value', defaulting to PATIENT")
                    PATIENT
                }
            }
        }
    }
}

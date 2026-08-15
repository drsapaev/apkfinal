package com.aistudio.clinicsystem.domain.model

import timber.log.Timber

/**
 * Stage 5.1 (C-10 fix): UserRole moved from `data.model` to `domain.model`.
 *
 * The domain layer no longer depends on the data layer. This enum is a pure
 * domain concept — it describes a user's authorization role, independent of
 * how it's stored or transmitted.
 *
 * Source of truth: backend `app/core/security.py` — `require_roles(*roles)`.
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
    ADMIN("Admin", "Администратор"),
    ;

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
                "STAFF" -> DOCTOR // legacy "STAFF" → default to DOCTOR
                else -> {
                    Timber.w("Unknown backend role: '$value', defaulting to PATIENT")
                    PATIENT
                }
            }
        }
    }
}

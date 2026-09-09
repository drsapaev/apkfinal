package com.aistudio.clinicsystem.data.repository

import androidx.room.withTransaction
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.DoctorEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P-04: DoctorRepository — справочник врачей с offline-first кешированием.
 *
 * Архитектура: Room (Single Source of Truth) + NetworkBoundResource.
 *
 * Сценарии:
 * 1. Приложение запускается → DAO отдаёт кешированных врачей мгновенно
 * 2. Если кеш устарел (>24ч) или пуст → fetch с backend → обновление кеша
 * 3. Если сети нет → пользователь видит кешированных врачей
 * 4. Если backend вернул 304 Not Modified → кеш не обновляется
 *
 * Зависимости:
 * - MobileApiService.getDoctors() — GET /api/v1/mobile/doctors
 * - MobileApiService.getDoctorSchedule() — GET /api/v1/mobile/doctors/{id}/schedule?date_from&date_to
 * - ClinicDatabase.doctorDao() — Room DAO
 *
 * Контракт backend (`final/backend`):
 * - GET /api/v1/mobile/doctors → [{id, name, specialty, cabinet, active}]
 * - GET /api/v1/mobile/doctors/{id}/schedule → {doctor_id, schedule: [
 *     {date, weekday, start_time, end_time, appointments: [...]}]} —
 *   свободные слоты вычисляются на клиенте (окно работы минус занятые приёмы).
 */
@Singleton
class DoctorRepository
    @Inject
    constructor(
        private val database: ClinicDatabase,
        private val apiService: MobileApiService,
    ) {
        private val doctorDao = database.doctorDao()

        /**
         * Flow всех активных врачей из локального кеша.
         * UI подписывается на этот Flow и получает мгновенные обновления.
         */
        val allDoctors: Flow<List<DoctorEntity>> = doctorDao.getAllDoctors()

        /**
         * Sync doctors from backend. Call this on app startup or when user
         * pulls to refresh.
         *
         * @return true if sync succeeded, false on network/server error
         */
        suspend fun syncDoctors(): Boolean =
            try {
                val response = apiService.getDoctors()
                if (response.isSuccessful) {
                    val doctors = response.body() ?: emptyList()
                    database.withTransaction {
                        doctorDao.clearDoctors()
                        doctorDao.insertDoctors(doctors.map { it.toEntity() })
                    }
                    Timber.i("P-04: synced ${doctors.size} doctors from backend")
                    true
                } else if (response.code() == 304) {
                    Timber.i("P-04: doctors not modified (304), keeping cache")
                    true
                } else {
                    Timber.w("P-04: syncDoctors failed with code ${response.code()}")
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "P-04: syncDoctors network error")
                false
            }

        /**
         * Should we refresh from network? True if cache is empty or older than 24h.
         */
        suspend fun shouldRefresh(): Boolean {
            val count = doctorDao.getDoctorCount()
            if (count == 0) return true

            val lastUpdated = doctorDao.getLastUpdated() ?: return true
            val dayMs = 24 * 60 * 60 * 1000L
            return (System.currentTimeMillis() - lastUpdated) > dayMs
        }

        /**
         * Get available time slots for a doctor on a specific date.
         *
         * The backend has no dedicated /slots endpoint — slots are derived
         * CLIENT-SIDE from GET /api/v1/mobile/doctors/{id}/schedule: the
         * doctor's working window (start_time..end_time) is split into
         * 30-minute steps and every slot already booked by an appointment is
         * marked unavailable.
         *
         * @param doctorServerId backend-assigned doctor ID
         * @param date "2026-06-29" format
         * @return list of available "HH:MM" time strings, or empty list on error
         */
        suspend fun getAvailableTimeSlots(
            doctorServerId: Int,
            date: String,
        ): List<String> =
            try {
                val response =
                    apiService.getDoctorSchedule(
                        doctorId = doctorServerId,
                        dateFrom = date,
                        dateTo = date,
                    )
                if (response.isSuccessful) {
                    val slots =
                        response
                            .body()
                            ?.let { deriveTimeSlots(it, date) }
                            ?.filter { it.available }
                            ?.map { it.time }
                            ?: emptyList()
                    if (slots.isEmpty()) {
                        Timber.i("P-04: no free slots for doctor $doctorServerId on $date")
                    }
                    slots
                } else {
                    Timber.w("P-04: getAvailableTimeSlots failed with code ${response.code()}")
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "P-04: getAvailableTimeSlots network error")
                emptyList()
            }

        /**
         * Derives 30-minute [TimeSlotDto]s for [date] from the doctor's
         * schedule grid. Returns an empty list when the doctor has no working
         * window that day (start_time/end_time are null on days off).
         */
        private fun deriveTimeSlots(
            schedule: com.aistudio.clinicsystem.data.api.DoctorScheduleResponse,
            date: String,
        ): List<com.aistudio.clinicsystem.data.api.TimeSlotDto> {
            val day =
                schedule.schedule.firstOrNull { it.date == date }
                    ?: return emptyList()
            val startMinutes = parseHhMm(day.startTime) ?: return emptyList()
            val endMinutes = parseHhMm(day.endTime) ?: return emptyList()
            if (endMinutes <= startMinutes) return emptyList()

            val bookedByTime =
                day.appointments
                    .mapNotNull { appt -> parseHhMm(appt.appointmentTime) }
                    .toSet()

            val slots = mutableListOf<com.aistudio.clinicsystem.data.api.TimeSlotDto>()
            var cursor = startMinutes
            while (cursor < endMinutes) {
                val isBooked = cursor in bookedByTime
                slots.add(
                    com.aistudio.clinicsystem.data.api.TimeSlotDto(
                        time = formatHhMm(cursor),
                        available = !isBooked,
                        appointmentId =
                            if (isBooked) {
                                day.appointments
                                    .firstOrNull { parseHhMm(it.appointmentTime) == cursor }
                                    ?.id
                            } else {
                                null
                            },
                    ),
                )
                cursor += SLOT_STEP_MINUTES
            }
            return slots
        }

        /** Parses "HH:MM" / "HH:MM:SS" into minutes since midnight, or null. */
        private fun parseHhMm(value: String?): Int? {
            if (value.isNullOrBlank()) return null
            val parts = value.split(":")
            if (parts.size < 2) return null
            val hours = parts[0].trim().toIntOrNull() ?: return null
            val minutes = parts[1].trim().toIntOrNull() ?: return null
            if (hours !in 0..23 || minutes !in 0..59) return null
            return hours * 60 + minutes
        }

        private fun formatHhMm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

        private companion object {
            /** Fixed slot granularity used by the clinic for booking. */
            const val SLOT_STEP_MINUTES = 30
        }

        /**
         * TASK-4: demo-doctor seeding REMOVED from the production flow.
         * A clean installation now shows the REAL directory from the backend
         * or an explicit loading/empty/error state — never fictitious
         * doctors that a patient could book.
         */
    }

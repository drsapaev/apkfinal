package com.aistudio.clinicsystem.data.repository

import androidx.room.withTransaction
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.DoctorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * - MobileApiService.getDoctorTimeSlots() — GET /api/v1/mobile/doctors/{id}/slots?date=...
 * - ClinicDatabase.doctorDao() — Room DAO
 *
 * TODO (backend):
 * - Реализовать GET /api/v1/mobile/doctors на FastAPI backend
 * - Реализовать GET /api/v1/mobile/doctors/{id}/slots?date=YYYY-MM-DD
 * - Добавить ETag support для If-None-Match
 * - Возвращать только активных врачей (is_active=true) по умолчанию
 */
@Singleton
class DoctorRepository @Inject constructor(
    private val database: ClinicDatabase,
    private val apiService: MobileApiService
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
    suspend fun syncDoctors(): Boolean {
        return try {
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
     * @param doctorServerId backend-assigned doctor ID
     * @param date "2026-06-29" format
     * @return list of available time slots, or empty list on error
     */
    suspend fun getAvailableTimeSlots(doctorServerId: Int, date: String): List<String> {
        return try {
            val response = apiService.getDoctorTimeSlots(doctorServerId, date)
            if (response.isSuccessful) {
                response.body()
                    ?.filter { it.available }
                    ?.map { it.time }
                    ?: emptyList()
            } else {
                Timber.w("P-04: getAvailableTimeSlots failed with code ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "P-04: getAvailableTimeSlots network error")
            emptyList()
        }
    }

    /**
     * Seed fallback doctors when backend is unavailable and cache is empty.
     *
     * This provides a graceful degradation path — the app remains usable
     * even if backend is down on first launch. These doctors get replaced
     * by real data on next successful sync.
     */
    suspend fun seedFallbackDoctorsIfEmpty() {
        val count = doctorDao.getDoctorCount()
        if (count > 0) return

        val fallback = listOf(
            DoctorEntity(
                fullName = "Dr. Rustam Sapaev",
                specialty = "Стоматолог-Хирург",
                phone = "+7 999 123-45-67"
            ),
            DoctorEntity(
                fullName = "Dr. Elena Petrova",
                specialty = "Кардиолог",
                phone = "+7 999 234-56-78"
            ),
            DoctorEntity(
                fullName = "Dr. Alexander Smirnov",
                specialty = "Невролог",
                phone = "+7 999 345-67-89"
            )
        )
        doctorDao.insertDoctors(fallback)
        Timber.i("P-04: seeded ${fallback.size} fallback doctors (backend unavailable)")
    }
}

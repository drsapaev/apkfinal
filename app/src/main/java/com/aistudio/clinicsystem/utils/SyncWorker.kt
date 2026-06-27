package com.aistudio.clinicsystem.utils

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Stage 3.12: SyncWorker is now @HiltWorker — dependencies injected via
 * AssistedInject (Hilt's mechanism for Workers, which require the
 * `appContext` + `workerParams` to be provided by the WorkManager runtime).
 *
 * Closes the original code's manual `ClinicRepository(db)` + `SessionRepository(
 * SessionManagerImpl.getInstance(applicationContext))` construction —
 * now both come from the Hilt singleton graph, sharing the same instances
 * as the ViewModels and RealtimeManager.
 *
 * The worker's `doWork()` returns:
 *   - `Result.success()` if all outbox rows were processed successfully
 *   - `Result.retry()` if any row failed with a retriable error (5xx,
 *     IOException, 401, 408, 429). WorkManager will retry with exponential
 *     backoff (30s, 60s, 120s, ...).
 *   - `Result.failure()` if all remaining rows are DEAD_LETTER (no point
 *     in retrying — they need manual intervention).
 *
 * Note: non-retriable 4xx failures are moved to DEAD_LETTER immediately
 * by [ClinicRepository.retryUnsyncedWrites] (Stage 3.6 / NET-7 fix),
 * so this worker does NOT return Result.retry() for them.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val clinicRepository: ClinicRepository,
    private val sessionRepository: SessionRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Запущен фоновый процесс обработки отложенных транзакций (WorkManager)...")
        val startTime = System.currentTimeMillis()
        return try {
            // Stage 2.2: token comes from the SSOT SessionRepository.
            val token = sessionRepository.accessToken

            val success = clinicRepository.retryUnsyncedWrites(token)
            val latency = System.currentTimeMillis() - startTime

            if (success) {
                SyncMetricsManager.recordSuccess(latency)
                Log.d("SyncWorker", "Фоновая синхронизация завершена успешно за ${latency}мс")
                Result.success()
            } else {
                SyncMetricsManager.recordFailure()
                Log.w("SyncWorker", "Сбой фоновой синхронизации, планируем повтор.")
                // Stage 3.6: retry is safe — claimForProcessing is atomic,
                // so retried rows won't be double-processed. Non-retriable
                // rows are already in DEAD_LETTER and won't be picked up.
                Result.retry()
            }
        } catch (e: Exception) {
            SyncMetricsManager.recordFailure()
            Log.e("SyncWorker", "Исключение в фоновом воркере WorkManager", e)
            Result.retry()
        }
    }
}

package com.aistudio.clinicsystem.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.repository.ClinicRepository

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Запущен фоновый процесс обработки отложенных транзакций (WorkManager)...")
        val startTime = System.currentTimeMillis()
        try {
            val db = ClinicDatabase.getDatabase(applicationContext)
            val repository = ClinicRepository(db)
            // M3B.1: use SessionRepository as SSOT for token access
            val sessionRepository = com.aistudio.clinicsystem.data.session.SessionRepository(
                SessionManagerImpl.getInstance(applicationContext)
            )
            val token = sessionRepository.accessToken
            
            val success = repository.retryUnsyncedWrites(token)
            val latency = System.currentTimeMillis() - startTime
            
            if (success) {
                SyncMetricsManager.recordSuccess(latency)
                Log.d("SyncWorker", "Фоновая синхронизация завершена успешно за ${latency}мс")
                return Result.success()
            } else {
                SyncMetricsManager.recordFailure()
                Log.w("SyncWorker", "Сбой фоновой синхронизации, планируем повтор.")
                return Result.retry()
            }
        } catch (e: Exception) {
            SyncMetricsManager.recordFailure()
            Log.e("SyncWorker", "Исключение в фоновом воркере WorkManager", e)
            return Result.retry()
        }
    }
}

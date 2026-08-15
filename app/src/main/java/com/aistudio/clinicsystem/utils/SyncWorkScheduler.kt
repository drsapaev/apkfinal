package com.aistudio.clinicsystem.utils

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Stage 3.7 (NET-8 / PERF-13 / M-6 fix): WorkManager scheduling with
 * production-grade constraints.
 *
 * Changes from the previous version:
 *   1. Periodic sync interval: 15 min → 6 hours. The always-on WebSocket
 *      (Stage 2.3) delivers real-time updates; the periodic sync exists
 *      ONLY to retry failed outbox rows, not to do full sync.
 *   2. Constraints now include `setRequiresBatteryNotLow(true)` and
 *      `setRequiresStorageNotLow(true)` — a patient at 5% battery should
 *      not trigger a sync that holds a wakelock for 2-10 seconds.
 *   3. Immediate sync (triggered by NetworkMonitor.onAvailable) is now
 *      expedited via `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`
 *      — runs immediately if the system has expedited quota, otherwise
 *      falls back to a normal work request (still runs, just not within
 *      3 seconds).
 *   4. Backoff: 30 seconds (was 10) — gives the server more time to
 *      recover from a transient outage before we hammer it again.
 */
object SyncWorkScheduler {

    /**
     * Schedules the periodic outbox-retry worker. Should be called ONCE
     * per process — from [com.aistudio.clinicsystem.ClinicSystemApplication.onCreate].
     *
     * The periodic worker ONLY retries failed outbox rows. It does NOT
     * do a full server sync — that is the responsibility of:
     *   - The always-on WebSocket (real-time updates)
     *   - The delta sync triggered by NetworkMonitor.onAvailable
     *   - The manual "refresh" button in the UI
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        // Stage 3.7 (PERF-13): 6 hours — was 15 minutes. 15 min was the
        // WorkManager minimum, which is aggressive for a medical app that
        // already has a real-time WebSocket. 6 hours is enough to catch
        // any outbox rows that failed all retries during normal usage.
        val syncRequest = PeriodicWorkRequest.Builder(
            SyncWorker::class.java,
            6,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, // was 10 — more forgiving for transient outages
                TimeUnit.SECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "PeriodicSyncWork",
            // KEEP: if a previous schedule exists, don't replace it. This
            // makes the call idempotent across Application.onCreate invocations.
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    /**
     * Triggers an immediate one-time sync. Called by NetworkMonitor.onAvailable
     * when network is restored after being lost.
     *
     * The work is expedited — runs within ~3 seconds if the system has
     * expedited quota, otherwise falls back to a normal work request.
     */
    fun triggerImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            // Stage 3.7: expedited — runs immediately if quota available.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "ImmediateSyncWork",
            // REPLACE: if a previous immediate sync is still running, cancel
            // it and start a new one. The user just got network back; they
            // want the LATEST state, not a stale sync.
            ExistingWorkPolicy.REPLACE,
            syncRequest,
        )
    }
}

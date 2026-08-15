package com.aistudio.clinicsystem

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.aistudio.clinicsystem.data.realtime.RealtimeManager
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.utils.NetworkMonitor
import com.aistudio.clinicsystem.utils.SyncWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Stage 2.9: ClinicSystemApplication — Hilt entry point + process-level
 * lifecycle observer for WebSocket start/stop.
 *
 * Stage 3.12: implements [Configuration.Provider] so that
 * [SyncWorker] @HiltWorker can be constructed by WorkManager via the
 * Hilt-injected [HiltWorkerFactory].
 *
 * Closes audit findings H-3 (RealtimeManager never reconnected), PERF-12
 * (WebSocket always-on in background), M-1 (NetworkMonitor registered
 * multiple times).
 *
 * Responsibilities (all run ONCE per process):
 *   1. Hilt: `@HiltAndroidApp` triggers Hilt codegen + dependency graph.
 *   2. SessionRepository.restoreSession() — async verify cached tokens.
 *   3. RealtimeManager.initialize() — set up session state observers.
 *   4. NetworkMonitor.startMonitoring() — register connectivity callback.
 *   5. SyncWorkScheduler.schedulePeriodicSync() — schedule the 6-hour
 *      periodic outbox retry worker (Stage 3.7).
 *   6. ProcessLifecycleObserver — ON_START → realtimeManager.start(),
 *      ON_STOP → realtimeManager.stop(). This means the WebSocket is
 *      connected ONLY when the app is in the foreground, saving battery
 *      (PERF-12 fix). Real-time updates in the background require FCM
 *      (Stage 9 — not yet implemented).
 *   7. [workerFactory] — Hilt-provided, allows @HiltWorker-annotated
 *      workers to be constructed via AssistedInject (Stage 3.12).
 */
@HiltAndroidApp
class ClinicSystemApplication : Application(), Configuration.Provider {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var realtimeManager: RealtimeManager
    @Inject lateinit var networkMonitor: NetworkMonitor
    // Stage 3.12: HiltWorkerFactory — injected so that @HiltWorker classes
    // (SyncWorker) can be constructed by WorkManager.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Stage 3.12: required by Configuration.Provider. Returning the
    // HiltWorkerFactory means WorkManager will use Hilt to construct
    // @HiltWorker-annotated workers, getting all injected dependencies
    // from the singleton graph.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Stage 4.1 (C-4 final): plant Timber trees BEFORE anything else
        // logs. Debug → DebugTree (full logging); Release → ReleaseTree
        // (WARN/ERROR only, PHI redacted).
        com.aistudio.clinicsystem.utils.initTimber()

        // 1. Restore session — async verify cached tokens against backend.
        sessionRepository.restoreSession(authRepository)

        // 2. Initialize the WebSocket manager — sets up session state
        //    observer (auto start/stop) + token rotation observer (reconnect
        //    with new token after refresh).
        realtimeManager.initialize()

        // 3. Register network callback ONCE (per process). NetworkMonitor
        //    is @Singleton — its startMonitoring() is idempotent.
        networkMonitor.startMonitoring()

        // 4. Schedule periodic sync (outbox retry). Stage 3.7 set the
        //    interval to 6 hours with battery-not-low + storage-not-low
        //    constraints.
        SyncWorkScheduler.schedulePeriodicSync(this)

        // 5. Tie WebSocket lifecycle to app foreground/background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    realtimeManager.start()
                }

                override fun onStop(owner: LifecycleOwner) {
                    realtimeManager.stop()
                }
            },
        )
    }
}

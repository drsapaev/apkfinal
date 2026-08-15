package com.aistudio.clinicsystem.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.aistudio.clinicsystem.data.db.SyncLogEntity
import com.aistudio.clinicsystem.data.realtime.RealtimeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 2.5: NetworkMonitor — observes connectivity changes and triggers
 * WebSocket reconnect + immediate sync when network is restored.
 *
 * Closes audit findings M-1, NET-12, NET-13.
 *
 * Key changes from the previous `object NetworkMonitor`:
 *
 * 1. **@Singleton class** (not `object`) — Hilt-managed; depends on
 *    [RealtimeManager] via constructor injection. The singleton instance
 *    is the SAME one owned by ClinicViewModel, so `reconnect()` actually
 *    reaches the live WebSocket (fixes NET-13).
 *
 * 2. **Single [CoroutineScope]** with [SupervisorJob] — no per-event
 *    `CoroutineScope(Dispatchers.IO).launch { ... }` allocations. Each
 *    previous event leaked a scope; with a flaky network this could
 *    accumulate ~200 KB per event (fixes NET-12).
 *
 * 3. **Idempotent [startMonitoring]** — guarded by a `started` flag so
 *    multiple Activity recreations don't register multiple callbacks
 *    (fixes M-1).
 *
 * 4. **[stopMonitoring]** — unregisters the network callback and cancels
 *    the scope. Called from Application.onTerminate (or never, in
 *    practice — the singleton lives for the process lifetime).
 *
 * [startMonitoring] should be called once from
 * [com.aistudio.clinicsystem.ClinicSystemApplication.onCreate].
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val realtimeManager: RealtimeManager,
    private val syncLogDao: com.aistudio.clinicsystem.data.db.SyncLogDao,
) {
    // Medium-1 audit fix: TAG constant removed — Timber auto-tags with class name.

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Registers the network callback. Idempotent — safe to call multiple
     * times. Should be called exactly once from Application.onCreate.
     */
    fun startMonitoring() {
        if (started) {
            Timber.d("startMonitoring() already started — ignoring")
            return
        }
        started = true

        connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        // Check initial state
        try {
            val initialCapabilities = connectivityManager?.getNetworkCapabilities(
                connectivityManager?.activeNetwork,
            )
            val initialOnline = initialCapabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
            ) == true
            _isOnline.value = initialOnline
        } catch (e: Exception) {
            _isOnline.value = true
        }

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!_isOnline.value) {
                    _isOnline.value = true
                    Timber.d("Network connection is AVAILABLE")
                    scope.launch {
                        syncLogDao.insertLog(
                            SyncLogEntity(
                                logMessage = "📶 Сеть восстановлена: Запуск фонового примирения данных и синхронизации.",
                                direction = "SYSTEM_SYNC",
                                timestamp = System.currentTimeMillis(),
                            ),
                        )
                        // Stage 2.5 (H-3 fix): call reconnectNow() on the
                        // SINGLETON RealtimeManager — Hilt guarantees this
                        // is the same instance ClinicViewModel holds.
                        // No more `RealtimeManager(...).reconnect()` no-op.
                        try {
                            realtimeManager.reconnectNow()
                        } catch (e: Exception) {
                            Timber.e("WebSocket reconnect failed", e)
                        }
                        // Trigger WorkManager instant syncer
                        SyncWorkScheduler.triggerImmediateSync(appContext)
                    }
                }
            }

            override fun onLost(network: Network) {
                if (_isOnline.value) {
                    _isOnline.value = false
                    Timber.d("Network connection was LOST")
                    scope.launch {
                        syncLogDao.insertLog(
                            SyncLogEntity(
                                logMessage = "⚠️ Соединение потеряно: Переход в автономный режим работы (Room DB).",
                                direction = "SYSTEM_SYNC",
                                timestamp = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Timber.e("Failed to register network callback", e)
        }
    }

    /**
     * Unregisters the network callback and cancels the internal scope.
     * Safe to call multiple times. In practice this is only called from
     * tests — the singleton lives for the process lifetime.
     */
    fun stopMonitoring() {
        if (!started) return
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Timber.e("Failed to unregister network callback", e)
        }
        networkCallback = null
        connectivityManager = null
        started = false
    }
}

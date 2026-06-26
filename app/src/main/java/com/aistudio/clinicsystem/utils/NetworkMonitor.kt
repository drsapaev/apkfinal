package com.aistudio.clinicsystem.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.SyncLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring(context: Context) {
        val appContext = context.applicationContext
        connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check initial state
        try {
            val initialCapabilities = connectivityManager?.getNetworkCapabilities(connectivityManager?.activeNetwork)
            val initialOnline = initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            _isOnline.value = initialOnline
        } catch (e: Exception) {
            _isOnline.value = true
        }

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!_isOnline.value) {
                    _isOnline.value = true
                    Log.d("NetworkMonitor", "Network connection is AVAILABLE")
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = ClinicDatabase.getDatabase(appContext)
                        db.syncLogDao().insertLog(
                            SyncLogEntity(
                                logMessage = "📶 Сеть восстановлена: Запуск фонового примирения данных и синхронизации.",
                                direction = "SYSTEM_SYNC",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        
                        // Reconnect WebSocket
                        try {
                            ClinicWebSocketClient.getInstance(appContext, db).start(forceReconnect = true)
                        } catch (e: Exception) {
                            Log.e("NetworkMonitor", "WebSocket reconnect failed", e)
                        }

                        // Trigger work manager instant syncer
                        SyncWorkScheduler.triggerImmediateSync(appContext)
                    }
                }
            }

            override fun onLost(network: Network) {
                if (_isOnline.value) {
                    _isOnline.value = false
                    Log.d("NetworkMonitor", "Network connection was LOST")
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = ClinicDatabase.getDatabase(appContext)
                        db.syncLogDao().insertLog(
                            SyncLogEntity(
                                logMessage = "⚠️ Соединение потеряно: Переход в автономный режим работы (Room DB).",
                                direction = "SYSTEM_SYNC",
                                timestamp = System.currentTimeMillis()
                            )
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
            Log.e("NetworkMonitor", "Failed to register network callback", e)
        }
    }
}

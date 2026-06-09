package com.example.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncMetrics(
    val lastSyncTime: Long = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastLatencyMs: Long = 0,
    val wsState: String = "DISCONNECTED"
)

object SyncMetricsManager {
    private val _metrics = MutableStateFlow(SyncMetrics())
    val metrics: StateFlow<SyncMetrics> = _metrics.asStateFlow()

    fun recordSuccess(latencyMs: Long) {
        val current = _metrics.value
        _metrics.value = current.copy(
            lastSyncTime = System.currentTimeMillis(),
            successCount = current.successCount + 1,
            lastLatencyMs = latencyMs
        )
    }

    fun recordFailure() {
        val current = _metrics.value
        _metrics.value = current.copy(
            lastSyncTime = System.currentTimeMillis(),
            failureCount = current.failureCount + 1
        )
    }

    fun updateWsState(newState: String) {
        val current = _metrics.value
        _metrics.value = current.copy(
            wsState = newState
        )
    }
}

package com.aistudio.clinicsystem.utils

import timber.log.Timber

/**
 * Stage 4.1 (L-1 fix): AnalyticsManager — now uses Timber instead of Log.
 *
 * In debug builds: events are logged to Logcat for debugging.
 * In release builds: the ReleaseTree drops INFO logs entirely; events
 * are NOT logged at all unless they're WARN/ERROR severity.
 *
 * For real production analytics, integrate Firebase Analytics or
 * Sentry (Stage 9 — Enterprise features). This stub is for dev only.
 *
 * Stage 4.1 also fixes the typo "Traacked" → "Tracked".
 */
object AnalyticsManager {
    fun logEvent(eventName: String, params: Map<String, Any>? = null) {
        // Note: in release builds this message will be DROPPED by ReleaseTree
        // (INFO level). If you need events in release, use Firebase Analytics
        // or Sentry — see Stage 9.
        val paramString = params?.entries?.joinToString { "${it.key}=${it.value}" } ?: "no params"
        Timber.i("Event Tracked: $eventName | $paramString")
    }

    fun trackScreen(screenName: String) {
        Timber.i("Screen View: $screenName")
    }
}

package com.aistudio.clinicsystem.utils

import android.util.Log

object AnalyticsManager {
    fun logEvent(eventName: String, params: Map<String, Any>? = null) {
        val paramString = params?.entries?.joinToString { "${it.key}=${it.value}" } ?: "no params"
        Log.i("AnalyticsManager", "Event Traacked: $eventName | $paramString")
    }

    fun trackScreen(screenName: String) {
        Log.i("AnalyticsManager", "Screen View: $screenName")
    }
}

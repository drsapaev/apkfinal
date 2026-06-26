package com.aistudio.clinicsystem.utils

import android.content.Context
import android.util.Log
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.repository.ClinicRepository

/**
 * FirestoreSyncManager — previously attempted to bootstrap Firebase Firestore
 * with a FAKE API key ("AIzaSyFakeKeyForRealtimeSyncSimulation") to simulate
 * real-time cloud sync. This was a security ship-blocker (E1.7):
 *
 *  - The fake key shape (AIzaSy...) looks like a real Google API key prefix.
 *  - If a real key was sanitized to "Fake" but the call site still attempted
 *    initialization, it would produce confusing crash logs and create the
 *    impression that real cloud sync was active when it wasn't.
 *  - Real-time sync is already handled by [ClinicWebSocketClient] (OkHttp
 *    WebSocket to /ws/queue on the backend). Firestore is redundant.
 *
 * M0/E1.7 resolution: Firestore initialization is DISABLED. All public methods
 * are now no-ops that log a debug message. This keeps the call sites in
 * [ClinicViewModel] and elsewhere compiling without modification, while
 * removing the security risk entirely.
 *
 * Future work (M2+): if real Firestore integration is needed, configure it
 * properly via `google-services.json` + `firebase-firestore` dependency with
 * a real project ID, and remove the no-op shims below.
 */
object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"

    private const val DISABLED_MESSAGE =
        "FirestoreSyncManager is disabled (M0/E1.7). " +
            "Real-time sync is handled by ClinicWebSocketClient. " +
            "Firestore initialization with fake API key was removed for security."

    /**
     * Previously initialized Firebase Firestore with a fake API key.
     * Now a no-op — kept for source compatibility with [ClinicViewModel.init].
     */
    fun init(context: Context, repository: ClinicRepository) {
        Log.d(TAG, "init() called — $DISABLED_MESSAGE")
    }

    /**
     * Previously published an appointment to Firestore.
     * Now a no-op — appointments are synced via the API + pending sync queue.
     */
    fun publishAppointment(appointment: AppointmentEntity) {
        Log.d(TAG, "publishAppointment(${appointment.id}) — $DISABLED_MESSAGE")
    }

    /**
     * Previously published a medical record to Firestore.
     * Now a no-op — medical records are synced via the API + pending sync queue.
     */
    fun publishMedicalRecord(record: MedicalRecordEntity) {
        Log.d(TAG, "publishMedicalRecord(${record.id}) — $DISABLED_MESSAGE")
    }
}

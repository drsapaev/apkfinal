package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.database.AppointmentEntity
import com.example.data.database.MedicalRecordEntity
import com.example.data.repository.ClinicRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"
    private var firestore: FirebaseFirestore? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isListenersConfigured = false
    private var applicationContext: Context? = null

    fun init(context: Context, repository: ClinicRepository) {
        try {
            applicationContext = context.applicationContext
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:567843210987:android:abcd1234abcd5678")
                    .setApiKey("AIzaSyFakeKeyForRealtimeSyncSimulation")
                    .setProjectId("intellect-clinic-realtime")
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            }
            firestore = FirebaseFirestore.getInstance()
            Log.d(TAG, "Firestore initialized successfully.")
            setupRealtimeListeners(repository)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore, running in default offline mode", e)
        }
    }

    private fun setupRealtimeListeners(repository: ClinicRepository) {
        if (isListenersConfigured) return
        val db = firestore ?: return
        isListenersConfigured = true

        Log.d(TAG, "Setting up real-time snapshot listeners for Firestore collections...")

        // 1. Real-time Listener for Appointments
        db.collection("appointments")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed for appointments from Firestore", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    val id = doc.getLong("id")?.toInt() ?: return@forEach
                    val patientPhone = doc.getString("patientPhone") ?: ""
                    val patientName = doc.getString("patientName") ?: ""
                    val doctorName = doc.getString("doctorName") ?: ""
                    val specialty = doc.getString("specialty") ?: ""
                    val date = doc.getString("date") ?: ""
                    val time = doc.getString("time") ?: ""
                    val status = doc.getString("status") ?: "PENDING"
                    val reason = doc.getString("reason") ?: ""
                    val notes = doc.getString("notes") ?: ""
                    val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                    val firestoreAppointment = AppointmentEntity(
                        id = id,
                        patientPhone = patientPhone,
                        patientName = patientName,
                        doctorName = doctorName,
                        specialty = specialty,
                        date = date,
                        time = time,
                        status = status,
                        reason = reason,
                        notes = notes,
                        updatedAt = updatedAt
                    )

                    scope.launch {
                        val existing = repository.getAppointmentById(id)
                        if (existing == null) {
                            repository.insertAppointment(firestoreAppointment)
                            repository.addSyncLog(
                                "🔥 FIRESTORE listener: Added appointment ID #$id (Status: $status)",
                                "CLOUD_SYNC_SIMULATOR"
                            )
                        } else if (existing.status != status || existing.notes != notes) {
                            repository.updateAppointment(firestoreAppointment)
                            repository.addSyncLog(
                                "🔥 FIRESTORE listener: Updated appointment ID #$id to state '$status'",
                                "CLOUD_SYNC_SIMULATOR"
                            )
                            
                            // Trigger local push notification on status change
                            val ctx = applicationContext
                            if (ctx != null) {
                                NotificationHelper.sendAppointmentStatusNotification(
                                    context = ctx,
                                    appointmentId = id,
                                    doctorName = doctorName,
                                    dateTimeString = "$date в $time",
                                    newStatus = status,
                                    patientName = patientName
                                )
                            }
                        }
                    }
                }
            }

        // 2. Real-time Listener for Medical Records
        db.collection("medical_records")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed for medical records from Firestore", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    if (change.type == DocumentChange.Type.ADDED) {
                        val id = doc.getLong("id")?.toInt() ?: return@forEach
                        val patientPhone = doc.getString("patientPhone") ?: ""
                        val doctorName = doc.getString("doctorName") ?: ""
                        val diagnosis = doc.getString("diagnosis") ?: ""
                        val prescription = doc.getString("prescription") ?: ""
                        val visitDate = doc.getString("visitDate") ?: ""
                        val recommendations = doc.getString("recommendations") ?: ""
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                        val firestoreRecord = MedicalRecordEntity(
                            id = id,
                            patientPhone = patientPhone,
                            doctorName = doctorName,
                            diagnosis = diagnosis,
                            prescription = prescription,
                            visitDate = visitDate,
                            recommendations = recommendations,
                            timestamp = timestamp
                        )

                        scope.launch {
                            val existing = repository.getMedicalRecordById(id)
                            if (existing == null) {
                                repository.insertMedicalRecord(firestoreRecord)
                                repository.addSyncLog(
                                    "🔥 FIRESTORE listener: Received new medical report ID #$id diagnosis: '$diagnosis'",
                                    "CLOUD_SYNC_SIMULATOR"
                                )

                                val patientUser = repository.getUserByPhone(patientPhone)
                                val patientName = patientUser?.fullName ?: "Пациент"

                                val ctx = applicationContext
                                if (ctx != null) {
                                    NotificationHelper.sendMedicalRecordNotification(
                                        context = ctx,
                                        recordId = id,
                                        doctorName = doctorName,
                                        diagnosis = diagnosis,
                                        patientName = patientName
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    // Publish Appointments to Firestore when changed
    fun publishAppointment(appointment: AppointmentEntity) {
        val db = firestore ?: return
        val data = hashMapOf(
            "id" to appointment.id,
            "patientPhone" to appointment.patientPhone,
            "patientName" to appointment.patientName,
            "doctorName" to appointment.doctorName,
            "specialty" to appointment.specialty,
            "date" to appointment.date,
            "time" to appointment.time,
            "status" to appointment.status,
            "reason" to appointment.reason,
            "notes" to appointment.notes,
            "updatedAt" to appointment.updatedAt
        )
        db.collection("appointments").document(appointment.id.toString())
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully published appointment ID #${appointment.id} to Firestore collection")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to publish appointment ID #${appointment.id} to Firestore", e)
            }
    }

    // Publish Medical Records to Firestore when created
    fun publishMedicalRecord(record: MedicalRecordEntity) {
        val db = firestore ?: return
        val data = hashMapOf(
            "id" to record.id,
            "patientPhone" to record.patientPhone,
            "doctorName" to record.doctorName,
            "diagnosis" to record.diagnosis,
            "prescription" to record.prescription,
            "visitDate" to record.visitDate,
            "recommendations" to record.recommendations,
            "timestamp" to record.timestamp
        )
        db.collection("medical_records").document(record.id.toString())
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully published medical record ID #${record.id} to Firestore collection")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to publish medical record ID #${record.id} to Firestore", e)
            }
    }
}

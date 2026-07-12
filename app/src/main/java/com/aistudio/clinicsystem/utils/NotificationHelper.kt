@file:Suppress("UnusedParameter")
package com.aistudio.clinicsystem.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aistudio.clinicsystem.MainActivity
import com.aistudio.clinicsystem.R

object NotificationHelper {
    private const val APPOINTMENT_CHANNEL_ID = "clinic_appointment_channel"
    private const val MEDICAL_CHANNEL_ID = "clinic_medical_channel"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appointmentChan = NotificationChannel(
                APPOINTMENT_CHANNEL_ID,
                context.getString(R.string.notif_channel_appointments),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_appointments_desc)
                enableLights(true)
                enableVibration(true)
            }

            val medicalChan = NotificationChannel(
                MEDICAL_CHANNEL_ID,
                context.getString(R.string.notif_channel_medical),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_medical_desc)
                enableLights(true)
                enableVibration(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(appointmentChan)
            manager.createNotificationChannel(medicalChan)
        }
    }

    fun sendAppointmentStatusNotification(
        context: Context,
        appointmentId: Int,
        doctorName: String,
        dateTimeString: String,
        newStatus: String,
        patientName: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            100 + appointmentId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (newStatus.uppercase()) {
            "APPROVED" -> context.getString(R.string.notif_appointment_approved)
            "CANCELLED", "REJECTED" -> context.getString(R.string.notif_appointment_cancelled)
            else -> context.getString(R.string.notif_appointment_update)
        }

        val statusText = when (newStatus.uppercase()) {
            "APPROVED" -> context.getString(R.string.notif_appointment_approved_text)
            "CANCELLED", "REJECTED" -> context.getString(R.string.notif_appointment_cancelled_text)
            else -> context.getString(R.string.notif_appointment_update_text)
        }

        val builder = NotificationCompat.Builder(context, APPOINTMENT_CHANNEL_ID)
            // Stage 4.7 (UI-34 fix): branded monochrome icon instead of
            // android.R.drawable.ic_dialog_info (generic system bubble).
            .setSmallIcon(R.drawable.ic_notification_appointment)
            .setContentTitle(title)
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Stage 4.7: VISIBILITY_PRIVATE — on lock screen, only the
            // public version ("Новое уведомление от клиники") is shown,
            // NOT the patient name or appointment details.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, APPOINTMENT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_appointment)
                    .setContentTitle(context.getString(R.string.notif_public_title))
                    .setContentText(context.getString(R.string.notif_public_text))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
            )

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(1000 + appointmentId, builder.build())
            } catch (e: SecurityException) {
                // Ignore missing permissions safely (runtime handle instead)
            }
        }
    }

    fun sendMedicalRecordNotification(
        context: Context,
        recordId: Int,
        doctorName: String,
        diagnosis: String,
        patientName: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            200 + recordId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = context.getString(R.string.notif_medical_record_text)

        val builder = NotificationCompat.Builder(context, MEDICAL_CHANNEL_ID)
            // Stage 4.7 (UI-34 fix): branded clipboard icon instead of
            // android.R.drawable.ic_menu_agenda (generic system list icon).
            .setSmallIcon(R.drawable.ic_notification_medical)
            .setContentTitle(context.getString(R.string.notif_medical_record_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Stage 4.7: VISIBILITY_PRIVATE — on lock screen, only the
            // public version is shown, NOT the diagnosis or prescription.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, MEDICAL_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_medical)
                    .setContentTitle(context.getString(R.string.notif_public_title))
                    .setContentText(context.getString(R.string.notif_public_text))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
            )

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(2000 + recordId, builder.build())
            } catch (e: SecurityException) {
                // Ignore missing permissions safely (runtime handle instead)
            }
        }
    }
}

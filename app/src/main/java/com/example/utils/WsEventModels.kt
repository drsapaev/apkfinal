package com.example.utils

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseWsEvent(
    val event: String?
)

@JsonClass(generateAdapter = true)
data class AppointmentStatusEvent(
    val event: String?,
    val data: AppointmentStatusData?
)

@JsonClass(generateAdapter = true)
data class AppointmentStatusData(
    val id: Int?,
    val status: String?,
    @param:Json(name="doctor_name") val doctorName: String?,
    val date: String?,
    val time: String?,
    @param:Json(name="patient_name") val patientName: String?,
    @param:Json(name="patient_phone") val patientPhone: String?,
    val specialty: String?,
    val reason: String?
)

@JsonClass(generateAdapter = true)
data class NewMedicalRecordEvent(
    val event: String?,
    val data: NewMedicalRecordData?
)

@JsonClass(generateAdapter = true)
data class NewMedicalRecordData(
    val id: Int?,
    @param:Json(name="patient_phone") val patientPhone: String?,
    @param:Json(name="doctor_name") val doctorName: String?,
    val diagnosis: String?,
    val prescription: String?,
    @param:Json(name="visit_date") val visitDate: String?,
    val recommendations: String?
)

@JsonClass(generateAdapter = true)
data class QueueUpdateEvent(
    val event: String?,
    val data: QueueUpdateData?
)

@JsonClass(generateAdapter = true)
data class QueueUpdateData(
    val queue: List<com.example.data.api.QueueDto>?
)

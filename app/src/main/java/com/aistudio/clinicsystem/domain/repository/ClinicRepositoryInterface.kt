package com.aistudio.clinicsystem.domain.repository

import com.aistudio.clinicsystem.domain.model.Appointment
import com.aistudio.clinicsystem.domain.model.MedicalRecord
import com.aistudio.clinicsystem.domain.model.PendingSync
import com.aistudio.clinicsystem.domain.model.QueuePosition
import kotlinx.coroutines.flow.Flow

interface ClinicRepositoryInterface {
    suspend fun bookAppointment(doctorId: Int, date: String, time: String, reason: String, clinicId: String?): Result<Appointment>
    suspend fun cancelAppointment(appointmentId: Int, reason: String): Result<Unit>
    suspend fun updateAppointmentStatus(appointmentId: Int, status: String, notes: String): Result<Appointment?>
    suspend fun fetchMedicalRecords(phone: String): Result<List<MedicalRecord>>
    suspend fun createMedicalRecord(patientPhone: String, doctorName: String, diagnosis: String, prescription: String, recommendations: String): Result<MedicalRecord>
    suspend fun registerInQueue(appointmentId: Int): Result<QueuePosition?>
    suspend fun syncAllFromServer(token: String?): Result<Boolean>
    suspend fun retryPendingSyncs(token: String?): Result<Boolean>
    fun observePendingSyncs(): Flow<List<PendingSync>>
    suspend fun dismissPendingSync(sync: PendingSync): Result<Unit>
}

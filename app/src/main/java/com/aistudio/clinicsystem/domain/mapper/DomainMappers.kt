package com.aistudio.clinicsystem.domain.mapper

import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import com.aistudio.clinicsystem.data.db.QueueSnapshotEntity
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.model.UserRole
import com.aistudio.clinicsystem.data.api.UserDto
import com.aistudio.clinicsystem.data.api.UserProfileResponse
import com.aistudio.clinicsystem.domain.model.Appointment
import com.aistudio.clinicsystem.domain.model.AppointmentStatus
import com.aistudio.clinicsystem.domain.model.MedicalRecord
import com.aistudio.clinicsystem.domain.model.PendingSync
import com.aistudio.clinicsystem.domain.model.QueuePosition
import com.aistudio.clinicsystem.domain.model.QueueStatus
import com.aistudio.clinicsystem.domain.model.User

fun UserEntity.toDomain(): User = User(id = id, phone = phone, fullName = fullName, role = UserRole.fromBackend(role), dateOfBirth = dateOfBirth, biometricEnabled = biometricEnabled, telegramChatId = telegramChatId, clinicId = clinicId)
fun UserDto.toDomain(): User = User(id = id, phone = phone, fullName = fullName, role = UserRole.fromBackend(role), dateOfBirth = dateOfBirth, biometricEnabled = biometricEnabled, telegramChatId = telegramChatId, clinicId = clinicId)
fun UserProfileResponse.toDomain(): User = User(id = id, phone = phone ?: "", fullName = fullName ?: "", role = UserRole.fromBackend(role), dateOfBirth = dateOfBirth, biometricEnabled = biometricEnabled ?: false, telegramChatId = telegramChatId, clinicId = clinicId)
fun AppointmentEntity.toDomain(): Appointment = Appointment(id = id, patientPhone = patientPhone, patientName = patientName, doctorName = doctorName, specialty = specialty, date = date, time = time, status = AppointmentStatus.fromString(status), reason = reason, notes = notes, clinicId = clinicId, updatedAt = updatedAt)
fun MedicalRecordEntity.toDomain(): MedicalRecord = MedicalRecord(id = id, patientPhone = patientPhone, doctorName = doctorName, diagnosis = diagnosis, prescription = prescription, visitDate = visitDate, recommendations = recommendations, timestamp = timestamp)
fun QueueSnapshotEntity.toDomain(): QueuePosition = QueuePosition(position = position, queueNumber = id, status = QueueStatus.fromString(status), queueId = id)
fun PendingSyncEntity.toDomain(): PendingSync = PendingSync(id = id, type = type, payload = payload, clientRequestId = clientRequestId, timestamp = timestamp, retryCount = retryCount)

package com.aistudio.clinicsystem.domain.usecase.medical

import com.aistudio.clinicsystem.domain.model.MedicalRecord
import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface

class FetchMedicalRecordsUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(phone: String): Result<List<MedicalRecord>> {
        if (phone.isBlank()) return Result.failure(IllegalArgumentException("Номер телефона не может быть пустым"))
        return repository.fetchMedicalRecords(phone)
    }
}

class CreateMedicalRecordUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(patientPhone: String, doctorName: String, diagnosis: String, prescription: String, recommendations: String = ""): Result<MedicalRecord> {
        if (patientPhone.isBlank()) return Result.failure(IllegalArgumentException("Номер телефона пациента обязателен"))
        if (doctorName.isBlank()) return Result.failure(IllegalArgumentException("Имя врача обязательно"))
        if (diagnosis.isBlank()) return Result.failure(IllegalArgumentException("Диагноз не может быть пустым"))
        return repository.createMedicalRecord(patientPhone, doctorName, diagnosis, prescription, recommendations)
    }
}

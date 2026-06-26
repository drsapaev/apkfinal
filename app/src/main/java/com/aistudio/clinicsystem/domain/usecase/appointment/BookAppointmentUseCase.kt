package com.aistudio.clinicsystem.domain.usecase.appointment

import com.aistudio.clinicsystem.domain.model.Appointment
import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface

class BookAppointmentUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(doctorId: Int, date: String, time: String, reason: String, clinicId: String? = null): Result<Appointment> {
        if (doctorId <= 0) return Result.failure(IllegalArgumentException("Выберите врача"))
        if (date.isBlank() || time.isBlank()) return Result.failure(IllegalArgumentException("Выберите дату и время"))
        return repository.bookAppointment(doctorId, date, time, reason, clinicId)
    }
}

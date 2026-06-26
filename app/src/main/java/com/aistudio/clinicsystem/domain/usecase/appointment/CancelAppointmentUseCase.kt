package com.aistudio.clinicsystem.domain.usecase.appointment

import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface

class CancelAppointmentUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(appointmentId: Int, reason: String): Result<Unit> {
        if (appointmentId <= 0) return Result.failure(IllegalArgumentException("Неверный ID записи"))
        if (reason.isBlank()) return Result.failure(IllegalArgumentException("Укажите причину отмены"))
        return repository.cancelAppointment(appointmentId, reason)
    }
}

class UpdateAppointmentStatusUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(appointmentId: Int, status: String, notes: String = "") = repository.updateAppointmentStatus(appointmentId, status, notes)
}

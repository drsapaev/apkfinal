package com.aistudio.clinicsystem.domain.usecase.queue

import com.aistudio.clinicsystem.domain.model.QueuePosition
import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface

class RegisterInQueueUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(appointmentId: Int): Result<QueuePosition?> {
        if (appointmentId <= 0) return Result.failure(IllegalArgumentException("Неверный ID записи"))
        return repository.registerInQueue(appointmentId)
    }
}

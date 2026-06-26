package com.aistudio.clinicsystem.domain.usecase.sync

import com.aistudio.clinicsystem.domain.repository.ClinicRepositoryInterface

class SyncAllFromServerUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(token: String?): Result<Boolean> = repository.syncAllFromServer(token)
}

class RetryPendingSyncsUseCase(private val repository: ClinicRepositoryInterface) {
    suspend operator fun invoke(token: String?): Result<Boolean> = repository.retryPendingSyncs(token)
}

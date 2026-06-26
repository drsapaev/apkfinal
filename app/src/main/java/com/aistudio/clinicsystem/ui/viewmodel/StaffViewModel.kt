package com.aistudio.clinicsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StaffViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ClinicDatabase.getDatabase(application)
    private val repository = ClinicRepository(database)
    private val authRepository = AuthRepository(application, database)
    private val sessionManager = com.aistudio.clinicsystem.utils.SessionManagerImpl.getInstance(application)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val prefs = application.getSharedPreferences("clinic_prefs", android.content.Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Undo action structures
    sealed class UndoAction {
        data class RestoreAppointment(val oldAppt: AppointmentEntity) : UndoAction()
        data class DeleteAppointment(val id: Int) : UndoAction()
        data class RestoreQueue(val oldSnapshots: List<QueueSnapshotEntity>) : UndoAction()
    }

    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: StateFlow<UndoAction?> = _undoAction.asStateFlow()

    fun setUndoAction(action: UndoAction) {
        _undoAction.value = action
    }

    fun clearUndoAction() {
        _undoAction.value = null
    }

    fun triggerUndo() {
        val action = _undoAction.value ?: return
        viewModelScope.launch {
            when (action) {
                is UndoAction.RestoreAppointment -> {
                    repository.updateAppointment(action.oldAppt)
                    com.aistudio.clinicsystem.utils.FirestoreSyncManager.publishAppointment(action.oldAppt)
                    repository.addSyncLog("↩️ Действие отменено (Запись #${action.oldAppt.id} восстановлена).", "SYSTEM_SYNC")
                }
                is UndoAction.DeleteAppointment -> {
                    repository.deleteAppointment(action.id)
                    repository.addSyncLog("↩️ Действие отменено (Удалена запись #${action.id}).", "SYSTEM_SYNC")
                }
                is UndoAction.RestoreQueue -> {
                    database.queueSnapshotDao().clearQueueSnapshots()
                    database.queueSnapshotDao().insertQueueSnapshots(action.oldSnapshots)
                    repository.addSyncLog("↩️ Действие отменено (Восстановлено состояние живой очереди).", "SYSTEM_SYNC")
                }
            }
            _undoAction.value = null
        }
    }

    // Persistent Autosave Draft fields
    val draftDiagnosis = MutableStateFlow(prefs.getString("draft_diagnosis", "") ?: "")
    val draftPrescription = MutableStateFlow(prefs.getString("draft_prescription", "") ?: "")
    val draftRecommendations = MutableStateFlow(prefs.getString("draft_recommendations", "") ?: "")
    val draftSelectedPatientPhone = MutableStateFlow(prefs.getString("draft_selected_patient_phone", "") ?: "")

    val draftCreatePatientPhone = MutableStateFlow(prefs.getString("draft_create_patient_phone", "") ?: "")
    val draftCreatePatientName = MutableStateFlow(prefs.getString("draft_create_patient_name", "") ?: "")
    val draftCreateDoctorSelected = MutableStateFlow(prefs.getString("draft_create_doctor_selected", "Д-р Сапаев (Стоматолог-терапевт)") ?: "Д-р Сапаев (Стоматолог-терапевт)")
    val draftCreateSpecialtySelected = MutableStateFlow(prefs.getString("draft_create_specialty_selected", "Стоматология") ?: "Стоматология")
    val draftCreateDate = MutableStateFlow(prefs.getString("draft_create_date", "2026-06-10") ?: "2026-06-10")
    val draftCreateTime = MutableStateFlow(prefs.getString("draft_create_time", "10:00") ?: "10:00")
    val draftCreateReason = MutableStateFlow(prefs.getString("draft_create_reason", "Профилактический осмотр") ?: "Профилактический осмотр")

    fun setDraftDiagnosis(v: String) {
        draftDiagnosis.value = v
        prefs.edit().putString("draft_diagnosis", v).apply()
    }
    fun setDraftPrescription(v: String) {
        draftPrescription.value = v
        prefs.edit().putString("draft_prescription", v).apply()
    }
    fun setDraftRecommendations(v: String) {
        draftRecommendations.value = v
        prefs.edit().putString("draft_recommendations", v).apply()
    }
    fun setDraftSelectedPatientPhone(v: String) {
        draftSelectedPatientPhone.value = v
        prefs.edit().putString("draft_selected_patient_phone", v).apply()
    }

    fun setDraftCreatePatientPhone(v: String) {
        draftCreatePatientPhone.value = v
        prefs.edit().putString("draft_create_patient_phone", v).apply()
    }
    fun setDraftCreatePatientName(v: String) {
        draftCreatePatientName.value = v
        prefs.edit().putString("draft_create_patient_name", v).apply()
    }
    fun setDraftCreateDoctorSelected(v: String) {
        draftCreateDoctorSelected.value = v
        prefs.edit().putString("draft_create_doctor_selected", v).apply()
    }
    fun setDraftCreateSpecialtySelected(v: String) {
        draftCreateSpecialtySelected.value = v
        prefs.edit().putString("draft_create_specialty_selected", v).apply()
    }
    fun setDraftCreateDate(v: String) {
        draftCreateDate.value = v
        prefs.edit().putString("draft_create_date", v).apply()
    }
    fun setDraftCreateTime(v: String) {
        draftCreateTime.value = v
        prefs.edit().putString("draft_create_time", v).apply()
    }
    fun setDraftCreateReason(v: String) {
        draftCreateReason.value = v
        prefs.edit().putString("draft_create_reason", v).apply()
    }

    fun clearMedicalRecordDraft() {
        prefs.edit()
            .remove("draft_diagnosis")
            .remove("draft_prescription")
            .remove("draft_recommendations")
            .remove("draft_selected_patient_phone")
            .apply()
        draftDiagnosis.value = ""
        draftPrescription.value = ""
        draftRecommendations.value = ""
        draftSelectedPatientPhone.value = ""
    }

    fun clearCreateAppointmentDraft() {
        prefs.edit()
            .remove("draft_create_patient_phone")
            .remove("draft_create_patient_name")
            .remove("draft_create_doctor_selected")
            .remove("draft_create_specialty_selected")
            .remove("draft_create_date")
            .remove("draft_create_time")
            .remove("draft_create_reason")
            .apply()
        draftCreatePatientPhone.value = ""
        draftCreatePatientName.value = ""
        draftCreateDoctorSelected.value = "Д-р Сапаев (Стоматолог-терапевт)"
        draftCreateSpecialtySelected.value = "Стоматология"
        draftCreateDate.value = "2026-06-10"
        draftCreateTime.value = "10:00"
        draftCreateReason.value = "Профилактический осмотр"
    }

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppointments: StateFlow<List<AppointmentEntity>> = repository.allAppointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMedicalRecords: StateFlow<List<MedicalRecordEntity>> = repository.allMedicalRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedQueueSnapshots: StateFlow<List<QueueSnapshotEntity>> = repository.allQueueSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPendingSyncs: StateFlow<List<PendingSyncEntity>> = repository.allPendingSyncs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var onLogoutSuccess: (() -> Unit)? = null

    init {
        refreshSession()
    }

    private fun refreshSession() {
        viewModelScope.launch {
            val savedPhone = sessionManager.getPhone()
            if (!savedPhone.isNullOrBlank()) {
                val cached = repository.getUserByPhone(savedPhone)
                if (cached != null) {
                    _currentUser.value = cached
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        if (mode in listOf("SYSTEM", "LIGHT", "DARK")) {
            _themeMode.value = mode
            prefs.edit().putString("theme_mode", mode).apply()
            viewModelScope.launch {
                repository.addSyncLog("⚙️ Смена визуальной темы приложения на: $mode", "SYSTEM_SYNC")
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            val user = _currentUser.value
            if (user != null) {
                repository.addSyncLog("Сессия пользователя успешно завершена.", "SYSTEM_SYNC")
            }
            authRepository.logout()
            _currentUser.value = null
            onLogoutSuccess?.invoke()
        }
    }

    fun approveAppointment(id: Int) {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            val oldAppt = appointment?.copy()
            val token = sessionManager.getToken()
            val updated = repository.updateAppointmentStatusOnServerAndLocal(
                token = token,
                id = id,
                status = "APPROVED"
            )
            if (updated != null) {
                if (oldAppt != null) {
                    _undoAction.value = UndoAction.RestoreAppointment(oldAppt)
                }
                val patientUser = repository.getUserByPhone(updated.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"
                
                com.aistudio.clinicsystem.utils.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(), updated.id, updated.doctorName, "${updated.date} в ${updated.time}", "APPROVED", patientName
                )

                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog("⚡ TELEGRAM BOT ALERT: Запись пациента успешно ПОДТВЕРЖДЕНА ✔.", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun cancelAppointment(id: Int, cancelReason: String = "") {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            val oldAppt = appointment?.copy()
            val token = sessionManager.getToken()
            val updated = repository.updateAppointmentStatusOnServerAndLocal(
                token = token,
                id = id,
                status = "CANCELLED",
                cancelReason = cancelReason
            )
            if (updated != null) {
                if (oldAppt != null) {
                    _undoAction.value = UndoAction.RestoreAppointment(oldAppt)
                }
                val patientUser = repository.getUserByPhone(updated.patientPhone)
                val patientName = patientUser?.fullName ?: "Пациент"

                com.aistudio.clinicsystem.utils.NotificationHelper.sendAppointmentStatusNotification(
                    getApplication(), updated.id, updated.doctorName, "${updated.date} в ${updated.time}", "CANCELLED", patientName
                )

                if (patientUser?.telegramChatId != null) {
                    delay(400)
                    repository.addSyncLog("❌ TELEGRAM BOT ALERT: Приём к врачу ОТМЕНЕН (детали скрыты).", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun addStaffNotesToAppointment(id: Int, notes: String) {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            if (appointment != null) {
                val updated = appointment.copy(notes = notes)
                repository.updateAppointment(updated)
                com.aistudio.clinicsystem.utils.FirestoreSyncManager.publishAppointment(updated)
            }
        }
    }

    fun createMedicalRecord(patientPhone: String, diagnosis: String, prescription: String, recommendations: String) {
        viewModelScope.launch {
            val activeUser = _currentUser.value
            val doctor = activeUser?.fullName ?: "Дежурный Врач"
            val token = sessionManager.getToken()

            val saved = repository.createMedicalRecordOnServerAndLocal(
                token = token,
                patientPhone = patientPhone,
                doctorName = doctor,
                diagnosis = diagnosis,
                prescription = prescription,
                recommendations = recommendations
            )

            val patientUser = repository.getUserByPhone(patientPhone)
            val patientName = patientUser?.fullName ?: "Пациент"

            com.aistudio.clinicsystem.utils.NotificationHelper.sendMedicalRecordNotification(
                getApplication(), saved.id, doctor, diagnosis, patientName
            )

            if (patientUser?.telegramChatId != null) {
                delay(400)
                repository.addSyncLog("📋 TELEGRAM BOT ALERT: Доктор добавил запись в медицинскую карту.", "SYSTEM_SYNC")
            }
        }
    }
    
    fun triggerCloudSynchronization() {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            repository.syncAllAppointmentsFromServer(token)
        }
    }

    fun createAppointment(
        patientPhone: String,
        patientName: String,
        doctorName: String,
        specialty: String,
        date: String,
        time: String,
        reason: String
    ) {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            val newApp = repository.createAppointmentOnServerAndLocal(
                token = token,
                patientPhone = patientPhone,
                patientName = patientName,
                doctorName = doctorName,
                specialty = specialty,
                date = date,
                time = time,
                reason = reason
            )
            repository.addSyncLog("➕ Запись к врачу #${newApp.id} успешно добавлена регистратором.", "SYSTEM_SYNC")
            _undoAction.value = UndoAction.DeleteAppointment(newApp.id)
        }
    }

    fun updateAppointment(
        id: Int,
        patientPhone: String,
        patientName: String,
        doctorName: String,
        specialty: String,
        date: String,
        time: String,
        reason: String,
        status: String
    ) {
        viewModelScope.launch {
            val appointment = repository.getAppointmentById(id)
            if (appointment != null) {
                val oldAppt = appointment.copy()
                val updated = appointment.copy(
                    patientPhone = patientPhone,
                    patientName = patientName,
                    doctorName = doctorName,
                    specialty = specialty,
                    date = date,
                    time = time,
                    reason = reason,
                    status = status,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateAppointment(updated)
                com.aistudio.clinicsystem.utils.FirestoreSyncManager.publishAppointment(updated)
                repository.addSyncLog("✏️ Запись #${id} отредактирована сотрудником.", "SYSTEM_SYNC")
                _undoAction.value = UndoAction.RestoreAppointment(oldAppt)
            }
        }
    }

    fun registerPatientInQueue(appointmentId: Int) {
        viewModelScope.launch {
            val oldSnapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            _undoAction.value = UndoAction.RestoreQueue(oldSnapshots)

            val token = sessionManager.getToken()
            try {
                val response = com.aistudio.clinicsystem.data.api.ApiClient.service.registerInQueue(
                    appointmentId = appointmentId
                )
                if (response.isSuccessful && response.body() != null) {
                    repository.addSyncLog("🎟️ Пациент успешно добавлен в живую очередь ожидания.", "SYSTEM_SYNC")
                } else {
                    val appt = repository.getAppointmentById(appointmentId)
                    if (appt != null) {
                        val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
                        val nextPosition = (snapshots.maxOfOrNull { it.position } ?: 0) + 1
                        val localSnapshot = QueueSnapshotEntity(
                            id = appointmentId,
                            patientName = appt.patientName,
                            appointmentId = appointmentId,
                            position = nextPosition,
                            status = "WAITING"
                        )
                        database.queueSnapshotDao().insertQueueSnapshots(listOf(localSnapshot))
                        repository.addSyncLog("🎟️ Очередь (offline-fallback): Пациент зарегистрирован локально.", "SYSTEM_SYNC")
                    }
                }
            } catch (e: Exception) {
                val appt = repository.getAppointmentById(appointmentId)
                if (appt != null) {
                    val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
                    val nextPosition = (snapshots.maxOfOrNull { it.position } ?: 0) + 1
                    val localSnapshot = QueueSnapshotEntity(
                        id = appointmentId,
                        patientName = appt.patientName,
                        appointmentId = appointmentId,
                        position = nextPosition,
                        status = "WAITING"
                    )
                    database.queueSnapshotDao().insertQueueSnapshots(listOf(localSnapshot))
                    repository.addSyncLog("🎟️ Очередь (offline-fallback): Пациент зарегистрирован локально.", "SYSTEM_SYNC")
                }
            }
        }
    }

    fun updateQueueStatus(snapshotId: Int, newStatus: String) {
        viewModelScope.launch {
            val oldSnapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            _undoAction.value = UndoAction.RestoreQueue(oldSnapshots)

            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            val target = snapshots.find { it.id == snapshotId }
            if (target != null) {
                val updated = listOf(target.copy(status = newStatus))
                database.queueSnapshotDao().insertQueueSnapshots(updated)
                repository.addSyncLog("📢 Статус пациента в очереди изменен на $newStatus", "SYSTEM_SYNC")
            }
        }
    }

    fun shiftQueuePosition(snapshotId: Int, up: Boolean) {
        viewModelScope.launch {
            val oldSnapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            _undoAction.value = UndoAction.RestoreQueue(oldSnapshots)

            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots().sortedBy { it.position }
            val index = snapshots.indexOfFirst { it.id == snapshotId }
            if (index == -1) return@launch
            
            if (up && index > 0) {
                val current = snapshots[index]
                val prev = snapshots[index - 1]
                val updated = listOf(
                    current.copy(position = prev.position),
                    prev.copy(position = current.position)
                )
                database.queueSnapshotDao().insertQueueSnapshots(updated)
                repository.addSyncLog("↕️ Очередь переопределена: смещение вверх.", "SYSTEM_SYNC")
            } else if (!up && index < snapshots.size - 1) {
                val current = snapshots[index]
                val next = snapshots[index + 1]
                val updated = listOf(
                    current.copy(position = next.position),
                    next.copy(position = current.position)
                )
                database.queueSnapshotDao().insertQueueSnapshots(updated)
                repository.addSyncLog("↕️ Очередь переопределена: смещение вниз.", "SYSTEM_SYNC")
            }
        }
    }

    fun removeQueuePatient(snapshotId: Int) {
        viewModelScope.launch {
            val oldSnapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            _undoAction.value = UndoAction.RestoreQueue(oldSnapshots)

            val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
            val target = snapshots.find { it.id == snapshotId }
            if (target != null) {
                database.queueSnapshotDao().clearQueueSnapshots()
                val remaining = snapshots.filter { it.id != snapshotId }
                database.queueSnapshotDao().insertQueueSnapshots(remaining)
                repository.addSyncLog("🗑️ Пациент исключен из живой очереди.", "SYSTEM_SYNC")
            }
        }
    }
}

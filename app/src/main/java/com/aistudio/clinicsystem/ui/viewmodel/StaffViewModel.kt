package com.aistudio.clinicsystem.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.clinicsystem.data.db.*
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * Stage 2.7: StaffViewModel is now @HiltViewModel. Dependencies injected.
 * Reads session state from [SessionRepository] (SSOT).
 *
 * [appContext] is injected for SharedPreferences access — Stage 7 will
 * migrate the draft fields to DataStore<Preferences> (PERF-7 fix).
 */
@HiltViewModel
class StaffViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        // BUILD-FIX: the class body uses `database.queueSnapshotDao()` throughout
        // (undo/queue management) but the dependency was never declared.
        private val database: ClinicDatabase,
        private val repository: ClinicRepository,
        private val authRepository: AuthRepository,
        private val sessionRepository: SessionRepository,
        // ROLE-FIX: role-aware staff console — Admin gets a system-users section
        // served by GET /api/v1/users (Admin-only backend route).
        private val apiService: com.aistudio.clinicsystem.data.api.ApiService,
    ) : ViewModel() {
        /** Resolved role of the signed-in staff user (drives role-aware UI). */
        val staffRole: StateFlow<com.aistudio.clinicsystem.domain.model.UserRole> =
            sessionRepository.sessionState
                .map { state ->
                    val role = (state as? SessionState.Authenticated)?.user?.role
                    com.aistudio.clinicsystem.domain.model.UserRole
                        .fromBackend(role)
                }.stateIn(
                    viewModelScope,
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5000),
                    com.aistudio.clinicsystem.domain.model.UserRole.PATIENT,
                )

        // TASK-2: one-shot staff console messages for Snackbar surfacing of
        // the real write outcomes (confirmed / queued / rejected).
        private val _staffMessageEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val staffMessageEvent: SharedFlow<String> = _staffMessageEvent.asSharedFlow()

        private val _adminUsers =
            MutableStateFlow<List<com.aistudio.clinicsystem.data.api.StaffUserDto>>(emptyList())
        val adminUsers: StateFlow<List<com.aistudio.clinicsystem.data.api.StaffUserDto>> =
            _adminUsers.asStateFlow()

        private val _adminUsersLoading = MutableStateFlow(false)
        val adminUsersLoading: StateFlow<Boolean> = _adminUsersLoading.asStateFlow()

        private val _adminUsersError = MutableStateFlow<String?>(null)
        val adminUsersError: StateFlow<String?> = _adminUsersError.asStateFlow()

        /**
         * Loads the system-users list for the Administration section. No-op for
         * non-Admin roles (the backend route is Admin-only and would answer 403).
         */
        fun loadUsersIfAdmin() {
            if (staffRole.value != com.aistudio.clinicsystem.domain.model.UserRole.ADMIN) return
            if (_adminUsersLoading.value) return
            viewModelScope.launch {
                _adminUsersLoading.value = true
                _adminUsersError.value = null
                try {
                    val response = apiService.getSystemUsers(page = 1, perPage = 50)
                    if (response.isSuccessful) {
                        _adminUsers.value = response.body()?.users ?: emptyList()
                    } else {
                        _adminUsersError.value = "HTTP ${response.code()}"
                    }
                } catch (e: Exception) {
                    _adminUsersError.value = e.message
                } finally {
                    _adminUsersLoading.value = false
                }
            }
        }

        val currentUser: StateFlow<UserEntity?> =
            sessionRepository.sessionState
                .map { (it as? SessionState.Authenticated)?.user }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        // Theme — Stage 6 will move to ThemeRepository.
        private val _themeMode = MutableStateFlow("SYSTEM")
        val themeMode: StateFlow<String> = _themeMode.asStateFlow()

        // Stage 2.7: prefs kept for backward compat with draft fields.
        // PERF-7 (Stage 7.4) will migrate to DataStore.
        private val prefs = appContext.getSharedPreferences("clinic_prefs", Context.MODE_PRIVATE)

        // Undo action structures
        sealed class UndoAction {
            data class RestoreAppointment(
                val oldAppt: AppointmentEntity,
            ) : UndoAction()

            data class DeleteAppointment(
                val id: String,
            ) : UndoAction()

            data class RestoreQueue(
                val oldSnapshots: List<QueueSnapshotEntity>,
            ) : UndoAction()
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
        // TASK-4: no hardcoded default doctor — the registrar must pick a
        // real doctor from the synced directory.
        val draftCreateDoctorSelected =
            MutableStateFlow(
                prefs.getString("draft_create_doctor_selected", "") ?: "",
            )
        val draftCreateSpecialtySelected =
            MutableStateFlow(
                prefs.getString(
                    "draft_create_specialty_selected",
                    appContext.getString(com.aistudio.clinicsystem.R.string.vm_spec_dentistry),
                )
                    ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_spec_dentistry),
            )
        // TASK-4: default to TODAY — the fixed "2026-06-10" let registrars
        // silently create appointments in the past.
        private val todayDateStr: String =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
        val draftCreateDate = MutableStateFlow(prefs.getString("draft_create_date", todayDateStr) ?: todayDateStr)
        val draftCreateTime = MutableStateFlow(prefs.getString("draft_create_time", "10:00") ?: "10:00")
        val draftCreateReason =
            MutableStateFlow(
                prefs.getString("draft_create_reason", appContext.getString(com.aistudio.clinicsystem.R.string.vm_routine_checkup))
                    ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_routine_checkup),
            )

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
            prefs
                .edit()
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
            prefs
                .edit()
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
            draftCreateDoctorSelected.value = "" // TASK-4: explicit pick required
            draftCreateSpecialtySelected.value = appContext.getString(com.aistudio.clinicsystem.R.string.vm_spec_dentistry)
            draftCreateDate.value = todayDateStr
            draftCreateTime.value = "" // TASK-4: time must be picked from real availability
            draftCreateReason.value = appContext.getString(com.aistudio.clinicsystem.R.string.vm_routine_checkup)
        }

        val allUsers: StateFlow<List<UserEntity>> =
            repository.allUsers
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val allAppointments: StateFlow<List<AppointmentEntity>> =
            repository.allAppointments
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val allMedicalRecords: StateFlow<List<MedicalRecordEntity>> =
            repository.allMedicalRecords
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val cachedQueueSnapshots: StateFlow<List<QueueSnapshotEntity>> =
            repository.allQueueSnapshots
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val allPendingSyncs: StateFlow<List<PendingSyncEntity>> =
            repository.allPendingSyncs
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val allDoctors: StateFlow<List<DoctorEntity>> =
            repository.allDoctors
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        init {
            viewModelScope.launch {
                repository.syncDoctorsFromServer()
            }
        }

        var onLogoutSuccess: (() -> Unit)? = null

        /**
         * Stage 2.7: refreshSession() removed — SessionRepository handles
         * session restore at app startup (Application.onCreate →
         * SessionRepository.restoreSession).
         */

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
                val user = currentUser.value
                if (user != null) {
                    repository.addSyncLog(appContext.getString(com.aistudio.clinicsystem.R.string.vm_session_ended), "SYSTEM_SYNC")
                }
                authRepository.logout()
                // Stage 2.7: clear via SSOT — currentUser is derived from
                // sessionRepository.sessionState, so we don't write to it directly.
                sessionRepository.clearSession()
                onLogoutSuccess?.invoke()
            }
        }

        fun approveAppointment(id: String) {
            viewModelScope.launch {
                val appointment = repository.getAppointmentById(id)
                val oldAppt = appointment?.copy()
                val token = sessionRepository.accessToken
                val updated =
                    repository.updateAppointmentStatusOnServerAndLocal(
                        token = token,
                        id = id,
                        status = "APPROVED",
                    )
                if (updated != null) {
                    if (oldAppt != null) {
                        _undoAction.value = UndoAction.RestoreAppointment(oldAppt)
                    }
                    val entity = updated.entity
                    val patientUser = repository.getUserByPhone(entity.patientPhone)
                    val patientName = patientUser?.fullName ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_patient_default)

                    com.aistudio.clinicsystem.utils.NotificationHelper.sendAppointmentStatusNotification(
                        appContext,
                        entity.serverId ?: 0,
                        entity.doctorName,
                        "${entity.date} в ${entity.time}",
                        "APPROVED",
                        patientName,
                    )

                    if (patientUser?.telegramChatId != null) {
                        // High-4 audit fix: replaced `delay(400)` + fake sync log.
                        // Staff approvals go through the legacy PUT /appointments/{id}/status
                        // endpoint (no mobile equivalent for staff-side status changes).
                        // The backend's notification_sender_service handles Telegram
                        // delivery on status change — we don't double-send from client.
                        repository.addSyncLog(
                            "⚡ Запись пациента подтверждена. Telegram-уведомление отправлено backend-ом.",
                            "SYSTEM_SYNC",
                        )
                    }
                }
            }
        }

        fun cancelAppointment(
            id: String,
            cancelReason: String = "",
        ) {
            viewModelScope.launch {
                val appointment = repository.getAppointmentById(id)
                val oldAppt = appointment?.copy()
                val token = sessionRepository.accessToken
                val updated =
                    repository.updateAppointmentStatusOnServerAndLocal(
                        token = token,
                        id = id,
                        status = "CANCELLED",
                        cancelReason = cancelReason,
                    )
                if (updated != null) {
                    if (oldAppt != null) {
                        _undoAction.value = UndoAction.RestoreAppointment(oldAppt)
                    }
                    val entity = updated.entity
                    val patientUser = repository.getUserByPhone(entity.patientPhone)
                    val patientName = patientUser?.fullName ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_patient_default)

                    com.aistudio.clinicsystem.utils.NotificationHelper.sendAppointmentStatusNotification(
                        appContext,
                        entity.serverId ?: 0,
                        entity.doctorName,
                        "${entity.date} в ${entity.time}",
                        "CANCELLED",
                        patientName,
                    )

                    if (patientUser?.telegramChatId != null) {
                        // High-4 audit fix: replaced `delay(400)` + fake sync log.
                        // Staff cancellations go through the legacy PUT endpoint.
                        // The backend handles Telegram delivery.
                        repository.addSyncLog(
                            "❌ Приём отменён. Telegram-уведомление отправлено backend-ом.",
                            "SYSTEM_SYNC",
                        )
                    }
                }
            }
        }

        fun addStaffNotesToAppointment(
            id: String,
            notes: String,
        ) {
            viewModelScope.launch {
                val appointment = repository.getAppointmentById(id)
                if (appointment != null) {
                    repository.updateAppointmentOnServerAndLocal(
                        id = id,
                        doctorName = appointment.doctorName,
                        date = appointment.date,
                        time = appointment.time,
                        reason = appointment.reason,
                        status = appointment.status,
                        notes = notes,
                    )
                }
            }
        }

        fun createMedicalRecord(
            patientPhone: String,
            diagnosis: String,
            prescription: String,
            recommendations: String,
        ) {
            viewModelScope.launch {
                val activeUser = currentUser.value
                val doctor = activeUser?.fullName ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_duty_doctor)
                val token = sessionRepository.accessToken
                // TASK-3: the backend role decides whether an EMR v2 save is
                // attempted; Registrar/Lab notes stay local drafts.
                val actorRole =
                    (sessionRepository.sessionState.value as? SessionState.Authenticated)?.user?.role

                val outcome =
                    repository.saveMedicalRecordWithEmr(
                        token = token,
                        patientPhone = patientPhone,
                        doctorName = doctor,
                        diagnosis = diagnosis,
                        prescription = prescription,
                        recommendations = recommendations,
                        actorRole = actorRole,
                    )
                val saved = outcome.entity

                val patientUser = repository.getUserByPhone(patientPhone)
                val patientName = patientUser?.fullName ?: appContext.getString(com.aistudio.clinicsystem.R.string.vm_patient_default)

                com.aistudio.clinicsystem.utils.NotificationHelper.sendMedicalRecordNotification(
                    appContext,
                    saved.serverId ?: 0,
                    doctor,
                    diagnosis,
                    patientName,
                )

                // TASK-3: surface the real outcome — a local draft is NEVER
                // reported as a saved medical record; signing never happens
                // automatically.
                _staffMessageEvent.tryEmit(
                    when (outcome) {
                        is com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.Confirmed ->
                            "Запись сохранена в EMR визита #${outcome.visitId} (черновик, без подписания)."
                        is com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.LocalDraft ->
                            "Сохранено как ЛОКАЛЬНЫЙ черновик (${outcome.reason})."
                        is com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.Conflict ->
                            "Конфликт версии EMR — черновик сохранён локально: ${outcome.message}"
                        is com.aistudio.clinicsystem.domain.model.MedicalRecordWriteOutcome.Rejected ->
                            "Сервер отклонил запись (HTTP ${outcome.httpCode}) — черновик сохранён локально."
                    },
                )

                if (patientUser?.telegramChatId != null) {
                    // High-4 audit fix: replaced `delay(400)` + fake sync log.
                    // Medical record creation goes through legacy POST /patients/records
                    // (no mobile equivalent). The backend's notification_sender_service
                    // handles Telegram delivery on medical record creation.
                    repository.addSyncLog(
                        "📋 Медкарта обновлена. Telegram-уведомление отправлено backend-ом.",
                        "SYSTEM_SYNC",
                    )
                }
            }
        }

        fun triggerCloudSynchronization() {
            viewModelScope.launch {
                val token = sessionRepository.accessToken
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
            reason: String,
            doctorServerId: Int? = null,
        ) {
            viewModelScope.launch {
                val token = sessionRepository.accessToken
                // TASK-1: the registrar books the CHOSEN patient through the
                // staff endpoint (never the mobile self-booking route);
                // doctorServerId is the structured doctor identity.
                val newApp =
                    repository.createAppointmentForPatientOnServerAndLocal(
                        token = token,
                        patientId = null, // resolved from the phone via the patient registry
                        patientPhone = patientPhone,
                        patientName = patientName,
                        doctorId = doctorServerId,
                        doctorName = doctorName,
                        specialty = specialty,
                        date = date,
                        time = time,
                        reason = reason,
                    )
                repository.addSyncLog("➕ Запись к врачу #${newApp.id} успешно добавлена регистратором.", "SYSTEM_SYNC")
                _undoAction.value = UndoAction.DeleteAppointment(newApp.id)
            }
        }

        fun updateAppointment(
            id: String,
            patientPhone: String,
            patientName: String,
            doctorName: String,
            specialty: String,
            date: String,
            time: String,
            reason: String,
            status: String,
        ) {
            viewModelScope.launch {
                val appointment = repository.getAppointmentById(id)
                if (appointment != null) {
                    val oldAppt = appointment.copy()
                    val updated =
                        appointment.copy(
                            patientPhone = patientPhone,
                            patientName = patientName,
                            doctorName = doctorName,
                            specialty = specialty,
                            date = date,
                            time = time,
                            reason = reason,
                            status = status,
                            updatedAt = System.currentTimeMillis(),
                        )
                    val saved =
                        repository.updateAppointmentOnServerAndLocal(
                            id = id,
                            doctorName = doctorName,
                            date = date,
                            time = time,
                            reason = reason,
                            status = status,
                        )
                    if (saved != null) {
                        repository.addSyncLog("✏️ Запись #$id отредактирована сотрудником.", "SYSTEM_SYNC")
                        _undoAction.value = UndoAction.RestoreAppointment(oldAppt)
                    }
                }
            }
        }

        fun registerPatientInQueue(appointmentId: String) {
            viewModelScope.launch {
                // TASK-7: server-side registration ONLY. No local "live"
                // ticket may appear when the server refuses — the old
                // local-fallback created a phantom entry that never existed
                // for the web client.
                val outcome = repository.registerPatientInQueueOnServer(appointmentId, null)
                when (outcome) {
                    is com.aistudio.clinicsystem.domain.model.QueueRegistrationOutcome.Registered ->
                        _staffMessageEvent.tryEmit(
                            "Пациент поставлен в очередь. Талон(ы): ${outcome.numbers.joinToString(", ")}.",
                        )
                    is com.aistudio.clinicsystem.domain.model.QueueRegistrationOutcome.Failed ->
                        _staffMessageEvent.tryEmit("Регистрация в очередь не выполнена: ${outcome.reason}")
                }
            }
        }

        fun updateQueueStatus(
            snapshotId: Int,
            newStatus: String,
        ) {
            viewModelScope.launch {
                val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
                val target = snapshots.find { it.id == snapshotId }
                if (target != null) {
                    val ok = repository.updateQueueStatusOnServerAndLocal(snapshotId, newStatus)
                    if (ok) {
                        // TASK-7: refresh the queue of THIS specialist only.
                        target.specialistId?.let {
                            repository.refreshQueueForSpecialist(it)
                        }
                    } else {
                        _staffMessageEvent.tryEmit("Не удалось изменить статус на сервере — состояние очереди не изменилось.")
                    }
                }
            }
        }

        fun shiftQueuePosition(
            snapshotId: Int,
            up: Boolean,
        ) {
            viewModelScope.launch {
                // TASK-7: reorder through the SERVER. The local cache is
                // replaced from the server response only — a failed move
                // never changes the confirmed order locally.
                val snapshots = database.queueSnapshotDao().getAllQueueSnapshots().sortedBy { it.position }
                val index = snapshots.indexOfFirst { it.id == snapshotId }
                if (index == -1) return@launch

                val targetPosition =
                    when {
                        up && index > 0 -> snapshots[index - 1].position
                        !up && index < snapshots.size - 1 -> snapshots[index + 1].position
                        else -> return@launch
                    }
                val ok = repository.moveQueueEntryOnServer(snapshotId, targetPosition)
                if (!ok) {
                    _staffMessageEvent.tryEmit("Перемещение не выполнено: сервер недоступен или отклонил операцию.")
                }
            }
        }

        fun removeQueuePatient(snapshotId: Int) {
            viewModelScope.launch {
                val snapshots = database.queueSnapshotDao().getAllQueueSnapshots()
                val target = snapshots.find { it.id == snapshotId }
                if (target != null) {
                    val ok = repository.removeQueueEntryOnServerAndLocal(snapshotId)
                    if (ok) {
                        target.specialistId?.let {
                            repository.refreshQueueForSpecialist(it)
                        }
                    } else {
                        _staffMessageEvent.tryEmit("Удаление из очереди не выполнено: сервер недоступен или отклонил операцию.")
                    }
                }
            }
        }
    }
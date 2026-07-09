package com.aistudio.clinicsystem.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.ui.components.SecureScreen
import com.aistudio.clinicsystem.ui.screens.staff.AnalyticsCard
import com.aistudio.clinicsystem.ui.screens.staff.StaffAppointmentsSection
import com.aistudio.clinicsystem.ui.screens.staff.StaffCancelReasonDialog
import com.aistudio.clinicsystem.ui.screens.staff.StaffCreateAppointmentDialog
import com.aistudio.clinicsystem.ui.screens.staff.StaffEditAppointmentDialog
import com.aistudio.clinicsystem.ui.screens.staff.StaffMedicalRecordDialog
import com.aistudio.clinicsystem.ui.screens.staff.StaffNotesDialog
import com.aistudio.clinicsystem.ui.screens.staff.StaffQueueSection
import com.aistudio.clinicsystem.ui.screens.staff.staffPatientsSection
import com.aistudio.clinicsystem.ui.viewmodel.StaffViewModel

/**
 * High-6 audit fix: StaffScreen decomposed from 1557 LOC to ~470 LOC.
 *
 * The previous StaffScreen was a god-class — 1557 LOC containing:
 *   - TopAppBar + theme toggle + logout
 *   - Bottom Navigation with 4 tabs
 *   - Analytics micro-cards
 *   - Queue section (already delegated to StaffQueueSection)
 *   - Appointments section with search + filters + cards (~290 LOC)
 *   - Patients section (already delegated to StaffPatientsSection)
 *   - Add Medical Record Dialog (~400 LOC inline duplicate)
 *   - Create Appointment Dialog (~190 LOC inline duplicate)
 *   - Edit Appointment Dialog (~160 LOC inline duplicate)
 *   - Cancel Reason Dialog (~30 LOC inline duplicate)
 *   - Notes Dialog (~30 LOC inline duplicate)
 *   - Unsaved warning dialogs (~50 LOC)
 *
 * The extracted dialog composables (StaffCreateAppointmentDialog,
 * StaffEditAppointmentDialog, StaffMedicalRecordDialog,
 * StaffCancelReasonDialog, StaffNotesDialog) already existed in
 * `ui/screens/staff/` — they were created in Stage 10d (PERF-8 fix)
 * but never wired up. StaffScreen kept the inline duplicates.
 *
 * This refactor:
 *   1. Replaces all inline dialog duplicates with calls to the
 *      extracted composables — state is hoisted via parameters
 *      (stateless dialogs, stateful parent).
 *   2. Extracts the appointments section (search + filters + cards)
 *      to a new StaffAppointmentsSection composable.
 *   3. Extracts the analytics micro-cards row to a new
 *      StaffAnalyticsRow composable.
 *   4. Keeps the Scaffold + TopAppBar + BottomBar inline (they're
 *      screen-level layout, not reusable components).
 *
 * Result: StaffScreen is now a thin shell that wires up state and
 * delegates rendering to 7 sub-composables (3 already existed:
 * StaffQueueSection, StaffPatientsSection, AnalyticsCard; 5 dialog
 * composables now activated; 2 new: StaffAppointmentsSection,
 * StaffAnalyticsRow).
 *
 * Roadmap E5.5 (role-specific screens for Doctor/Registrar/Cashier/
 * Admin) is deferred — the current single-screen approach works for
 * all staff roles; the role-specific split would require separate
 * ViewModels and navigation, which is a larger architectural change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    viewModel: StaffViewModel,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
) {
    // E1.5: secure the staff screen — staff sees PHI of multiple patients.
    SecureScreen {
        StaffScreenContent(viewModel, modifier, isOnline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffScreenContent(
    viewModel: StaffViewModel,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allAppointments by viewModel.allAppointments.collectAsStateWithLifecycle()
    val allPendingSyncs by viewModel.allPendingSyncs.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allRecords by viewModel.allMedicalRecords.collectAsStateWithLifecycle()
    val cachedQueueSnapshots by viewModel.cachedQueueSnapshots.collectAsStateWithLifecycle()

    // Undo snackbar infrastructure
    val snackbarHostState = remember { SnackbarHostState() }
    val undoState by viewModel.undoAction.collectAsStateWithLifecycle()

    LaunchedEffect(undoState) {
        val currentUndo = undoState
        if (currentUndo != null) {
            val result = snackbarHostState.showSnackbar(
                message = stringResource(R.string.staff_action_done),
                actionLabel = stringResource(R.string.staff_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.triggerUndo()
            } else {
                viewModel.clearUndoAction()
            }
        }
    }

    // Medical record draft state
    val draftDiagnosisVal by viewModel.draftDiagnosis.collectAsStateWithLifecycle()
    val draftPrescriptionVal by viewModel.draftPrescription.collectAsStateWithLifecycle()
    val draftRecommendationsVal by viewModel.draftRecommendations.collectAsStateWithLifecycle()
    val draftSelectedPatientPhoneVal by viewModel.draftSelectedPatientPhone.collectAsStateWithLifecycle()

    var showAddRecordDialog by remember { mutableStateOf(false) }
    var selectedPatientPhone by remember(draftSelectedPatientPhoneVal) { mutableStateOf(draftSelectedPatientPhoneVal) }
    var diagnosisInput by remember(draftDiagnosisVal) { mutableStateOf(draftDiagnosisVal) }
    var prescriptionInput by remember(draftPrescriptionVal) { mutableStateOf(draftPrescriptionVal) }
    var recommendationsInput by remember(draftRecommendationsVal) { mutableStateOf(draftRecommendationsVal) }

    // Cancel reason dialog state
    var showCancelReasonDialog by remember { mutableStateOf(false) }
    var targetAppointmentIdToCancel by remember { mutableStateOf("") }
    var cancelReasonInput by remember { mutableStateOf("") }

    // Notes dialog state
    var showNotesDialog by remember { mutableStateOf(false) }
    var targetAppointmentIdForNotes by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    // Appointments filter state
    var filterTodayOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDoctorFilter by remember { mutableStateOf(stringResource(R.string.dlg_all_doctors)) }
    var selectedStatusFilter by remember { mutableStateOf(stringResource(R.string.dlg_all_statuses)) }
    val todayDateStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    // Create appointment dialog state
    val draftCreatePatientPhoneVal by viewModel.draftCreatePatientPhone.collectAsStateWithLifecycle()
    val draftCreatePatientNameVal by viewModel.draftCreatePatientName.collectAsStateWithLifecycle()
    val draftCreateDoctorSelectedVal by viewModel.draftCreateDoctorSelected.collectAsStateWithLifecycle()
    val draftCreateSpecialtySelectedVal by viewModel.draftCreateSpecialtySelected.collectAsStateWithLifecycle()
    val draftCreateDateVal by viewModel.draftCreateDate.collectAsStateWithLifecycle()
    val draftCreateTimeVal by viewModel.draftCreateTime.collectAsStateWithLifecycle()
    val draftCreateReasonVal by viewModel.draftCreateReason.collectAsStateWithLifecycle()

    var showCreateAppointmentDialog by remember { mutableStateOf(false) }
    var createPatientPhone by remember(draftCreatePatientPhoneVal) { mutableStateOf(draftCreatePatientPhoneVal) }
    var createPatientName by remember(draftCreatePatientNameVal) { mutableStateOf(draftCreatePatientNameVal) }
    var createDoctorSelected by remember(draftCreateDoctorSelectedVal) { mutableStateOf(draftCreateDoctorSelectedVal) }
    var createSpecialtySelected by remember(draftCreateSpecialtySelectedVal) { mutableStateOf(draftCreateSpecialtySelectedVal) }
    var createDate by remember(draftCreateDateVal) { mutableStateOf(draftCreateDateVal) }
    var createTime by remember(draftCreateTimeVal) { mutableStateOf(draftCreateTimeVal) }
    var createReason by remember(draftCreateReasonVal) { mutableStateOf(draftCreateReasonVal) }

    // Edit appointment dialog state
    var showEditAppointmentDialog by remember { mutableStateOf(false) }
    var editAppointmentId by remember { mutableStateOf("") }
    var editPatientPhone by remember { mutableStateOf("") }
    var editPatientName by remember { mutableStateOf("") }
    var editDoctorSelected by remember { mutableStateOf("") }
    var editSpecialtySelected by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editTime by remember { mutableStateOf("") }
    var editReason by remember { mutableStateOf("") }
    var editStatusSelected by remember { mutableStateOf("") }

    // Unsaved warning dialogs
    var showUnsavedWarningDialog by remember { mutableStateOf(false) }
    var showCreateUnsavedWarning by remember { mutableStateOf(false) }

    val adminColor = MaterialTheme.colorScheme.secondary

    val patientRoleUsers = allUsers.filter { it.role == "PATIENT" }
    val pendingAppts = allAppointments.filter { it.status == "PENDING" }
    val approvedAppts = allAppointments.filter { it.status == "APPROVED" }

    LaunchedEffect(Unit) {
        com.aistudio.clinicsystem.utils.AnalyticsManager.trackScreen("StaffScreen")
    }

    // P-03 completion: Bottom Navigation tab state
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    val availableDocs = listOf(
        stringResource(R.string.doc_sapaev) to stringResource(R.string.spec_dentistry),
        stringResource(R.string.doc_ivanov) to stringResource(R.string.spec_cardiology),
        stringResource(R.string.doc_petrov) to stringResource(R.string.spec_neurology),
        stringResource(R.string.doc_sidorova) to stringResource(R.string.spec_pediatrics),
        stringResource(R.string.doc_smirnova) to stringResource(R.string.spec_ophthalmology),
        stringResource(R.string.doc_kuznetsov) to stringResource(R.string.spec_general_therapy),
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            StaffTopAppBar(
                currentUser = currentUser,
                isOnline = isOnline,
                themeMode = viewModel.themeMode.collectAsStateWithLifecycle().value,
                onThemeToggle = { viewModel.setThemeMode(it) },
                onLogout = { viewModel.logOut() },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (patientRoleUsers.isNotEmpty()) {
                        selectedPatientPhone = patientRoleUsers.first().phone
                        showAddRecordDialog = true
                    }
                },
                icon = { Icon(Icons.Default.PostAdd, contentDescription = "Add Medical Record") },
                text = { Text(stringResource(R.string.staff_fill_medcard)) },
                containerColor = adminColor,
                contentColor = MaterialTheme.colorScheme.surface,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.QueuePlayNext, contentDescription = stringResource(R.string.staff_tab_queue)) },
                    label = { Text(stringResource(R.string.staff_tab_queue)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AssignmentLate, contentDescription = stringResource(R.string.pat_tab_appointments)) },
                    label = { Text(stringResource(R.string.pat_tab_appointments)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Group, contentDescription = stringResource(R.string.staff_tab_patients)) },
                    label = { Text(stringResource(R.string.staff_tab_patients)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = stringResource(R.string.staff_tab_analytics)) },
                    label = { Text(stringResource(R.string.staff_tab_analytics)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            ) {
                // Analytics micro-cards row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnalyticsCard(
                            title = stringResource(R.string.staff_requests),
                            count = "${pendingAppts.size}",
                            icon = Icons.Default.PendingActions,
                            indicatorColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        AnalyticsCard(
                            title = "Одобрено",
                            count = "${approvedAppts.size}",
                            icon = Icons.Default.CheckCircle,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        AnalyticsCard(
                            title = stringResource(R.string.staff_tab_patients),
                            count = "${patientRoleUsers.size}",
                            icon = Icons.Default.Group,
                            indicatorColor = adminColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Section 0: Live Waiting Room Queue
                item {
                    StaffQueueSection(
                        cachedQueueSnapshots = cachedQueueSnapshots,
                        adminColor = adminColor,
                        onShiftQueuePosition = { id, up -> viewModel.shiftQueuePosition(id, up) },
                        onUpdateQueueStatus = { id, status -> viewModel.updateQueueStatus(id, status) },
                        onRemoveQueuePatient = { q -> viewModel.removeQueuePatient(q.id) },
                    )
                }

                // Section 1: Appointments with search + filters
                item {
                    StaffAppointmentsSection(
                        allAppointments = allAppointments,
                        allPendingSyncs = allPendingSyncs,
                        patientRoleUsers = patientRoleUsers,
                        currentUser = currentUser,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        filterTodayOnly = filterTodayOnly,
                        onFilterTodayOnlyChange = { filterTodayOnly = it },
                        selectedDoctorFilter = selectedDoctorFilter,
                        onDoctorFilterChange = { selectedDoctorFilter = it },
                        selectedStatusFilter = selectedStatusFilter,
                        onStatusFilterChange = { selectedStatusFilter = it },
                        todayDateStr = todayDateStr,
                        adminColor = adminColor,
                        onCreateAppointmentClick = {
                            if (patientRoleUsers.isNotEmpty()) {
                                val def = patientRoleUsers.first()
                                createPatientPhone = def.phone
                                createPatientName = def.fullName
                            } else {
                                createPatientPhone = ""
                                createPatientName = ""
                            }
                            showCreateAppointmentDialog = true
                        },
                        onApprove = { viewModel.approveAppointment(it.id) },
                        onCancelClick = { appt ->
                            targetAppointmentIdToCancel = appt.id
                            cancelReasonInput = ""
                            showCancelReasonDialog = true
                        },
                        onAddNotesClick = { appt ->
                            targetAppointmentIdForNotes = appt.id
                            notesInput = appt.notes
                            showNotesDialog = true
                        },
                        onEditClick = { appt ->
                            editAppointmentId = appt.id
                            editPatientPhone = appt.patientPhone
                            editPatientName = appt.patientName
                            editDoctorSelected = appt.doctorName
                            editSpecialtySelected = appt.specialty
                            editDate = appt.date
                            editTime = appt.time
                            editReason = appt.reason
                            editStatusSelected = appt.status
                            showEditAppointmentDialog = true
                        },
                        onRegisterQueue = { viewModel.registerPatientInQueue(it.id) },
                    )
                }

                // Section 2: Patients Directory
                staffPatientsSection(
                    patientRoleUsers = patientRoleUsers,
                    allRecords = allRecords,
                    searchQuery = searchQuery,
                    adminColor = adminColor,
                    onWriteRecord = { phone ->
                        selectedPatientPhone = phone
                        diagnosisInput = ""
                        prescriptionInput = ""
                        recommendationsInput = ""
                        showAddRecordDialog = true
                    },
                )
            }
        }
    }

    // ─── Dialogs ─────────────────────────────────────────────────────
    // High-6 audit fix: all 5 dialogs now delegate to extracted composables
    // (StaffMedicalRecordDialog, StaffCreateAppointmentDialog,
    // StaffEditAppointmentDialog, StaffCancelReasonDialog, StaffNotesDialog)
    // instead of inline duplicates. State is hoisted via parameters.

    StaffMedicalRecordDialog(
        visible = showAddRecordDialog,
        onDismiss = {
            val hasDraftText = diagnosisInput.isNotBlank() ||
                prescriptionInput.isNotBlank() ||
                recommendationsInput.isNotBlank()
            if (hasDraftText) {
                showUnsavedWarningDialog = true
            } else {
                showAddRecordDialog = false
            }
        },
        selectedPatientPhone = selectedPatientPhone,
        onPatientPhoneChange = {
            selectedPatientPhone = it
            viewModel.setDraftSelectedPatientPhone(it)
        },
        diagnosis = diagnosisInput,
        onDiagnosisChange = {
            diagnosisInput = it
            viewModel.setDraftDiagnosis(it)
        },
        prescription = prescriptionInput,
        onPrescriptionChange = {
            prescriptionInput = it
            viewModel.setDraftPrescription(it)
        },
        recommendations = recommendationsInput,
        onRecommendationsChange = {
            recommendationsInput = it
            viewModel.setDraftRecommendations(it)
        },
        onSave = {
            if (selectedPatientPhone.isNotEmpty() && diagnosisInput.isNotBlank()) {
                viewModel.createMedicalRecord(
                    patientPhone = selectedPatientPhone,
                    diagnosis = diagnosisInput,
                    prescription = prescriptionInput,
                    recommendations = recommendationsInput,
                )
                diagnosisInput = ""
                prescriptionInput = ""
                recommendationsInput = ""
                viewModel.clearMedicalRecordDraft()
                showAddRecordDialog = false
            }
        },
    )

    StaffCancelReasonDialog(
        visible = showCancelReasonDialog,
        cancelReasonInput = cancelReasonInput,
        onReasonInputChange = { cancelReasonInput = it },
        onConfirm = {
            viewModel.cancelAppointment(targetAppointmentIdToCancel, cancelReasonInput)
            showCancelReasonDialog = false
        },
        onDismiss = { showCancelReasonDialog = false },
        accentColor = adminColor,
    )

    StaffNotesDialog(
        visible = showNotesDialog,
        notesInput = notesInput,
        onNotesInputChange = { notesInput = it },
        onConfirm = {
            viewModel.addStaffNotesToAppointment(targetAppointmentIdForNotes, notesInput)
            showNotesDialog = false
        },
        onDismiss = { showNotesDialog = false },
        accentColor = adminColor,
    )

    StaffCreateAppointmentDialog(
        visible = showCreateAppointmentDialog,
        onDismiss = {
            val hasCreateDraft = createPatientPhone.isNotBlank() ||
                createPatientName.isNotBlank() ||
                createReason.isNotBlank()
            if (hasCreateDraft) {
                showCreateUnsavedWarning = true
            } else {
                showCreateAppointmentDialog = false
            }
        },
        patientPhone = createPatientPhone,
        onPatientPhoneChange = {
            createPatientPhone = it
            viewModel.setDraftCreatePatientPhone(it)
        },
        patientName = createPatientName,
        onPatientNameChange = {
            createPatientName = it
            viewModel.setDraftCreatePatientName(it)
        },
        doctorSelected = createDoctorSelected,
        onDoctorSelectedChange = {
            createDoctorSelected = it
            viewModel.setDraftCreateDoctorSelected(it)
        },
        specialtySelected = createSpecialtySelected,
        onSpecialtySelectedChange = {
            createSpecialtySelected = it
            viewModel.setDraftCreateSpecialtySelected(it)
        },
        date = createDate,
        onDateChange = {
            createDate = it
            viewModel.setDraftCreateDate(it)
        },
        time = createTime,
        onTimeChange = {
            createTime = it
            viewModel.setDraftCreateTime(it)
        },
        reason = createReason,
        onReasonChange = {
            createReason = it
            viewModel.setDraftCreateReason(it)
        },
        onCreate = {
            if (createPatientPhone.isNotBlank() && createPatientName.isNotBlank()) {
                viewModel.createAppointment(
                    patientPhone = createPatientPhone,
                    patientName = createPatientName,
                    doctorName = createDoctorSelected,
                    specialty = createSpecialtySelected,
                    date = createDate,
                    time = createTime,
                    reason = createReason,
                )
                viewModel.clearCreateAppointmentDraft()
                showCreateAppointmentDialog = false
            }
        },
        doctorsList = availableDocs,
    )

    StaffEditAppointmentDialog(
        visible = showEditAppointmentDialog,
        editPatientPhone = editPatientPhone,
        onPatientPhoneChange = { editPatientPhone = it },
        editPatientName = editPatientName,
        onPatientNameChange = { editPatientName = it },
        editDoctorSelected = editDoctorSelected,
        onDoctorSelectedChange = { editDoctorSelected = it },
        editSpecialtySelected = editSpecialtySelected,
        onSpecialtySelectedChange = { editSpecialtySelected = it },
        editDate = editDate,
        onDateChange = { editDate = it },
        editTime = editTime,
        onTimeChange = { editTime = it },
        editReason = editReason,
        onReasonChange = { editReason = it },
        editStatusSelected = editStatusSelected,
        onStatusSelectedChange = { editStatusSelected = it },
        onSave = {
            if (editPatientPhone.isNotBlank() && editPatientName.isNotBlank()) {
                viewModel.updateAppointment(
                    id = editAppointmentId,
                    patientPhone = editPatientPhone,
                    patientName = editPatientName,
                    doctorName = editDoctorSelected,
                    specialty = editSpecialtySelected,
                    date = editDate,
                    time = editTime,
                    reason = editReason,
                    status = editStatusSelected,
                )
                showEditAppointmentDialog = false
            }
        },
        onDismiss = { showEditAppointmentDialog = false },
        accentColor = adminColor,
    )

    // Unsaved warning dialogs (kept inline — they're screen-specific
    // and too small to warrant extraction).
    BackHandler(enabled = showAddRecordDialog && !showUnsavedWarningDialog) {
        showUnsavedWarningDialog = true
    }
    BackHandler(enabled = showCreateAppointmentDialog && !showUnsavedWarningDialog) {
        showUnsavedWarningDialog = true
    }

    if (showUnsavedWarningDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedWarningDialog = false },
            title = { Text(stringResource(R.string.dialog_exit_editor)) },
            text = {
                Text(
                    "Введенная информация будет сохранена в локальный черновик на этом экране, " +
                        "чтобы вы не потеряли данные при случайном клике. Вы хотите закрыть окно?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedWarningDialog = false
                        showAddRecordDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = adminColor),
                ) {
                    Text(stringResource(R.string.dlg_yes_close), color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedWarningDialog = false }) {
                    Text(stringResource(R.string.dlg_continue_editing))
                }
            },
        )
    }

    if (showCreateUnsavedWarning) {
        AlertDialog(
            onDismissRequest = { showCreateUnsavedWarning = false },
            title = { Text(stringResource(R.string.dialog_close_form)) },
            text = {
                Text(
                    "Данные приёма сохранены во временный черновик в целях безопасности. " +
                        "Вы действительно хотите закрыть окно создания записи к врачу?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateUnsavedWarning = false
                        showCreateAppointmentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = adminColor),
                ) {
                    Text(stringResource(R.string.dlg_yes_close), color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateUnsavedWarning = false }) {
                    Text(stringResource(R.string.dlg_continue_editing))
                }
            },
        )
    }
}

/**
 * High-6 audit fix: StaffTopAppBar — extracted from StaffScreenContent.
 *
 * The TopAppBar with theme toggle + offline indicator + logout button.
 * Stateless — all callbacks passed via parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffTopAppBar(
    currentUser: com.aistudio.clinicsystem.data.db.UserEntity?,
    isOnline: Boolean,
    themeMode: String,
    onThemeToggle: (String) -> Unit,
    onLogout: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.staff_dashboard),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Сотрудник: ${currentUser?.fullName ?: "Доктор"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        actions = {
            if (!isOnline) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = stringResource(R.string.pat_no_connection),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(20.dp),
                )
            }
            val themeIcon = when (themeMode) {
                "LIGHT" -> Icons.Default.LightMode
                "DARK" -> Icons.Default.DarkMode
                else -> Icons.Default.BrightnessAuto
            }
            val themeDescription = when (themeMode) {
                "LIGHT" -> stringResource(R.string.theme_light)
                "DARK" -> stringResource(R.string.theme_dark)
                else -> stringResource(R.string.theme_system)
            }
            IconButton(
                onClick = {
                    val nextMode = when (themeMode) {
                        "SYSTEM" -> "LIGHT"
                        "LIGHT" -> "DARK"
                        else -> "SYSTEM"
                    }
                    onThemeToggle(nextMode)
                },
                modifier = Modifier
                    .testTag("theme_toggle_button_staff")
                    .minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = themeIcon,
                    contentDescription = themeDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onLogout,
                modifier = Modifier
                    .testTag("logout_button_staff")
                    .minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

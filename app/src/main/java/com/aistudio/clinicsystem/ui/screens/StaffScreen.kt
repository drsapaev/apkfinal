package com.aistudio.clinicsystem.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.ui.viewmodel.StaffViewModel

import com.aistudio.clinicsystem.ui.components.SecureScreen
import com.aistudio.clinicsystem.utils.TokenManager

import com.aistudio.clinicsystem.ui.screens.staff.AnalyticsCard
import com.aistudio.clinicsystem.ui.screens.staff.StaffAppointmentCardItem
import com.aistudio.clinicsystem.ui.screens.staff.StaffPatientCardItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    viewModel: StaffViewModel,
    modifier: Modifier = Modifier
) {
    // E1.5: secure the staff screen — staff sees PHI of multiple patients
    // (appointments, medical records, queue management).
    com.aistudio.clinicsystem.ui.components.SecureScreen {
        StaffScreenContent(viewModel, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffScreenContent(
    viewModel: StaffViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allAppointments by viewModel.allAppointments.collectAsStateWithLifecycle()
    val allPendingSyncs by viewModel.allPendingSyncs.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allRecords by viewModel.allMedicalRecords.collectAsStateWithLifecycle()

    // Undo snackbar infrastructure
    val snackbarHostState = remember { SnackbarHostState() }
    val undoState by viewModel.undoAction.collectAsStateWithLifecycle()

    LaunchedEffect(undoState) {
        val currentUndo = undoState
        if (currentUndo != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Действие успешно выполнено",
                actionLabel = "Отменить",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.triggerUndo()
            } else {
                viewModel.clearUndoAction()
            }
        }
    }

    // Safety Dialog conformations
    var showRemoveQueueConfirmDialog by remember { mutableStateOf(false) }
    var targetQueueSnapshotToRemove by remember { mutableStateOf<com.aistudio.clinicsystem.data.db.QueueSnapshotEntity?>(null) }
    var showCreateUnsavedWarning by remember { mutableStateOf(false) }
    var showEditUnsavedWarning by remember { mutableStateOf(false) }

    // Medical Card Draft Flows
    val draftDiagnosisVal by viewModel.draftDiagnosis.collectAsStateWithLifecycle()
    val draftPrescriptionVal by viewModel.draftPrescription.collectAsStateWithLifecycle()
    val draftRecommendationsVal by viewModel.draftRecommendations.collectAsStateWithLifecycle()
    val draftSelectedPatientPhoneVal by viewModel.draftSelectedPatientPhone.collectAsStateWithLifecycle()

    var showAddRecordDialog by remember { mutableStateOf(false) }
    var selectedPatientPhone by remember(draftSelectedPatientPhoneVal) { mutableStateOf(draftSelectedPatientPhoneVal) }
    var diagnosisInput by remember(draftDiagnosisVal) { mutableStateOf(draftDiagnosisVal) }
    var prescriptionInput by remember(draftPrescriptionVal) { mutableStateOf(draftPrescriptionVal) }
    var recommendationsInput by remember(draftRecommendationsVal) { mutableStateOf(draftRecommendationsVal) }

    var showCancelReasonDialog by remember { mutableStateOf(false) }
    var targetAppointmentIdToCancel by remember { mutableStateOf("") }
    var cancelReasonInput by remember { mutableStateOf("") }

    var showNotesDialog by remember { mutableStateOf(false) }
    var targetAppointmentIdForNotes by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    var filterTodayOnly by remember { mutableStateOf(false) }
    var showUnsavedWarningDialog by remember { mutableStateOf(false) }
    val todayDateStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }

    val adminColor = MaterialTheme.colorScheme.secondary
    val adminLight = MaterialTheme.colorScheme.secondaryContainer

    val cachedQueueSnapshots by viewModel.cachedQueueSnapshots.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedDoctorFilter by remember { mutableStateOf("Все врачи") }
    var selectedStatusFilter by remember { mutableStateOf("Все статусы") }

    // Create Appointment Dialog state & Flows
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

    // Edit Appointment Dialog state
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

    val patientRoleUsers = allUsers.filter { it.role == "PATIENT" }
    val pendingAppts = allAppointments.filter { it.status == "PENDING" }
    val approvedAppts = allAppointments.filter { it.status == "APPROVED" }

    LaunchedEffect(Unit) {
        com.aistudio.clinicsystem.utils.AnalyticsManager.trackScreen("StaffScreen")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Панель управления персоналом",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.surface
                        )
                        Text(
                            text = "Сотрудник: ${currentUser?.fullName ?: "Доктор"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                },
                actions = {
                    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                    val themeIcon = when (themeMode) {
                        "LIGHT" -> Icons.Default.LightMode
                        "DARK" -> Icons.Default.DarkMode
                        else -> Icons.Default.BrightnessAuto
                    }
                    val themeDescription = when (themeMode) {
                        "LIGHT" -> "Светлая тема"
                        "DARK" -> "Темная тема"
                        else -> "Системная тема"
                    }

                    IconButton(
                        onClick = {
                            val nextMode = when (themeMode) {
                                "SYSTEM" -> "LIGHT"
                                "LIGHT" -> "DARK"
                                else -> "SYSTEM"
                            }
                            viewModel.setThemeMode(nextMode)
                        },
                        modifier = Modifier
                            .testTag("theme_toggle_button_staff")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = themeDescription,
                            tint = MaterialTheme.colorScheme.surface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { viewModel.logOut() },
                        modifier = Modifier
                            .testTag("logout_button_staff")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.surface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = adminColor)
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
                text = { Text("Заполнить медкарту") },
                containerColor = adminColor,
                contentColor = MaterialTheme.colorScheme.surface
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                // Analytics Micro-cards today dashboard
                item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnalyticsCard(
                        title = "Заявки",
                        count = "${pendingAppts.size}",
                        icon = Icons.Default.PendingActions,
                        indicatorColor = Color(0xFFFBC02D),
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsCard(
                        title = "Одобрено",
                        count = "${approvedAppts.size}",
                        icon = Icons.Default.CheckCircle,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsCard(
                        title = "Пациенты",
                        count = "${patientRoleUsers.size}",
                        icon = Icons.Default.Group,
                        indicatorColor = adminColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section 0: Live Waiting Room Queue
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QueuePlayNext,
                                contentDescription = null,
                                tint = adminColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Живая очередь в клинике",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.surface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(adminColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Пациентов: ${cachedQueueSnapshots.size}",
                                fontSize = 11.sp,
                                color = adminColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (cachedQueueSnapshots.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "В коридоре ожидания пока никого нет.\nЗарегистрируйте подтвержденного пациента ниже.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cachedQueueSnapshots.sortedBy { it.position }.forEach { q ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(adminColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${q.position}",
                                                        color = MaterialTheme.colorScheme.surface,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = q.patientName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Приём #${q.appointmentId}",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            val qStatusColor = when (q.status) {
                                                "WAITING" -> Color(0xFFEA580C)
                                                "IN_PROGRESS" -> Color(0xFF2563EB)
                                                "COMPLETED" -> Color(0xFF16A34A)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            val qStatusText = when (q.status) {
                                                "WAITING" -> "Ожидает"
                                                "IN_PROGRESS" -> "На приёме"
                                                "COMPLETED" -> "Осмотрен"
                                                else -> q.status
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(qStatusColor.copy(alpha = 0.12f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = qStatusText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = qStatusColor
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.shiftQueuePosition(q.id, up = true) },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                    
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Вверх", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.shiftQueuePosition(q.id, up = false) },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                    
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Вниз", modifier = Modifier.size(16.dp))
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            if (q.status == "WAITING") {
                                                Button(
                                                    onClick = { viewModel.updateQueueStatus(q.id, "IN_PROGRESS") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                                    modifier = Modifier.heightIn(min = 44.dp)
                                                ) {
                                                    Text("Вызвать к врачу", fontSize = 10.sp, color = MaterialTheme.colorScheme.surface)
                                                }
                                            } else if (q.status == "IN_PROGRESS") {
                                                Button(
                                                    onClick = { viewModel.updateQueueStatus(q.id, "COMPLETED") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                                    modifier = Modifier.heightIn(min = 44.dp)
                                                ) {
                                                    Text("Завершить прием", fontSize = 10.sp, color = MaterialTheme.colorScheme.surface)
                                                }
                                            }

                                            TextButton(
                                                onClick = {
                                                    targetQueueSnapshotToRemove = q
                                                    showRemoveQueueConfirmDialog = true
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                                modifier = Modifier.heightIn(min = 44.dp).testTag("remove_queue_patient_button")
                                            ) {
                                                Text("Убрать", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 1: Scheduler Approvals queue
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AssignmentLate,
                                contentDescription = null,
                                tint = adminColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Очередь приёма и одобрение записей",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Create Appointment Action button for registrar
                        Button(
                            onClick = {
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
                            colors = ButtonDefaults.buttonColors(containerColor = adminColor),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 44.dp).testTag("create_appointment_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surface)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Приём", fontSize = 11.sp, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                        }
                    }

                    // SEARCH BY NAME OR PHONE
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск по ФИО или номеру телефона...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = adminColor,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("appointment_search_field")
                    )

                    // TIMED TODAY ONLY FILTER
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!filterTodayOnly) adminColor else MaterialTheme.colorScheme.surface)
                                .border(1.dp, if (!filterTodayOnly) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable { filterTodayOnly = false }
                                .padding(vertical = 10.dp)
                                .testTag("all_appointments_tab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Все записи (${allAppointments.size})",
                                color = if (!filterTodayOnly) MaterialTheme.colorScheme.surface else Color.DarkGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (filterTodayOnly) adminColor else MaterialTheme.colorScheme.surface)
                                .border(1.dp, if (filterTodayOnly) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable { filterTodayOnly = true }
                                .padding(vertical = 10.dp)
                                .testTag("today_appointments_tab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = if (filterTodayOnly) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "На Сегодня (${allAppointments.count { it.date == todayDateStr }})",
                                    color = if (filterTodayOnly) MaterialTheme.colorScheme.surface else Color.DarkGray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // DOCTOR FILTER CHIPS
                    Text("Фильтр по врачу:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    val allDoctorsInSystem = remember(allAppointments) {
                        allAppointments.map { it.doctorName }.distinct().filter { it.isNotBlank() }
                    }
                    val isCurrentUserDoctor = currentUser?.fullName?.startsWith("Dr.") == true || currentUser?.jobTitle == "DOCTOR"
                    
                    LaunchedEffect(isCurrentUserDoctor, currentUser) {
                        if (isCurrentUserDoctor && currentUser != null) {
                            selectedDoctorFilter = currentUser!!.fullName
                        }
                    }

                    val doctorsList = buildList {
                        add("Все врачи")
                        if (isCurrentUserDoctor && currentUser != null) {
                            if (!contains(currentUser!!.fullName)) add(currentUser!!.fullName)
                        }
                        addAll(allDoctorsInSystem.filter { it != currentUser?.fullName })
                    }.distinct()

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        doctorsList.forEach { doc ->
                            val isSelected = selectedDoctorFilter == doc
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) adminColor else MaterialTheme.colorScheme.outlineVariant)
                                    .clickable { selectedDoctorFilter = doc }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = doc,
                                    fontSize = 10.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.DarkGray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // STATUS FILTER CHIPS
                    Text("Фильтр по статусу:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val statusesList = listOf(
                        "Все статусы",
                        "PENDING",
                        "APPROVED",
                        "COMPLETED",
                        "CANCELLED"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusesList.forEach { status ->
                            val labelText = when (status) {
                                "Все статусы" -> "Все статусы"
                                "PENDING" -> "На рассмотрении"
                                "APPROVED" -> "Подтверждено"
                                "COMPLETED" -> "Осмотр завершен"
                                "CANCELLED" -> "Отклонено"
                                else -> status
                            }
                            val isSelected = selectedStatusFilter == status
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) adminColor else MaterialTheme.colorScheme.outlineVariant)
                                    .clickable { selectedStatusFilter = status }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = labelText,
                                    fontSize = 10.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.DarkGray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            val displayAppointments = allAppointments.filter { appt ->
                val matchesToday = if (filterTodayOnly) appt.date == todayDateStr else true
                
                val matchesSearch = if (searchQuery.isNotBlank()) {
                    appt.patientName.contains(searchQuery, ignoreCase = true) ||
                    appt.patientPhone.contains(searchQuery)
                } else true
                
                val matchesDoctor = if (selectedDoctorFilter != "Все врачи") {
                    appt.doctorName == selectedDoctorFilter
                } else true
                
                val matchesStatus = if (selectedStatusFilter != "Все статусы") {
                    appt.status == selectedStatusFilter
                } else true
                
                matchesToday && matchesSearch && matchesDoctor && matchesStatus
            }

            if (displayAppointments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Подходящих сеансов или записей не найдено.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(displayAppointments, key = { it.id }) { appt ->
                    val isPendingSync = allPendingSyncs.any {
                        it.clientRequestId == appt.clientRequestId ||
                        (it.type == "UPDATE_STATUS" && it.payload.startsWith("${appt.id}|"))
                    }
                    StaffAppointmentCardItem(
                        appt = appt,
                        isPendingSync = isPendingSync,
                        onApprove = { viewModel.approveAppointment(appt.id) },
                        onCancelClick = {
                            targetAppointmentIdToCancel = appt.id
                            cancelReasonInput = ""
                            showCancelReasonDialog = true
                        },
                        onAddNotesClick = {
                            targetAppointmentIdForNotes = appt.id
                            notesInput = appt.notes
                            showNotesDialog = true
                        },
                        onEditClick = {
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
                        onRegisterQueue = {
                            viewModel.registerPatientInQueue(appt.id)
                        },
                        accentColor = adminColor
                    )
                }
            }

            // Section 2: Patients Directory
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = null,
                        tint = adminColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Справочник пациентов и медкарты",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val displayPatients = patientRoleUsers.filter {
                if (searchQuery.isNotBlank()) {
                    it.fullName.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery)
                } else true
            }

            if (displayPatients.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пациенты не найдены.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(displayPatients, key = { it.id }) { patient ->
                    val patientRecords = allRecords.filter { it.patientPhone == patient.phone }
                    StaffPatientCardItem(
                        patient = patient,
                        recordsCount = patientRecords.size,
                        onWriteRecord = {
                            selectedPatientPhone = patient.phone
                            diagnosisInput = ""
                            prescriptionInput = ""
                            recommendationsInput = ""
                            showAddRecordDialog = true
                        },
                        accentColor = adminColor
                    )
                }
            }
        }
        }
    }

    // Modal dialogue to fill in medical card
    if (showAddRecordDialog) {
        Dialog(onDismissRequest = { 
            val hasDraftText = diagnosisInput.isNotBlank() || prescriptionInput.isNotBlank() || recommendationsInput.isNotBlank()
            if (hasDraftText) {
                showUnsavedWarningDialog = true
            } else {
                showAddRecordDialog = false
            }
        }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .imePadding().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Внести запись в медицинскую карту",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = adminColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Draft auto-save status indicator with dynamic dismiss button
                    val hasDraftText = diagnosisInput.isNotBlank() || prescriptionInput.isNotBlank() || recommendationsInput.isNotBlank()
                    if (hasDraftText) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Активный черновик (защита от потери)",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Сбросить",
                                fontSize = 11.sp,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.clearMedicalRecordDraft()
                                        diagnosisInput = ""
                                        prescriptionInput = ""
                                        recommendationsInput = ""
                                    }
                                    .padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 1. One-tap templates
                    Text(
                        text = "⚡ Быстрые шаблоны в один тап:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = adminColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("templates_row")
                    ) {
                        val templatesList = listOf(
                            listOf(
                                "🌡️ ОРВИ",
                                "ОРВИ, острое течение (МКБ-10: J06.9)",
                                "Парацетамол 500мг при темп. >38.5С, обильное теплое питье, витамин С 1000мг.",
                                "Постельный режим 3-5 дней, повторный прием при сохранении лихорадки."
                            ),
                            listOf(
                                "🫀  Гипертония",
                                "Артериальная гипертензия II ст., риск 2 (МКБ-10: I11.9)",
                                "Лизиноприл 10мг утром натощак. Измерение давления утром и вечером.",
                                "Ограничить соль, чай, кофе на период кризов. ЭКГ в плановом порядке."
                            ),
                            listOf(
                                "🦷  Пульпит",
                                "Острый пульпит (МКБ-10: K04.0)",
                                "Ибупрофен 400мг при острых болях (не более 3 раз в день), полоскание раствором соды.",
                                "Срочно обратиться к стоматологу для санации зуба."
                            ),
                            listOf(
                                "🩹 Остеохондроз",
                                "Дорсопатия, остеохондроз поясничного отдела (МКБ-10: M54.5)",
                                "Мелоксикам 15мг 1 таб в день после еды 5 дней, мазь Диклофенак локально.",
                                "Избегать поднятия тяжестей, спать на ортопедическом матрасе."
                            )
                        )
                        items(templatesList) { template ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(adminLight.copy(alpha = 0.5f))
                                    .border(1.dp, adminColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        diagnosisInput = template[1]
                                        prescriptionInput = template[2]
                                        recommendationsInput = template[3]
                                        viewModel.setDraftDiagnosis(template[1])
                                        viewModel.setDraftPrescription(template[2])
                                        viewModel.setDraftRecommendations(template[3])
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = template[0],
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = adminColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "Выберите пациента:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Patient Selector Spinner layout
                    patientRoleUsers.forEach { pat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedPatientPhone == pat.phone) adminLight else Color.Transparent)
                                .clickable { 
                                    selectedPatientPhone = pat.phone 
                                    viewModel.setDraftSelectedPatientPhone(pat.phone)
                                }
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedPatientPhone == pat.phone,
                                onClick = { 
                                    selectedPatientPhone = pat.phone 
                                    viewModel.setDraftSelectedPatientPhone(pat.phone)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = adminColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(text = pat.fullName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = pat.phone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Copy previous visit feature
                    if (selectedPatientPhone.isNotEmpty()) {
                        val patientRecords = allRecords.filter { it.patientPhone == selectedPatientPhone }
                        val lastRecord = remember(selectedPatientPhone, allRecords) { patientRecords.maxByOrNull { it.timestamp } }
                        if (lastRecord != null) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        diagnosisInput = lastRecord.diagnosis
                                        prescriptionInput = lastRecord.prescription
                                        recommendationsInput = lastRecord.recommendations
                                        viewModel.setDraftDiagnosis(lastRecord.diagnosis)
                                        viewModel.setDraftPrescription(lastRecord.prescription)
                                        viewModel.setDraftRecommendations(lastRecord.recommendations)
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("copy_previous_visit_card")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "📋  Скопировать данные визита от ${lastRecord.visitDate}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Диагноз: ${lastRecord.diagnosis}",
                                            fontSize = 11.sp,
                                            color = Color.DarkGray,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // 3. Clinical diagnosis + Quick Diagnosis Badges
                    OutlinedTextField(
                        value = diagnosisInput,
                        onValueChange = { 
                            diagnosisInput = it 
                            viewModel.setDraftDiagnosis(it)
                        },
                        label = { Text("Клинический диагноз") },
                        placeholder = { Text("Острый пульпит, гипертония 2 ст. и др.") },
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth().testTag("diagnosis_input_field")
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Рекомендованные диагнозы (ICD-10):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        val icdDiagnoses = listOf("J06.9 (ОРВИ)", "I11.9 (Гипертония)", "K04.0 (Пульпит)", "M54.5 (Спина)", "K29.9 (Гастрит)", "Z01.2 (Осмотр)")
                        icdDiagnoses.forEach { diag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                    .clickable {
                                        val nextDiag = if (diagnosisInput.isBlank()) diag else "$diagnosisInput, $diag"
                                        diagnosisInput = nextDiag
                                        viewModel.setDraftDiagnosis(nextDiag)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = diag, fontSize = 10.sp, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Prescriptions and Recipes + Favorite phrases
                    OutlinedTextField(
                        value = prescriptionInput,
                        onValueChange = { 
                            prescriptionInput = it 
                            viewModel.setDraftPrescription(it)
                        },
                        label = { Text("Назначения и Рецептурный лист") },
                        placeholder = { Text("Принимать Лозартан 50мг по утрам, полоскание") },
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        val rxPhrases = listOf("+ Вит. C 1000мг", "Обильное питье", "Ибупрофен 400мг", "Полоскание содой", "Лозартан 50мг утром")
                        rxPhrases.forEach { phrase ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val nextPresc = if (prescriptionInput.isBlank()) phrase else "$prescriptionInput, $phrase"
                                        prescriptionInput = nextPresc
                                        viewModel.setDraftPrescription(nextPresc)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = phrase, fontSize = 10.sp, color = Color.DarkGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Recommendations and Advice + Favorite phrases
                    OutlinedTextField(
                        value = recommendationsInput,
                        onValueChange = { 
                            recommendationsInput = it 
                            viewModel.setDraftRecommendations(it)
                        },
                        label = { Text("Советы и рекомендации") },
                        placeholder = { Text("Повторный визит через две недели.") },
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        val recPhrases = listOf("Явка через 3 дня", "Контроль АД", "Ограничить соль", "Постельный режим", "Диета Стол №5")
                        recPhrases.forEach { phrase ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val nextRec = if (recommendationsInput.isBlank()) phrase else "$recommendationsInput, $phrase"
                                        recommendationsInput = nextRec
                                        viewModel.setDraftRecommendations(nextRec)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = phrase, fontSize = 10.sp, color = Color.DarkGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            if (hasDraftText) {
                                showUnsavedWarningDialog = true
                            } else {
                                showAddRecordDialog = false
                            }
                        }) {
                            Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedPatientPhone.isNotEmpty() && diagnosisInput.isNotBlank()) {
                                    viewModel.createMedicalRecord(
                                        patientPhone = selectedPatientPhone,
                                        diagnosis = diagnosisInput,
                                        prescription = prescriptionInput,
                                        recommendations = recommendationsInput
                                    )
                                    // Successfully completed -> clean inputs
                                    diagnosisInput = ""
                                    prescriptionInput = ""
                                    recommendationsInput = ""
                                    viewModel.clearMedicalRecordDraft()
                                    showAddRecordDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = adminColor),
                            modifier = Modifier.testTag("save_medical_record_button")
                        ) {
                            Text("Сохранить в базу", color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue popup for Unsaved draft protection warning
    if (showUnsavedWarningDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedWarningDialog = false },
            title = { Text("Выйти из редактора?") },
            text = { Text("Введенная информация будет сохранена в локальный черновик на этом экране, чтобы вы не потеряли данные при случайном клике. Вы хотите закрыть окно создания записи?") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedWarningDialog = false
                        showAddRecordDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = adminColor)
                ) {
                    Text("Да, закрыть", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedWarningDialog = false }) {
                    Text("Продолжить редактирование")
                }
            }
        )
    }

    if (showCreateUnsavedWarning) {
        AlertDialog(
            onDismissRequest = { showCreateUnsavedWarning = false },
            title = { Text("Закрыть форму записи?") },
            text = { Text("Данные приёма сохранены во временный черновик в целях безопасности. Вы действительно хотите закрыть окно создания записи к врачу?") },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateUnsavedWarning = false
                        showCreateAppointmentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = adminColor)
                ) {
                    Text("Да, закрыть", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateUnsavedWarning = false }) {
                    Text("Продолжить редактирование")
                }
            }
        )
    }

    if (showRemoveQueueConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showRemoveQueueConfirmDialog = false
                targetQueueSnapshotToRemove = null
            },
            title = { Text("Убрать из живой очереди?") },
            text = { Text("Вы хотите исключить из списка ожидания пациента ${targetQueueSnapshotToRemove?.patientName ?: "пациента"}? (Эту операцию можно будет отменить).") },
            confirmButton = {
                Button(
                    onClick = {
                        targetQueueSnapshotToRemove?.let { q ->
                            viewModel.removeQueuePatient(q.id)
                        }
                        showRemoveQueueConfirmDialog = false
                        targetQueueSnapshotToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.testTag("confirm_remove_queue_patient_btn")
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRemoveQueueConfirmDialog = false
                    targetQueueSnapshotToRemove = null
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Modal reasons for cancellation
    if (showCancelReasonDialog) {
        AlertDialog(
            onDismissRequest = { showCancelReasonDialog = false },
            title = { Text("Причина отказа в записи") },
            text = {
                OutlinedTextField(
                    value = cancelReasonInput,
                    onValueChange = { cancelReasonInput = it },
                    label = { Text("Причина отмены") },
                    placeholder = { Text("Врач заболел или время занято") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelAppointment(targetAppointmentIdToCancel, cancelReasonInput)
                        showCancelReasonDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Отменить запись", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelReasonDialog = false }) {
                    Text("Назад")
                }
            }
        )
    }

    // Modal edit notes dialogue
    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text("Редактировать комментарий к приёму") },
            text = {
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Короткое примечание клиники") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addStaffNotesToAppointment(targetAppointmentIdForNotes, notesInput)
                        showNotesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = adminColor)
                ) {
                    Text("Внести", color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Назад")
                }
            }
        )
    }

    // Modal dialogue to CREATE a new appointment
    if (showCreateAppointmentDialog) {
        Dialog(onDismissRequest = {
            val hasCreateDraft = createPatientPhone.isNotBlank() || createPatientName.isNotBlank() || createReason.isNotBlank()
            if (hasCreateDraft) {
                showCreateUnsavedWarning = true
            } else {
                showCreateAppointmentDialog = false
            }
        }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Записать на приём (Регистратор)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = adminColor
                    )

                    Text("Пациент в базе клиники:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    if (patientRoleUsers.isEmpty()) {
                        Text("Нет зарегистрированных пациентов", fontSize = 12.sp, color = Color.Red)
                    } else {
                        var expandedPatientSpinner by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedPatientSpinner = true },
                                modifier = Modifier.fillMaxWidth().testTag("select_patient_spinner_btn")
                            ) {
                                Text(text = "$createPatientName ($createPatientPhone)", fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = expandedPatientSpinner,
                                onDismissRequest = { expandedPatientSpinner = false }
                            ) {
                                patientRoleUsers.forEach { pat ->
                                    DropdownMenuItem(
                                        text = { Text("${pat.fullName} (${pat.phone})") },
                                        onClick = {
                                            createPatientPhone = pat.phone
                                            viewModel.setDraftCreatePatientPhone(pat.phone)
                                            createPatientName = pat.fullName
                                            viewModel.setDraftCreatePatientName(pat.fullName)
                                            expandedPatientSpinner = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text("Или введите профиль вручную:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = createPatientName,
                        onValueChange = {
                            createPatientName = it
                            viewModel.setDraftCreatePatientName(it)
                        },
                        label = { Text("ФИО Пациента") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth().testTag("manual_patient_name")
                    )
                    OutlinedTextField(
                        value = createPatientPhone,
                        onValueChange = {
                            createPatientPhone = it
                            viewModel.setDraftCreatePatientPhone(it)
                        },
                        label = { Text("Телефон") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth().testTag("manual_patient_phone")
                    )

                    Text("Лечащий врач и специальность:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    val availableDocs = listOf(
                        "Д-р Сапаев (Стоматолог-терапевт)" to "Стоматология",
                        "Д-р Иванов (Кардиолог)" to "Кардиология",
                        "Д-р Петров (Невролог)" to "Неврология",
                        "Д-р Сидорова (Педиатр)" to "Педиатрия",
                        "Д-р Смирнова (Офтальмолог)" to "Офтальмология",
                        "Д-р Кузнецов (Терапевт)" to "Общая терапия"
                    )
                    var expandedDocSpinner by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDocSpinner = true },
                            modifier = Modifier.fillMaxWidth().testTag("select_doctor_spinner_btn")
                        ) {
                            Text(text = "$createDoctorSelected ($createSpecialtySelected)", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = expandedDocSpinner,
                            onDismissRequest = { expandedDocSpinner = false }
                        ) {
                            availableDocs.forEach { pair ->
                                DropdownMenuItem(
                                    text = { Text("${pair.first} - ${pair.second}") },
                                    onClick = {
                                        createDoctorSelected = pair.first
                                        viewModel.setDraftCreateDoctorSelected(pair.first)
                                        createSpecialtySelected = pair.second
                                        viewModel.setDraftCreateSpecialtySelected(pair.second)
                                        expandedDocSpinner = false
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = createDate,
                            onValueChange = {
                                createDate = it
                                viewModel.setDraftCreateDate(it)
                            },
                            label = { Text("Дата (ГГГГ-ММ-ДД)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("create_date_field")
                        )
                        OutlinedTextField(
                            value = createTime,
                            onValueChange = {
                                createTime = it
                                viewModel.setDraftCreateTime(it)
                            },
                            label = { Text("Время (ЧЧ:ММ)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("create_time_field")
                        )
                    }

                    OutlinedTextField(
                        value = createReason,
                        onValueChange = {
                            createReason = it
                            viewModel.setDraftCreateReason(it)
                        },
                        label = { Text("Причина приёма") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().testTag("create_reason_field")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            val hasCreateDraft = createPatientPhone.isNotBlank() || createPatientName.isNotBlank() || createReason.isNotBlank()
                            if (hasCreateDraft) {
                                showCreateUnsavedWarning = true
                            } else {
                                showCreateAppointmentDialog = false
                            }
                        }) {
                            Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (createPatientPhone.isNotBlank() && createPatientName.isNotBlank()) {
                                    viewModel.createAppointment(
                                        patientPhone = createPatientPhone,
                                        patientName = createPatientName,
                                        doctorName = createDoctorSelected,
                                        specialty = createSpecialtySelected,
                                        date = createDate,
                                        time = createTime,
                                        reason = createReason
                                    )
                                    viewModel.clearCreateAppointmentDraft()
                                    showCreateAppointmentDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = adminColor),
                            modifier = Modifier.testTag("submit_create_appointment_btn")
                        ) {
                            Text("Создать приём", color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue to EDIT an active appointment
    if (showEditAppointmentDialog) {
        Dialog(onDismissRequest = { showEditAppointmentDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Редактировать приём #${editAppointmentId}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = adminColor
                    )

                    OutlinedTextField(
                        value = editPatientName,
                        onValueChange = { editPatientName = it },
                        label = { Text("ФИО Пациента") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_patient_name")
                    )

                    OutlinedTextField(
                        value = editPatientPhone,
                        onValueChange = { editPatientPhone = it },
                        label = { Text("Телефон") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_patient_phone")
                    )

                    Text("Выберите лечащего врача:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    val availableDocs = listOf(
                        "Д-р Сапаев (Стоматолог-терапевт)" to "Стоматология",
                        "Д-р Иванов (Кардиолог)" to "Кардиология",
                        "Д-р Петров (Невролог)" to "Неврология",
                        "Д-р Сидорова (Педиатр)" to "Педиатрия",
                        "Д-р Смирнова (Офтальмолог)" to "Офтальмология",
                        "Д-р Кузнецов (Терапевт)" to "Общая терапия"
                    )
                    var expandedEditDocSpinner by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedEditDocSpinner = true },
                            modifier = Modifier.fillMaxWidth().testTag("edit_doc_spinner")
                        ) {
                            Text(text = "$editDoctorSelected ($editSpecialtySelected)", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = expandedEditDocSpinner,
                            onDismissRequest = { expandedEditDocSpinner = false }
                        ) {
                            availableDocs.forEach { pair ->
                                DropdownMenuItem(
                                    text = { Text("${pair.first} - ${pair.second}") },
                                    onClick = {
                                        editDoctorSelected = pair.first
                                        editSpecialtySelected = pair.second
                                        expandedEditDocSpinner = false
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editDate,
                            onValueChange = { editDate = it },
                            label = { Text("Дата") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_date")
                        )
                        OutlinedTextField(
                            value = editTime,
                            onValueChange = { editTime = it },
                            label = { Text("Время") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_time")
                        )
                    }

                    OutlinedTextField(
                        value = editReason,
                        onValueChange = { editReason = it },
                        label = { Text("Жалобы / Причина приёма") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().testTag("edit_reason")
                    )

                    Text("Статус записи:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    val statusList = listOf("PENDING", "APPROVED", "COMPLETED", "CANCELLED")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusList.forEach { status ->
                            val label = when (status) {
                                "PENDING" -> "Ожидает"
                                "APPROVED" -> "Подтвержден"
                                "COMPLETED" -> "Выполнен"
                                "CANCELLED" -> "Отменен"
                                else -> status
                            }
                            val isSelected = editStatusSelected == status
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) adminColor else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { editStatusSelected = status }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.DarkGray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditAppointmentDialog = false }) {
                            Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
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
                                        status = editStatusSelected
                                    )
                                    showEditAppointmentDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = adminColor),
                            modifier = Modifier.testTag("submit_edit_appointment_btn")
                        ) {
                            Text("Применить изменения", color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
        }
    }
}

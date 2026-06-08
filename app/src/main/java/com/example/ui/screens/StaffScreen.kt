package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.data.db.AppointmentEntity
import com.example.data.db.UserEntity
import com.example.ui.viewmodel.ClinicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    viewModel: ClinicViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allAppointments by viewModel.allAppointments.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allRecords by viewModel.allMedicalRecords.collectAsStateWithLifecycle()

    var showAddRecordDialog by remember { mutableStateOf(false) }
    var selectedPatientPhone by remember { mutableStateOf("") }
    var diagnosisInput by remember { mutableStateOf("") }
    var prescriptionInput by remember { mutableStateOf("") }
    var recommendationsInput by remember { mutableStateOf("") }

    var showCancelReasonDialog by remember { mutableStateOf(false) }
    var targetAppointmentIdToCancel by remember { mutableStateOf(-1) }
    var cancelReasonInput by remember { mutableStateOf("") }

    var showNotesDialog by remember { mutableStateOf(false) }
    var targetAppointmentIdForNotes by remember { mutableStateOf(-1) }
    var notesInput by remember { mutableStateOf("") }

    val adminColor = MaterialTheme.colorScheme.secondary
    val adminLight = MaterialTheme.colorScheme.secondaryContainer

    val patientRoleUsers = allUsers.filter { it.role == "PATIENT" }
    val pendingAppts = allAppointments.filter { it.status == "PENDING" }
    val approvedAppts = allAppointments.filter { it.status == "APPROVED" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Панель управления персоналом",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
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
                            tint = Color.White
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
                            tint = Color.White
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
                contentColor = Color.White
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
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
                        indicatorColor = Color(0xFF4CAF50),
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

            // Section 1: Scheduler Approvals queue
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        color = Color(0xFF263238)
                    )
                }
            }

            if (allAppointments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE0E6ED), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Новых приёмов не запланировано.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(allAppointments, key = { it.id }) { appt ->
                    StaffAppointmentCardItem(
                        appt = appt,
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
                        color = Color(0xFF263238)
                    )
                }
            }

            if (patientRoleUsers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE0E6ED), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пациенты в базе данных не найдены.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(patientRoleUsers, key = { it.id }) { patient ->
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

    // Modal dialogue to fill in medical card
    if (showAddRecordDialog) {
        Dialog(onDismissRequest = { showAddRecordDialog = false }) {
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
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Внести запись в медицинскую карту",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = adminColor
                    )

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
                                .clickable { selectedPatientPhone = pat.phone }
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedPatientPhone == pat.phone,
                                onClick = { selectedPatientPhone = pat.phone },
                                colors = RadioButtonDefaults.colors(selectedColor = adminColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(text = pat.fullName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = pat.phone, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = diagnosisInput,
                        onValueChange = { diagnosisInput = it },
                        label = { Text("Клинический диагноз") },
                        placeholder = { Text("Острый пульпит, гипертония 2 ст. и др.") },
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = prescriptionInput,
                        onValueChange = { prescriptionInput = it },
                        label = { Text("Назначения и Рецептурный лист") },
                        placeholder = { Text("Принимать Лозартан 50мг по утрам, полоскание") },
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = recommendationsInput,
                        onValueChange = { recommendationsInput = it },
                        label = { Text("Советы и рекомендации") },
                        placeholder = { Text("Повторный визит через две недели.") },
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = adminColor, focusedLabelColor = adminColor),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddRecordDialog = false }) {
                            Text("Отмена", color = Color.Gray)
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
                                    showAddRecordDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = adminColor)
                        ) {
                            Text("Сохранить в базу", color = Color.White)
                        }
                    }
                }
            }
        }
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
                    Text("Отменить запись", color = Color.White)
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
                    Text("Внести", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Назад")
                }
            }
        )
    }
}

@Composable
fun AnalyticsCard(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = indicatorColor,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StaffAppointmentCardItem(
    appt: AppointmentEntity,
    onApprove: () -> Unit,
    onCancelClick: () -> Unit,
    onAddNotesClick: () -> Unit,
    accentColor: Color
) {
    val statusColor = when (appt.status) {
        "PENDING" -> Color(0xFFFBC02D)
        "APPROVED" -> Color(0xFF4CAF50)
        "COMPLETED" -> Color(0xFF2196F3)
        "CANCELLED" -> Color(0xFFF44336)
        else -> Color.Gray
    }

    val statusTextRu = when (appt.status) {
        "PENDING" -> "На рассмотрении"
        "APPROVED" -> "Подтверждено"
        "COMPLETED" -> "Осмотр завершен"
        "CANCELLED" -> "Отклонено"
        else -> appt.status
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = appt.patientName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF263238)
                    )
                    Text(
                        text = "Телефон: ${appt.patientPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusTextRu,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Text(
                text = "Доктор: ${appt.doctorName}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            Text(
                text = "Сеанс: ${appt.date} в ${appt.time}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Жалобы: ${appt.reason}",
                fontSize = 13.sp,
                color = Color.Black
            )

            if (appt.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFECEFF1))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Заметка / Ответ: ${appt.notes}",
                        fontSize = 12.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action queues
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (appt.status == "PENDING") {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Одобрить", fontSize = 12.sp, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Отклонить", fontSize = 12.sp, color = Color.Red)
                    }
                } else {
                    OutlinedButton(
                        onClick = onAddNotesClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NoteAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Править заметку к сеансу", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StaffPatientCardItem(
    patient: UserEntity,
    recordsCount: Int,
    onWriteRecord: () -> Unit,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFECEFF1), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalInformation,
                    contentDescription = null,
                    tint = accentColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.fullName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF37474F)
                )
                Text(
                    text = "Тел: ${patient.phone}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Записей в медкарте: $recordsCount",
                    fontSize = 11.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = onWriteRecord) {
                Icon(
                    imageVector = Icons.Default.AddBox,
                    contentDescription = "New Record entry",
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

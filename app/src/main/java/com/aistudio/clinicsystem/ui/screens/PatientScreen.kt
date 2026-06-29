package com.aistudio.clinicsystem.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import com.aistudio.clinicsystem.ui.screens.patient.AppointmentSegmentTabs
import com.aistudio.clinicsystem.ui.screens.patient.AppointmentsSessionList
import com.aistudio.clinicsystem.ui.screens.patient.BookAppointmentDialog
import com.aistudio.clinicsystem.ui.screens.patient.CachedQueueSnapshotsCard
import com.aistudio.clinicsystem.ui.screens.patient.EditProfileDialog
import com.aistudio.clinicsystem.ui.screens.patient.HeaderGreetingBanner
import com.aistudio.clinicsystem.ui.screens.patient.MedicalReportsSection
import com.aistudio.clinicsystem.ui.screens.patient.ProfileCabinetCard
import com.aistudio.clinicsystem.ui.screens.patient.TelegramBotCard
import com.aistudio.clinicsystem.ui.viewmodel.PatientViewModel
import com.aistudio.clinicsystem.utils.TokenManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.aistudio.clinicsystem.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientScreen(
    viewModel: PatientViewModel,
    modifier: Modifier = Modifier
) {
    // E1.5: secure the patient screen — contains PHI (appointments, medical records,
    // queue position, telegram id). Prevents screenshots and screen recording.
    com.aistudio.clinicsystem.ui.components.SecureScreen {
        PatientScreenContent(viewModel, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientScreenContent(
    viewModel: PatientViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val appointments by viewModel.patientAppointments.collectAsStateWithLifecycle()
    val allPendingSyncs by viewModel.allPendingSyncs.collectAsStateWithLifecycle()
    val records by viewModel.patientRecords.collectAsStateWithLifecycle()
    val isFetchingRecords by viewModel.isFetchingReports.collectAsStateWithLifecycle()
    val isBookingInProgress by viewModel.isBookingInProgress.collectAsStateWithLifecycle()
    val cachedQueueSnapshots by viewModel.cachedQueueSnapshots.collectAsStateWithLifecycle()

    var showEditProfile by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf(currentUser?.fullName ?: "") }

    var showBookDialog by remember { mutableStateOf(false) }
    var selectedDoctor by remember { mutableStateOf("Dr. Rustam Sapaev") }
    var selectedSpecialty by remember { mutableStateOf("Стоматолог-Хирург") }
    var selectedDateIdx by remember { mutableStateOf(0) }
    var selectedTimeSlot by remember { mutableStateOf("11:00") }
    var bookingReasonInput by remember { mutableStateOf("") }

    // Navigation and filters
    var selectedAppFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, FINISHED
    var medicalSearchQuery by remember { mutableStateOf("") }
    var expandedRecords by remember { mutableStateOf(setOf<String>()) }

    val tealPrimary = MaterialTheme.colorScheme.primary
    val tealLight = MaterialTheme.colorScheme.primaryContainer
    val tealDark = MaterialTheme.colorScheme.secondary
    val accentNavy = MaterialTheme.colorScheme.onSurface
    val backgroundSoft = MaterialTheme.colorScheme.background

    // Generate upcoming dates for booking
    val bookingDatesList = remember {
        val list = mutableListOf<String>()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        repeat(5) {
            list.add(formatter.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val doctors = listOf(
        Pair("Dr. Rustam Sapaev", "Стоматолог-Хирург (Dentist-Surgeon)"),
        Pair("Dr. Elena Petrova", "Кардиолог (Cardiologist)"),
        Pair("Dr. Alexander Smirnov", "Невролог (Neurologist)")
    )
    val timeSlots = listOf("09:00", "10:00", "11:00", "12:00", "14:00", "15:00", "16:00")

    // Filter appointments based on selection
    val filteredAppointments = remember(appointments, selectedAppFilter) {
        when (selectedAppFilter) {
            "ACTIVE" -> appointments.filter { it.status == "PENDING" || it.status == "APPROVED" }
            "FINISHED" -> appointments.filter { it.status == "COMPLETED" || it.status == "CANCELLED" }
            else -> appointments
        }
    }

    // Filter records based on search query
    val filteredRecords = remember(records, medicalSearchQuery) {
        if (medicalSearchQuery.isBlank()) {
            records
        } else {
            records.filter {
                it.diagnosis.contains(medicalSearchQuery, ignoreCase = true) ||
                        it.doctorName.contains(medicalSearchQuery, ignoreCase = true) ||
                        it.prescription.contains(medicalSearchQuery, ignoreCase = true) ||
                        it.recommendations.contains(medicalSearchQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        com.aistudio.clinicsystem.utils.AnalyticsManager.trackScreen("PatientScreen")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                            modifier = Modifier
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Интеллект-Клиника",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.surface
                            )
                            Text(
                                text = "Личный Кабинет Пациента",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                        }
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
                            .testTag("theme_toggle_button")
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
                            .testTag("logout_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Log out",
                            tint = MaterialTheme.colorScheme.surface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tealPrimary)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showBookDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Book Appointment") },
                text = { Text("Запись на приём", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .testTag("book_appointment_fab")
                    .padding(bottom = 12.dp)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundSoft)
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth > 720.dp

            if (isWideScreen) {
                // RESPONSIVE LANDSCAPE TABLET LAYOUT: Two independent scrollable panes
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // LEFT COLUMN (60% width): Greeting banner + Appointments
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .imePadding().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HeaderGreetingBanner(
                            userName = currentUser?.fullName ?: "Пациент",
                            activeAppointmentsCount = appointments.count { it.status == "PENDING" || it.status == "APPROVED" },
                            completedRecordsCount = records.size
                        )

                        // Segment selector for Appointments
                        AppointmentSegmentTabs(
                            selectedFilter = selectedAppFilter,
                            onFilterSelect = { selectedAppFilter = it }
                        )

                        AppointmentsSessionList(
                            appointments = filteredAppointments,
                            pendingSyncs = allPendingSyncs,
                            onCancelClick = { id -> viewModel.cancelAppointment(id, "Отменено пациентом в кабинете") }
                        )
                    }

                    // RIGHT COLUMN (40% width): Profile Details + Past Medical Records
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .imePadding().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileCabinetCard(
                            user = currentUser,
                            onEditClick = {
                                editNameInput = currentUser?.fullName ?: ""
                                showEditProfile = true
                            },
                            onBiometricToggle = { viewModel.setBiometricEnrollment(it) }
                        )

                        TelegramBotCard(
                            user = currentUser,
                            onLinkClick = { chat -> viewModel.linkTelegramChatId(chat) },
                            onUnlinkClick = { viewModel.unlinkTelegramChatId() },
                            onTestClick = { viewModel.sendTestTelegramNotification() }
                        )

                        CachedQueueSnapshotsCard(
                            snapshots = cachedQueueSnapshots
                        )

                        MedicalReportsSection(
                            searchQuery = medicalSearchQuery,
                            onSearchQueryChange = { medicalSearchQuery = it },
                            records = filteredRecords,
                            expandedRecords = expandedRecords,
                            isFetching = isFetchingRecords,
                            onFetchClick = { viewModel.fetchMedicalReports() },
                            onRecordToggle = { id ->
                                expandedRecords = if (expandedRecords.contains(id)) {
                                    expandedRecords - id
                                } else {
                                    expandedRecords + id
                                }
                            }
                        )
                    }
                }
            } else {
                // RESPONSIVE PORTRAIT MOBILE LAYOUT: Full-bleed vertical scroll
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderGreetingBanner(
                        userName = currentUser?.fullName ?: "Пациент",
                        activeAppointmentsCount = appointments.count { it.status == "PENDING" || it.status == "APPROVED" },
                        completedRecordsCount = records.size
                    )

                    ProfileCabinetCard(
                        user = currentUser,
                        onEditClick = {
                            editNameInput = currentUser?.fullName ?: ""
                            showEditProfile = true
                        },
                        onBiometricToggle = { viewModel.setBiometricEnrollment(it) }
                    )

                    TelegramBotCard(
                        user = currentUser,
                        onLinkClick = { chat -> viewModel.linkTelegramChatId(chat) },
                        onUnlinkClick = { viewModel.unlinkTelegramChatId() },
                        onTestClick = { viewModel.sendTestTelegramNotification() }
                    )

                    CachedQueueSnapshotsCard(
                        snapshots = cachedQueueSnapshots
                    )

                    // Tab bar for Appointments
                    AppointmentSegmentTabs(
                        selectedFilter = selectedAppFilter,
                        onFilterSelect = { selectedAppFilter = it }
                    )

                    AppointmentsSessionList(
                        appointments = filteredAppointments,
                        pendingSyncs = allPendingSyncs,
                        onCancelClick = { id -> viewModel.cancelAppointment(id, "Отменено пациентом в кабинете") }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    MedicalReportsSection(
                        searchQuery = medicalSearchQuery,
                        onSearchQueryChange = { medicalSearchQuery = it },
                        records = filteredRecords,
                        expandedRecords = expandedRecords,
                        isFetching = isFetchingRecords,
                        onFetchClick = { viewModel.fetchMedicalReports() },
                        onRecordToggle = { id ->
                            expandedRecords = if (expandedRecords.contains(id)) {
                                expandedRecords - id
                            } else {
                                expandedRecords + id
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }
        }
    }

    // P-27 fix: BackHandler intercepts back press when a dialog is open
    BackHandler(enabled = showEditProfile) { showEditProfile = false }
    BackHandler(enabled = showBookDialog) { showBookDialog = false }

    // P-07 refactor: extracted to EditProfileDialog.kt
    if (showEditProfile) {
        EditProfileDialog(
            editNameInput = editNameInput,
            onEditNameInputChange = { editNameInput = it },
            onSave = {
                viewModel.updateProfileName(editNameInput)
                showEditProfile = false
            },
            onDismiss = { showEditProfile = false },
            tealPrimary = tealPrimary
        )
    }

    // P-07 refactor: extracted to BookAppointmentDialog.kt
    if (showBookDialog) {
        BookAppointmentDialog(
            doctors = doctors,
            bookingDatesList = bookingDatesList,
            timeSlots = timeSlots,
            selectedDoctor = selectedDoctor,
            selectedSpecialty = selectedSpecialty,
            selectedDateIdx = selectedDateIdx,
            selectedTimeSlot = selectedTimeSlot,
            bookingReasonInput = bookingReasonInput,
            isBookingInProgress = isBookingInProgress,
            onSelectDoctor = { doc, spec ->
                selectedDoctor = doc
                selectedSpecialty = spec
            },
            onSelectDateIdx = { idx -> selectedDateIdx = idx },
            onSelectTimeSlot = { slot -> selectedTimeSlot = slot },
            onReasonInputChange = { bookingReasonInput = it },
            onConfirm = {
                if (!isBookingInProgress) {
                    viewModel.createAppointment(
                        doctorName = selectedDoctor,
                        specialty = selectedSpecialty,
                        date = bookingDatesList[selectedDateIdx],
                        time = selectedTimeSlot,
                        reason = bookingReasonInput.ifBlank { "Консультация врача" }
                    )
                    showBookDialog = false
                    bookingReasonInput = ""
                }
            },
            onDismiss = { showBookDialog = false },
            tealPrimary = tealPrimary,
            tealLight = tealLight,
            accentNavy = accentNavy
        )
    }
}

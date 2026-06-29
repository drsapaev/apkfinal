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
import com.aistudio.clinicsystem.ui.screens.patient.PatientAppointmentsTab
import com.aistudio.clinicsystem.ui.screens.patient.PatientHomeTab
import com.aistudio.clinicsystem.ui.screens.patient.PatientMedicalTab
import com.aistudio.clinicsystem.ui.screens.patient.PatientProfileTab
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
    modifier: Modifier = Modifier,
    isOnline: Boolean = true
) {
    // E1.5: secure the patient screen — contains PHI (appointments, medical records,
    // queue position, telegram id). Prevents screenshots and screen recording.
    com.aistudio.clinicsystem.ui.components.SecureScreen {
        PatientScreenContent(viewModel, modifier, isOnline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientScreenContent(
    viewModel: PatientViewModel,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true
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

    // P-04: doctors loaded from DoctorRepository (backend-synced, offline-cached)
    val doctors by viewModel.doctors.collectAsStateWithLifecycle()
    val doctorsList = doctors.map { Pair(it.fullName, it.specialty) }
    val timeSlots = listOf("09:00", "10:00", "11:00", "12:00", "14:00", "15:00", "16:00")

    // P-03 refactor: filteredAppointments and filteredRecords moved into
    // PatientAppointmentsTab and PatientMedicalTab respectively.

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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            modifier = Modifier
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.ui_intellect_clinic),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.ui_patient_cabinet_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // P-15 fix: offline indicator in TopAppBar
                    if (!isOnline) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Нет соединения с интернетом",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(20.dp)
                        )
                    }
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
                            tint = MaterialTheme.colorScheme.onSurface
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
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showBookDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Book Appointment") },
                text = { Text(stringResource(R.string.ui_book_appointment), fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .testTag("book_appointment_fab")
                    .padding(bottom = 12.dp)
            )
        },
        // P-17 + P-18 fix: Snackbar host for appointment creation + undo on cancel
        snackbarHost = {
            val snackbarHostState = remember { SnackbarHostState() }
            val undoAction by viewModel.undoAction.collectAsStateWithLifecycle()
            // P-17 fix: listen for appointmentCreatedEvent from ViewModel
            LaunchedEffect(Unit) {
                viewModel.appointmentCreatedEvent.collect { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Long
                    )
                }
            }
            // P-18 fix: listen for undoAction (cancel appointment) — show Snackbar with Undo button
            LaunchedEffect(undoAction) {
                if (undoAction != null) {
                    val result = snackbarHostState.showSnackbar(
                        message = "Запись отменена",
                        actionLabel = "Отменить",
                        duration = SnackbarDuration.Short,
                        withDismissAction = true
                    )
                    when (result) {
                        SnackbarResult.ActionPerformed -> viewModel.triggerUndo()
                        SnackbarResult.Dismissed -> viewModel.clearUndoAction()
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier
    ) { innerPadding ->
        // P-03 refactor: Bottom Navigation with 4 tabs
        var selectedTab by rememberSaveable { mutableStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.ui_tab_home)) },
                        label = { Text(stringResource(R.string.ui_tab_home)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Event, contentDescription = stringResource(R.string.ui_tab_appointments)) },
                        label = { Text(stringResource(R.string.ui_tab_appointments)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.ui_tab_medical)) },
                        label = { Text(stringResource(R.string.ui_tab_medical)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.ui_tab_profile)) },
                        label = { Text(stringResource(R.string.ui_tab_profile)) }
                    )
                }
            }
        ) { tabPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundSoft)
                    .padding(innerPadding)
                    .padding(tabPadding)
            ) {
                when (selectedTab) {
                    0 -> PatientHomeTab(
                        currentUser = currentUser,
                        appointments = appointments,
                        records = records,
                        cachedQueueSnapshots = cachedQueueSnapshots
                    )
                    1 -> PatientAppointmentsTab(
                        appointments = appointments,
                        pendingSyncs = allPendingSyncs,
                        selectedFilter = selectedAppFilter,
                        onFilterSelect = { selectedAppFilter = it },
                        onCancelClick = { id -> viewModel.cancelAppointment(id, "Отменено пациентом в кабинете") }
                    )
                    2 -> PatientMedicalTab(
                        records = records,
                        searchQuery = medicalSearchQuery,
                        onSearchQueryChange = { medicalSearchQuery = it },
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
                    3 -> PatientProfileTab(
                        currentUser = currentUser,
                        onEditClick = {
                            editNameInput = currentUser?.fullName ?: ""
                            showEditProfile = true
                        },
                        onBiometricToggle = { viewModel.setBiometricEnrollment(it) },
                        onLinkTelegram = { chat -> viewModel.linkTelegramChatId(chat) },
                        onUnlinkTelegram = { viewModel.unlinkTelegramChatId() },
                        onTestTelegram = { viewModel.sendTestTelegramNotification() }
                    )
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
            doctors = doctorsList,
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
